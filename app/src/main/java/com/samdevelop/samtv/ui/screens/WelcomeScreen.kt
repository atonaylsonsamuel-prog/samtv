package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.repository.AuthenticationRepository
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.BrandMark
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.flow.first

class StartupViewModel(private val auth: AuthenticationRepository) : ViewModel() {
    suspend fun hasAnySource(): Boolean = auth.profiles.first().isNotEmpty()

    companion object {
        val Factory = containerFactory { c, _ -> StartupViewModel(c.authRepository) }
    }
}

/** Inicialização: mostra a marca enquanto lê as preferências locais (sem rede) e decide o destino. */
@Composable
fun StartupScreen(onHasSource: () -> Unit, onFirstUse: () -> Unit, vm: StartupViewModel = viewModel(factory = StartupViewModel.Factory)) {
    LaunchedEffect(Unit) { if (vm.hasAnySource()) onHasSource() else onFirstUse() }
    Column(
        Modifier.fillMaxSize().background(SamColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(subtitle = true)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = SamColors.Purple, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** Primeira utilização: nenhuma fonte cadastrada. */
@Composable
fun WelcomeScreen(onAdd: (SourceType) -> Unit) {
    Box(Modifier.fillMaxSize().background(SamColors.Background).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(subtitle = true)
            Spacer(Modifier.height(28.dp))
            Text("Bem-vindo!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Conecte a sua fonte de IPTV para começar. O SAM TV OFICIAL não fornece canais nem conteúdo: " +
                    "ele reproduz o que a sua própria assinatura ou lista autorizada disponibiliza.",
                color = SamColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = { onAdd(SourceType.XTREAM) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Dns, contentDescription = null)
                Text("  Conectar com Xtream Codes")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onAdd(SourceType.M3U) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                Text("  Adicionar playlist M3U")
            }
            Spacer(Modifier.height(32.dp))
            InstructionBlock()
        }
    }
}

@Composable
private fun InstructionBlock() {
    Column(
        Modifier.fillMaxWidth().background(SamColors.Surface, MaterialTheme.shapes.medium).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Como configurar", style = MaterialTheme.typography.titleSmall)
        Step("1", "Xtream Codes: use o endereço do servidor (com a porta, se houver), o usuário e a senha que o seu provedor enviou.")
        Step("2", "Playlist M3U: cole a URL completa da lista (geralmente termina em .m3u ou contém get.php).")
        Step("3", "Prefira endereços https://. Endereços http:// só são aceitos com sua confirmação, pois não são criptografados.")
        Step("4", "Suas senhas ficam criptografadas neste aparelho e só são enviadas ao servidor que você informar.")
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row {
        Text(n, color = SamColors.PurpleAccent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
        Text(text, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
