package com.samdevelop.samtv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samdevelop.samtv.core.catalog.HistoryPolicy
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.repository.FavoritesRepositoryImpl
import com.samdevelop.samtv.data.repository.HistoryRepositoryImpl
import com.samdevelop.samtv.security.CredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = AppDatabase.inMemory(context) }
    @After fun tearDown() { db.close() }

    @Test fun favoritosPersistemEAlternam() = runTest {
        val repo = FavoritesRepositoryImpl(db)
        val fav = Favorite("src", "src:live:1", ContentType.CHANNEL, "Canal Exemplo", null, 1L)
        assertTrue(repo.toggle(fav))
        assertEquals(listOf("src:live:1"), repo.favorites("src").first().map { it.contentId })
        assertTrue(repo.isFavorite("src", "src:live:1", ContentType.CHANNEL).first())
        assertFalse(repo.toggle(fav))
        assertTrue(repo.favorites("src").first().isEmpty())
        // Favoritos são isolados por fonte
        repo.toggle(fav.copy(sourceId = "outra"))
        assertTrue(repo.favorites("src").first().isEmpty())
    }

    @Test fun historicoSemDuplicacaoComLimiteELimpeza() = runTest {
        val repo = HistoryRepositoryImpl(db)
        fun h(id: String, t: Long, type: ContentType = ContentType.CHANNEL, parent: String? = null) =
            PlaybackHistory("src", id, type, id, null, parent, 0, null, t)
        repo.record(h("c1", 1)); repo.record(h("c1", 2)); repo.record(h("c2", 3))
        assertEquals(listOf("c2", "c1"), repo.history("src").first().map { it.contentId })
        repo.record(h("ep1", 4, ContentType.EPISODE, "serie")); repo.record(h("ep2", 5, ContentType.EPISODE, "serie"))
        assertEquals(listOf("ep2", "c2", "c1"), repo.history("src").first().map { it.contentId })
        repeat(HistoryPolicy.MAX_ENTRIES + 20) { repo.record(h("n$it", 10L + it)) }
        assertEquals(HistoryPolicy.MAX_ENTRIES, repo.history("src").first().size)
        repo.clear("src")
        assertTrue(repo.history("src").first().isEmpty())
    }

    @Test fun credenciaisSaoCifradasNoAparelho() = runTest {
        val store = CredentialStore(context)
        store.save("perfil-teste", SourceSecrets(password = "senha_teste_123", playlistUrl = "https://l.example.test/get.php?password=abc"))
        val loaded = store.load("perfil-teste")!!
        assertEquals("senha_teste_123", loaded.password)
        assertEquals("https://l.example.test/get.php?password=abc", loaded.playlistUrl)
        val raw = context.getSharedPreferences("samtv_secure_credentials", 0).all.values.joinToString()
        assertFalse("senha em texto puro", raw.contains("senha_teste_123"))
        assertFalse("URL em texto puro", raw.contains("password=abc"))
        store.delete("perfil-teste")
        assertNull(store.load("perfil-teste"))
    }
}
