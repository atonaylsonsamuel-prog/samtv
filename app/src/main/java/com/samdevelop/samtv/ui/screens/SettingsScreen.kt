package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.data.local.LiveFormat
import com.samdevelop.samtv.data.local.SyncStateEntity
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.ConfirmDialog
import com.samdevelop.samtv.ui.components.InlineBanner
import com.samdevelop.samtv.ui.components.SamChip
import com.samdevelop.samtv.ui.components.SectionTitle
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val profiles = c.authRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active = c.session.activeProfile
    val connection = c.session.connection
    val sync = c.session.sync
    val liveFormat = c.preferences.liveFormat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveFormat.AUTO)
    val syncStates = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else c.catalogRepository.syncStates(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: String) = viewModelScope.launch { c.authRepository.setActive(id) }
    fun remove(id: String) = viewModelScope.launch { c.authRepository.remove(id) }
    fun refreshCatalog() = c.session.refreshCatalog()
    fun recheck() = c.session.recheckConnection()
    fun clearCache() = viewModelScope.launch { c.session.clearCache() }
    fun clearHistory() = viewModelScope.launch { c.historyRepository.clear(c.session.activeProfile.value?.id) }
    fun setLiveFormat(f: LiveFormat) = viewModelScope.launch { c.preferences.setLiveFormat(f) }

    companion object {
        val Factory = containerFactory { c, _ -> SettingsViewModel(c) }
    }
}

private sealed interface Confirm {
    data class RemoveSource(val profile: ConnectionProfile) : Confirm
    data object ClearCache : Confirm
    data object ClearHistory : Confirm
}

@Composable
fun SettingsScreen(
    onAddSource: (SourceType) -> Unit,
    onEditSource: (ConnectionProfile) -> Unit,
    onNoSourcesLeft: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val liveFormat by vm.liveFormat.collectAsStateWithLifecycle()
    val syncStates by vm.syncStates.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    LazyColumn(Modifier.fillMaxSize().background(SamColors.Background), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Configurações") }

        // ------------------------------------------------ fontes
        item { SectionTitle("Fontes") }
        items(profiles, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { vm.select(p.id) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = active?.id == p.id, onClick = { vm.select(p.id) })
                Icon(if (p.type == SourceType.XTREAM) Icons.Filled.Dns else Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = SamColors.TextSecondary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (if (p.type == SourceType.XTREAM) "Xtream · " else "M3U · ") + p.address + if (p.allowInsecureHttp) " · HTTP permitido" else "",
                        style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary, maxLines = 1,
                    )
                }
                IconButton(onClick = { onEditSource(p) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar ${p.name}") }
                IconButton(onClick = { confirm = Confirm.RemoveSource(p) }) { Icon(Icons.Filled.Delete, contentDescription = "Remover ${p.name}") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { onAddSource(SourceType.XTREAM) }) { Icon(Icons.Filled.Add, null); Text(" Xtream") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onAddSource(SourceType.M3U) }) { Icon(Icons.Filled.Add, null); Text(" M3U") }
            }
        }

        // ------------------------------------------------ estado da conexão
        item { HorizontalDivider(color = SamColors.Outline, modifier = Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Estado da conexão", action = { OutlinedButton(onClick = vm::recheck) { Text("Verificar") } }) }
        item { ConnectionDetails(connection) }

        // ------------------------------------------------ catálogo
        item { HorizontalDivider(color = SamColors.Outline, modifier = Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Catálogo") }
        item { SyncDetails(syncStates, sync.results) }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = vm::refreshCatalog, enabled = !sync.running && active != null) {
                    Text(if (sync.running) "Atualizando…" else "Atualizar catálogo")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { confirm = Confirm.ClearCache }, enabled = active != null) { Text("Limpar cache") }
            }
        }

        // ------------------------------------------------ reprodução
        if (active?.type == SourceType.XTREAM) {
            item { HorizontalDivider(color = SamColors.Outline, modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Formato da TV ao vivo") }
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    LiveFormat.entries.forEach { f ->
                        SamChip(
                            when (f) { LiveFormat.AUTO -> "Automático"; LiveFormat.HLS -> "HLS (m3u8)"; LiveFormat.TS -> "MPEG-TS" },
                            liveFormat == f,
                        ) { vm.setLiveFormat(f) }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            item {
                Text(
                    "Automático usa o que o servidor declara permitir (prefere HLS). Troque se algum canal não abrir.",
                    style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ------------------------------------------------ histórico e sobre
        item { HorizontalDivider(color = SamColors.Outline, modifier = Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Privacidade") }
        item {
            OutlinedButton(onClick = { confirm = Confirm.ClearHistory }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Limpar histórico") }
        }
        item {
            Text(
                "Senhas e URLs de playlist são guardadas criptografadas (Android Keystore) e enviadas somente ao servidor configurado. " +
                    "Nenhum dado é coletado pela SAM DEVELOP.",
                style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
        item { HorizontalDivider(color = SamColors.Outline, modifier = Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Sobre") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("SAM TV OFICIAL ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text("Desenvolvido por SAM DEVELOP", style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary)
                Text(
                    "Este aplicativo não fornece conteúdo. Use apenas fontes que você tem autorização para acessar.",
                    style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
                )
            }
        }
    }

    when (val c = confirm) {
        is Confirm.RemoveSource -> ConfirmDialog(
            "Remover fonte?",
            "\"${c.profile.name}\" será removida junto com seu catálogo, favoritos e histórico neste aparelho.",
            "Remover",
            onConfirm = {
                vm.remove(c.profile.id)
                if (profiles.size <= 1) onNoSourcesLeft()
            },
            onDismiss = { confirm = null },
        )
        Confirm.ClearCache -> ConfirmDialog(
            "Limpar cache?", "O catálogo baixado será apagado. Favoritos e histórico são mantidos. Será preciso atualizar o catálogo.",
            "Limpar", onConfirm = { vm.clearCache() }, onDismiss = { confirm = null },
        )
        Confirm.ClearHistory -> ConfirmDialog(
            "Limpar histórico?", "Todo o histórico da fonte ativa será apagado deste aparelho.",
            "Limpar", onConfirm = { vm.clearHistory() }, onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun ConnectionDetails(status: ConnectionStatus) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        when (status) {
            ConnectionStatus.NoSource -> Text("Nenhuma fonte ativa.", color = SamColors.TextSecondary)
            ConnectionStatus.Checking -> Text("Verificando…", color = SamColors.TextSecondary)
            is ConnectionStatus.Offline -> InlineBanner(ErrorMessages.of(status.error), isError = true)
            is ConnectionStatus.Online -> {
                val a = status.auth
                val statusText = when (a.status) {
                    AccountStatus.ACTIVE -> "Ativa"
                    AccountStatus.UNKNOWN -> "Conectado (a fonte não informa estado de conta)"
                    else -> a.status.name
                }
                DetailLine("Conta", statusText)
                a.expiresAtEpochSeconds?.let { DetailLine("Vencimento", ErrorMessages.formatDate(it)) }
                if (a.maxConnections != null) DetailLine("Conexões", "${a.activeConnections ?: "?"} em uso de ${a.maxConnections}")
                if (a.allowedOutputFormats.isNotEmpty()) DetailLine("Formatos", a.allowedOutputFormats.joinToString(", "))
                a.itemCount?.let { DetailLine("Itens na playlist", it.toString()) }
                a.serverMessage?.let { DetailLine("Mensagem do servidor", it) }
            }
        }
    }
}

@Composable
private fun SyncDetails(states: List<SyncStateEntity>, results: Map<CatalogKind, AppResult<Int>>) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (states.isEmpty()) Text("Catálogo ainda não baixado.", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        states.sortedBy { it.kind }.forEach { s ->
            val name = when (s.kind) { "LIVE" -> "Canais"; "MOVIE" -> "Filmes"; "SERIES" -> "Séries"; else -> s.kind }
            DetailLine(name, "${s.itemCount} · ${fmt.format(Date(s.refreshedAt))}")
        }
        results.forEach { (kind, r) ->
            if (r is AppResult.Failure) InlineBanner("Falha em ${when (kind) { CatalogKind.LIVE -> "canais"; CatalogKind.MOVIE -> "filmes"; CatalogKind.SERIES -> "séries" }}: ${ErrorMessages.of(r.error)}", isError = true)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
