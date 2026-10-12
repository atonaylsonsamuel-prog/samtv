package com.samdevelop.samtv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.net.UrlRules
import com.samdevelop.samtv.core.repository.AuthenticationRepository
import com.samdevelop.samtv.domain.usecase.ConnectionForm
import com.samdevelop.samtv.domain.usecase.ValidateConnectionUseCase
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.InlineBanner
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val form: ConnectionForm,
    val editing: Boolean = false,
    val loadingExisting: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val errorField: AppError.Field? = null,
    val success: AuthenticationResult? = null,
    /** Em edição, há senha/URL salva — campo vazio mantém o valor atual. */
    val hasStoredSecret: Boolean = false,
)

class ConnectionViewModel(
    private val auth: AuthenticationRepository,
    private val validate: ValidateConnectionUseCase,
    handle: SavedStateHandle,
) : ViewModel() {

    private val profileId: String? = handle.get<String>("profileId")
    private var existing: ConnectionProfile? = null
    private var existingSecrets: SourceSecrets? = null

    private val _state = MutableStateFlow(
        ConnectionUiState(
            form = ConnectionForm(type = handle.get<String>("type")?.let { runCatching { SourceType.valueOf(it) }.getOrNull() } ?: SourceType.XTREAM),
            editing = profileId != null,
            loadingExisting = profileId != null,
        ),
    )
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    init {
        if (profileId != null) viewModelScope.launch {
            val profile = auth.profiles.first().firstOrNull { it.id == profileId }
            existing = profile
            existingSecrets = auth.secretsFor(profileId)
            if (profile != null) {
                _state.update {
                    it.copy(
                        loadingExisting = false,
                        hasStoredSecret = existingSecrets != null,
                        form = ConnectionForm(
                            type = profile.type,
                            name = profile.name,
                            server = if (profile.type == SourceType.XTREAM) profile.address else "",
                            username = profile.username.orEmpty(),
                            allowInsecureHttp = profile.allowInsecureHttp,
                        ),
                    )
                }
            } else {
                _state.update { it.copy(loadingExisting = false, error = "Fonte não encontrada.") }
            }
        }
    }

    fun update(transform: (ConnectionForm) -> ConnectionForm) =
        _state.update { it.copy(form = transform(it.form), error = null, errorField = null) }

    fun setType(type: SourceType) { if (!_state.value.editing) update { it.copy(type = type) } }

    /** Se o usuário colar uma URL get.php/player_api.php no campo do servidor, preenche usuário e senha. */
    fun onServerChanged(value: String) {
        val parts = UrlRules.extractXtreamParts(value)
        if (parts != null) {
            update { it.copy(server = parts.baseUrl, username = parts.username ?: it.username, password = parts.password ?: it.password) }
        } else update { it.copy(server = value) }
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        when (val v = validate(s.form, existing, existingSecrets)) {
            is AppResult.Failure -> _state.update { it.copy(error = ErrorMessages.of(v.error), errorField = (v.error as? AppError.InvalidInput)?.field) }
            is AppResult.Success -> {
                val (profile, secrets) = v.value
                _state.update { it.copy(submitting = true, error = null) }
                viewModelScope.launch {
                    when (val r = auth.saveAndActivate(profile, secrets)) {
                        is AppResult.Success -> _state.update { it.copy(submitting = false, success = r.value) }
                        is AppResult.Failure -> _state.update { it.copy(submitting = false, error = ErrorMessages.of(r.error)) }
                    }
                }
            }
        }
    }

    companion object {
        val Factory = containerFactory { c, h -> ConnectionViewModel(c.authRepository, ValidateConnectionUseCase(), h) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(onBack: () -> Unit, onConnected: () -> Unit, vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.success) { if (state.success != null) onConnected() }
    val focus = LocalFocusManager.current
    val form = state.form

    Scaffold(
        containerColor = SamColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Editar fonte" else "Adicionar fonte") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SamColors.Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                if (!state.editing) {
                    SecondaryTabRow(selectedTabIndex = form.type.ordinal, containerColor = SamColors.Background) {
                        Tab(selected = form.type == SourceType.XTREAM, onClick = { vm.setType(SourceType.XTREAM) }, text = { Text("Xtream Codes") })
                        Tab(selected = form.type == SourceType.M3U, onClick = { vm.setType(SourceType.M3U) }, text = { Text("Playlist M3U") })
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.loadingExisting) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        return@Column
                    }
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { v -> vm.update { it.copy(name = v) } },
                        label = { Text("Nome da conexão (opcional)") },
                        singleLine = true,
                        isError = state.errorField == AppError.Field.NAME,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (form.type == SourceType.XTREAM) {
                        OutlinedTextField(
                            value = form.server,
                            onValueChange = vm::onServerChanged,
                            label = { Text("Endereço do servidor") },
                            placeholder = { Text("https://servidor.com:8080") },
                            supportingText = { Text("Você também pode colar a URL get.php fornecida pelo provedor.") },
                            singleLine = true,
                            isError = state.errorField == AppError.Field.SERVER_URL,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("field_server"),
                        )
                        OutlinedTextField(
                            value = form.username,
                            onValueChange = { v -> vm.update { it.copy(username = v) } },
                            label = { Text("Usuário") },
                            singleLine = true,
                            isError = state.errorField == AppError.Field.USERNAME,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("field_user"),
                        )
                        PasswordField(
                            value = form.password,
                            onValueChange = { v -> vm.update { it.copy(password = v) } },
                            isError = state.errorField == AppError.Field.PASSWORD,
                            hint = if (state.hasStoredSecret) "Deixe em branco para manter a senha salva." else null,
                            onDone = { focus.clearFocus(); vm.submit() },
                        )
                    } else {
                        OutlinedTextField(
                            value = form.playlistUrl,
                            onValueChange = { v -> vm.update { it.copy(playlistUrl = v) } },
                            label = { Text("URL da playlist M3U") },
                            placeholder = { Text("https://provedor.com/lista.m3u") },
                            supportingText = {
                                Text(if (state.hasStoredSecret) "Deixe em branco para manter a URL salva (ela não é exibida por conter credenciais)." else "A URL é guardada criptografada, pois costuma conter usuário e senha.")
                            },
                            isError = state.errorField == AppError.Field.PLAYLIST_URL,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.submit() }),
                            modifier = Modifier.fillMaxWidth().testTag("field_playlist"),
                        )
                    }

                    val usesHttp = when (form.type) {
                        SourceType.XTREAM -> form.server.trim().startsWith("http://", ignoreCase = true)
                        SourceType.M3U -> true // a playlist pode ser https e conter canais http
                    }
                    AnimatedVisibility(usesHttp || form.allowInsecureHttp) {
                        InsecureConsent(form.type, form.allowInsecureHttp) { v -> vm.update { it.copy(allowInsecureHttp = v) } }
                    }

                    state.error?.let { InlineBanner(it, isError = true, modifier = Modifier.padding(horizontal = 0.dp)) }

                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { focus.clearFocus(); vm.submit() }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (state.submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SamColors.TextPrimary)
                            Text("  Verificando conexão…")
                        } else Text(if (state.editing) "Testar e salvar" else "Conectar")
                    }
                    Text(
                        "A conexão só é salva depois que o servidor confirma os dados. Use apenas fontes que você tem autorização para acessar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SamColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, isError: Boolean, hint: String?, onDone: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Senha") },
        singleLine = true,
        isError = isError,
        supportingText = if (hint != null) { { Text(hint) } } else null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "Ocultar senha" else "Mostrar senha")
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("field_password"),
    )
}

@Composable
private fun InsecureConsent(type: SourceType, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SamColors.Error.copy(alpha = 0.10f), MaterialTheme.shapes.small).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text("Permitir conexão sem criptografia (HTTP)", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (type == SourceType.XTREAM)
                "Este servidor usa http://. Usuário e senha serão enviados sem criptografia e podem ser interceptados na rede. Prefira https:// se o provedor oferecer."
            else
                "Marque apenas se a playlist ou os canais usarem http://. Nesse caso, o endereço e eventuais credenciais trafegam sem criptografia.",
            style = MaterialTheme.typography.bodySmall,
            color = SamColors.TextSecondary,
        )
    }
}
