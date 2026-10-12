package com.samdevelop.samtv.core.net

/**
 * Remove credenciais de URLs e textos antes de qualquer log, relatório ou mensagem de erro.
 * Cobre: userinfo (usuario:senha@), parâmetros de query sensíveis e caminhos Xtream
 * do tipo /live/USUARIO/SENHA/123.ts.
 */
object SecretRedactor {
    private const val MASK = "***"
    private val SENSITIVE_KEYS = setOf(
        "username", "user", "password", "pass", "pwd", "token", "auth", "key", "apikey", "api_key", "secret", "signature", "sig",
    )
    private val USERINFO = Regex("(?i)(\\b[a-z][a-z0-9+.-]*://)[^/@\\s]+@")
    private val QUERY_PARAM = Regex("(?i)([?&])([^=&#\\s]+)=([^&#\\s]*)")
    private val XTREAM_PATH = Regex("(?i)/(live|movie|series|timeshift)/([^/\\s]+)/([^/\\s]+)/")

    fun redactUrl(url: String): String {
        var out = USERINFO.replace(url) { "${it.groupValues[1]}$MASK@" }
        out = QUERY_PARAM.replace(out) { m ->
            val key = m.groupValues[2]
            if (key.lowercase() in SENSITIVE_KEYS) "${m.groupValues[1]}$key=$MASK" else m.value
        }
        out = XTREAM_PATH.replace(out) { "/${it.groupValues[1]}/$MASK/$MASK/" }
        return out
    }

    /** Redação de um texto livre que pode conter URLs e também segredos conhecidos. */
    fun redactText(text: String, knownSecrets: Collection<String?> = emptyList()): String {
        var out = redactUrl(text)
        knownSecrets.filterNotNull().filter { it.length >= 3 }.forEach { out = out.replace(it, MASK) }
        return out
    }
}
