package com.samdevelop.samtv.core.m3u

/**
 * Entrada de uma playlist M3U/M3U8 estendida.
 * @property attributes atributos do #EXTINF com chaves em minúsculas (tvg-id, tvg-name, tvg-logo, group-title...).
 */
data class M3uEntry(
    val title: String,
    val url: String,
    val durationSeconds: Int?,
    val attributes: Map<String, String>,
    val httpHeaders: Map<String, String>,
    /** Linha (1-based) onde está a URL. */
    val line: Int,
) {
    val tvgId: String? get() = attributes["tvg-id"]?.takeIf { it.isNotBlank() }
    val tvgName: String? get() = attributes["tvg-name"]?.takeIf { it.isNotBlank() }
    val tvgLogo: String? get() = attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
    val groupTitle: String? get() = attributes["group-title"]?.takeIf { it.isNotBlank() }
    val channelNumber: Int? get() = (attributes["tvg-chno"] ?: attributes["channel-number"])?.trim()?.toIntOrNull()
    override fun toString(): String = "M3uEntry(title=$title, line=$line)"
}

enum class M3uIssueKind { MALFORMED_EXTINF, MISSING_URL, UNSUPPORTED_URL, DUPLICATE }

data class M3uIssue(val line: Int, val kind: M3uIssueKind)

data class M3uParseResult(
    val entries: List<M3uEntry>,
    val issues: List<M3uIssue>,
    val issueCount: Int,
    val hasHeader: Boolean,
    val duplicatesRemoved: Int,
    val linesRead: Int,
)

/**
 * Parser incremental e tolerante de playlists M3U.
 *
 * - Lê linha a linha (funciona com [Sequence] de um stream HTTP sem carregar o arquivo todo).
 * - Aceita CRLF, LF, BOM, espaços extras e linhas em branco.
 * - Atributos com aspas duplas, simples ou sem aspas; vírgulas dentro de aspas não quebram o título.
 * - #EXTGRP define o grupo da próxima entrada quando não houver group-title.
 * - #EXTVLCOPT:http-user-agent / http-referrer viram cabeçalhos HTTP da entrada.
 * - Entradas sem URL, URLs com esquema não suportado e duplicatas são registradas em [M3uParseResult.issues].
 * - Duplicata = mesma URL e mesmo identificador (tvg-id, ou título quando não houver tvg-id).
 *
 * Não executa I/O nem toca na thread de UI: o chamador decide o dispatcher.
 */
class M3uParser(
    private val maxIssuesKept: Int = 200,
    private val supportedSchemes: Set<String> = setOf("http", "https", "rtsp"),
) {

    fun parse(text: String): M3uParseResult = parse(text.lineSequence())

    fun parse(lines: Sequence<String>): M3uParseResult {
        val entries = ArrayList<M3uEntry>()
        val issues = ArrayList<M3uIssue>()
        var issueCount = 0
        fun issue(line: Int, kind: M3uIssueKind) {
            issueCount++
            if (issues.size < maxIssuesKept) issues += M3uIssue(line, kind)
        }

        val seen = HashSet<String>()
        var duplicates = 0
        var hasHeader = false
        var lineNo = 0

        var pending: PendingInfo? = null
        var pendingGroup: String? = null
        val pendingHeaders = LinkedHashMap<String, String>()

        for (rawLine in lines) {
            lineNo++
            var line = rawLine.trimEnd('\r').trim()
            if (lineNo == 1) line = line.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> hasHeader = true
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    if (pending != null) issue(pending.line, M3uIssueKind.MISSING_URL)
                    pending = parseExtInf(line, lineNo)
                    if (pending == null) issue(lineNo, M3uIssueKind.MALFORMED_EXTINF)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifEmpty { null }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) when (key) {
                        "http-user-agent" -> pendingHeaders["User-Agent"] = value
                        "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                        "http-origin" -> pendingHeaders["Origin"] = value
                    }
                }
                line.startsWith("#") -> Unit // comentários e diretivas não suportadas são ignorados
                else -> {
                    val info = pending
                    val scheme = line.substringBefore("://", "").lowercase()
                    if (scheme.isEmpty() || scheme !in supportedSchemes) {
                        issue(lineNo, M3uIssueKind.UNSUPPORTED_URL)
                    } else {
                        val attrs = (info?.attributes ?: emptyMap()).toMutableMap()
                        if (attrs["group-title"].isNullOrBlank() && pendingGroup != null) attrs["group-title"] = pendingGroup!!
                        val title = info?.title?.takeIf { it.isNotBlank() }
                            ?: attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                            ?: titleFromUrl(line)
                        val entry = M3uEntry(
                            title = title,
                            url = line,
                            durationSeconds = info?.duration,
                            attributes = attrs,
                            httpHeaders = LinkedHashMap(pendingHeaders),
                            line = lineNo,
                        )
                        val key = line + "\u0000" + (entry.tvgId ?: title.lowercase())
                        if (seen.add(key)) entries += entry else {
                            duplicates++
                            issue(lineNo, M3uIssueKind.DUPLICATE)
                        }
                    }
                    pending = null
                    pendingGroup = null
                    pendingHeaders.clear()
                }
            }
        }
        if (pending != null) issue(pending.line, M3uIssueKind.MISSING_URL)

        return M3uParseResult(entries, issues, issueCount, hasHeader, duplicates, lineNo)
    }

    private class PendingInfo(val duration: Int?, val attributes: Map<String, String>, val title: String, val line: Int)

    /** #EXTINF:-1 tvg-id="a" group-title="Esportes, HD",Nome do canal */
    private fun parseExtInf(line: String, lineNo: Int): PendingInfo? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val body = line.substring(colon + 1).trim()
        val commaIdx = findTitleComma(body)
        val head = if (commaIdx >= 0) body.substring(0, commaIdx) else body
        val title = if (commaIdx >= 0) body.substring(commaIdx + 1).trim() else ""

        val headTrim = head.trim()
        val durationToken = headTrim.takeWhile { !it.isWhitespace() }
        val duration = durationToken.toDoubleOrNull()?.toInt()
        val attrText = if (duration != null || durationToken.matches(Regex("-?\\d+(\\.\\d+)?"))) {
            headTrim.substring(durationToken.length)
        } else headTrim
        val attrs = parseAttributes(attrText)

        // Sem vírgula de título e sem atributos reconhecíveis: linha realmente malformada.
        if (commaIdx < 0 && attrs.isEmpty() && duration == null) return null
        return PendingInfo(duration, attrs, unquote(title), lineNo)
    }

    /** Índice da primeira vírgula fora de aspas. */
    private fun findTitleComma(s: String): Int {
        var quote: Char? = null
        for (i in s.indices) {
            val c = s[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> {
                    // Só abre aspas se vier logo após '=' (valor de atributo); aspas soltas fazem parte do texto.
                    if (i > 0 && s[i - 1] == '=') quote = c
                }
                c == ',' -> return i
            }
        }
        return -1
    }

    private fun parseAttributes(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ATTR.findAll(text).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = when {
                m.groups[2] != null -> m.groupValues[2]
                m.groups[3] != null -> m.groupValues[3]
                else -> m.groupValues[4]
            }
            result[key] = value.trim()
        }
        return result
    }

    private fun unquote(s: String): String = s.trim().removeSurrounding("\"").trim()

    private fun titleFromUrl(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { url.substringAfter("://").substringBefore('/') }

    private companion object {
        val ATTR = Regex("([A-Za-z0-9_.:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"',]+))")
    }
}
