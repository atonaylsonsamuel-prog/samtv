package com.samdevelop.samtv.core.catalog

import com.samdevelop.samtv.core.m3u.M3uEntry
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.LiveCatalog
import com.samdevelop.samtv.core.model.PlaybackHistory
import java.security.MessageDigest
import java.text.Normalizer

/** Normalização de texto para busca: minúsculas, sem acentos, sem pontuação, espaços colapsados. */
object SearchText {
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return MARKS.replace(decomposed, "").replace(NON_ALNUM, " ").trim()
    }

    /** Todos os termos da busca precisam aparecer no texto (qualquer ordem). */
    fun matches(query: String, text: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val t = normalize(text)
        return q.split(' ').all { it in t }
    }
}

/** Identificadores estáveis, para que favoritos e histórico sobrevivam a atualizações de playlist. */
object StableIds {
    fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    fun categoryId(sourceId: String, kind: CatalogKind, rawId: String): String = "$sourceId:${kind.name.lowercase()}:cat:$rawId"
}

/**
 * Converte entradas M3U em categorias e canais.
 *
 * Organização de categorias:
 *  - Com group-title: uma categoria por grupo, na ordem em que aparecem.
 *  - Entradas sem grupo, quando outras têm: categoria "Sem categoria".
 *  - Nenhuma entrada com grupo: organização alternativa por letra inicial (A, B, ..., "#").
 *
 * Os nomes de categoria gerados aqui são chaves fixas (SEM_CATEGORIA, letras) que a UI pode traduzir.
 */
object M3uCatalogMapper {
    const val UNCATEGORIZED_NAME = "Sem categoria"
    const val OTHERS_LETTER = "#"

    fun toLiveCatalog(sourceId: String, entries: List<M3uEntry>): LiveCatalog {
        val anyGroup = entries.any { it.groupTitle != null }
        val categories = LinkedHashMap<String, ChannelCategory>()
        val usedIds = HashMap<String, Int>()
        val channels = ArrayList<Channel>(entries.size)

        fun categoryFor(name: String): ChannelCategory = categories.getOrPut(name) {
            ChannelCategory(
                id = StableIds.categoryId(sourceId, CatalogKind.LIVE, StableIds.sha1(name.lowercase())),
                sourceId = sourceId,
                name = name,
                kind = CatalogKind.LIVE,
                position = categories.size,
            )
        }

        val letterBuckets = if (!anyGroup) entries.map { letterOf(it.title) }.distinct().sortedWith(letterOrder) else emptyList()
        letterBuckets.forEach { categoryFor(it) }

        entries.forEachIndexed { index, e ->
            val catName = when {
                e.groupTitle != null -> e.groupTitle!!
                anyGroup -> UNCATEGORIZED_NAME
                else -> letterOf(e.title)
            }
            val category = categoryFor(catName)
            val baseId = StableIds.sha1(listOf(e.tvgId.orEmpty(), e.title.lowercase(), catName.lowercase()).joinToString("|"))
            val n = usedIds.merge(baseId, 1, Int::plus)!!
            val id = if (n == 1) "$sourceId:ch:$baseId" else "$sourceId:ch:$baseId-$n"
            channels += Channel(
                id = id,
                sourceId = sourceId,
                name = e.title,
                number = e.channelNumber,
                logoUrl = e.tvgLogo?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                categoryId = category.id,
                epgChannelId = e.tvgId,
                hasArchive = false,
                streamKey = e.url,
                containerExtension = null,
                httpHeaders = e.httpHeaders,
                position = index,
            )
        }
        return LiveCatalog(categories.values.toList(), channels)
    }

    fun letterOf(title: String): String {
        val first = SearchText.normalize(title).firstOrNull() ?: return OTHERS_LETTER
        return if (first in 'a'..'z') first.uppercaseChar().toString() else OTHERS_LETTER
    }

    private val letterOrder = Comparator<String> { a, b ->
        when {
            a == b -> 0
            a == OTHERS_LETTER -> 1
            b == OTHERS_LETTER -> -1
            else -> a.compareTo(b)
        }
    }
}

/**
 * Regras do histórico (iguais em Android e Roku):
 *  - uma entrada por (fonte, conteúdo, tipo) — reassistir apenas atualiza data e posição;
 *  - episódios de uma mesma série mantêm só o mais recente (evita lista poluída);
 *  - limite de [MAX_ENTRIES] por fonte, removendo os mais antigos.
 */
object HistoryPolicy {
    const val MAX_ENTRIES = 200

    fun dedupeKey(h: PlaybackHistory): String =
        if (h.parentId != null) "${h.sourceId}|series|${h.parentId}" else "${h.sourceId}|${h.type}|${h.contentId}"

    fun merge(existing: List<PlaybackHistory>, incoming: PlaybackHistory): List<PlaybackHistory> {
        val key = dedupeKey(incoming)
        val rest = existing.filterNot { dedupeKey(it) == key }
        return (listOf(incoming) + rest).sortedByDescending { it.lastWatchedAtMillis }.take(MAX_ENTRIES)
    }
}
