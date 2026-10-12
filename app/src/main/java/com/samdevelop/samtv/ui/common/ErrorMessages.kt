package com.samdevelop.samtv.ui.common

import com.samdevelop.samtv.core.common.AppError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mensagens para o usuário (pt-BR). Nunca incluem URLs, usuários ou senhas. */
object ErrorMessages {
    fun of(error: AppError): String = when (error) {
        is AppError.InvalidInput -> when (error.field) {
            AppError.Field.NAME -> "O nome da conexão é muito longo (máximo 60 caracteres)."
            AppError.Field.USERNAME -> "Informe o usuário."
            AppError.Field.PASSWORD -> "Informe a senha."
            AppError.Field.SERVER_URL -> when (error.detail) {
                "empty" -> "Informe o endereço do servidor."
                "userinfo" -> "Não coloque usuário e senha dentro do endereço. Use os campos próprios."
                "scheme" -> "O endereço deve começar com http:// ou https://."
                else -> "Endereço do servidor inválido. Exemplo: https://servidor.com:8080"
            }
            AppError.Field.PLAYLIST_URL -> when (error.detail) {
                "empty" -> "Informe a URL da playlist M3U."
                "userinfo" -> "URL com usuário:senha@ não é aceita. Use a URL fornecida pelo provedor."
                else -> "URL da playlist inválida. Ela deve começar com http:// ou https://."
            }
        }
        AppError.Timeout -> "O servidor demorou demais para responder. Tente novamente."
        is AppError.Network -> "Não foi possível conectar ao servidor. Verifique sua internet e o endereço informado."
        is AppError.Tls -> "Falha na verificação do certificado de segurança (HTTPS) do servidor. A conexão foi bloqueada para sua proteção."
        is AppError.Http -> when (error.code) {
            404 -> "O servidor respondeu 404: o endereço não parece ser de um servidor Xtream Codes/playlist válida."
            in 500..599 -> "O servidor está com problemas (HTTP ${error.code}). Tente mais tarde."
            else -> "O servidor respondeu com erro HTTP ${error.code}."
        }
        AppError.Unauthorized -> "Usuário ou senha incorretos, ou acesso negado pelo servidor."
        is AppError.AccountExpired -> error.expiresAtEpochSeconds?.let { "Sua assinatura expirou em ${formatDate(it)}." }
            ?: "Sua assinatura está expirada segundo o servidor."
        is AppError.AccountDisabled -> "Conta indisponível no servidor (estado: ${error.status})."
        AppError.InsecureConnectionNotAllowed ->
            "Este endereço usa HTTP sem criptografia. Para continuar, marque \"Permitir conexão sem criptografia\" ciente de que seus dados podem ser interceptados."
        is AppError.RedirectBlocked -> "O servidor tentou redirecionar para outro endereço e a conexão foi bloqueada para proteger suas credenciais."
        is AppError.InvalidResponse -> "O servidor respondeu em um formato inesperado. Confira se o endereço é de um servidor Xtream Codes."
        is AppError.PlaylistParse -> "O conteúdo baixado não é uma playlist M3U válida."
        AppError.EmptyPlaylist -> "A playlist não contém nenhum canal válido."
        AppError.UnsupportedFeature -> "Este recurso não está disponível para esta fonte."
        AppError.NotFound -> "Conteúdo não encontrado no servidor."
        AppError.NoActiveSource -> "Nenhuma fonte configurada."
        is AppError.Storage -> "Não foi possível acessar as credenciais salvas neste aparelho. Edite a fonte e informe a senha novamente."
        is AppError.Unknown -> "Ocorreu um erro inesperado."
    }

    fun formatDate(epochSeconds: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(epochSeconds * 1000))
}
