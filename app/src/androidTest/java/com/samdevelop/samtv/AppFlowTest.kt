package com.samdevelop.samtv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluxo completo com servidor Xtream simulado: primeira utilização, validação do formulário,
 * consentimento HTTP, cadastro, catálogo, busca, favoritos, player, rotação e remoção da fonte.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var fake: FakeXtreamServer
    private val app get() = ApplicationProvider.getApplicationContext<SamTvApplication>()

    @Before fun setUp() {
        fake = FakeXtreamServer().start()
        runBlocking {
            val repo = app.container.authRepository
            repo.profiles.first().forEach { repo.remove(it.id) }
        }
    }

    @After fun tearDown() {
        runBlocking {
            val repo = app.container.authRepository
            repo.profiles.first().forEach { repo.remove(it.id) }
        }
        fake.shutdown()
    }

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 15_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeout)

    @Test fun primeiraUtilizacaoCadastroCatalogoBuscaFavoritoPlayerERemocao() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Primeira utilização
        waitText("Bem-vindo!")
        compose.onNodeWithText("Conectar com Xtream Codes", substring = true).assertExists()
        compose.onNodeWithText("Adicionar playlist M3U", substring = true).assertExists()

        // Rotação/recriação mantém o estado da tela
        scenario.recreate()
        waitText("Bem-vindo!")

        // Formulário: validação de campos
        compose.onNodeWithText("Conectar com Xtream Codes", substring = true).performClick()
        waitText("Adicionar fonte")
        compose.onNodeWithText("Conectar").performClick()
        waitText("Informe o endereço do servidor.")

        compose.onNodeWithTag("field_server").performTextInput(fake.baseUrl)
        compose.onNodeWithTag("field_user").performTextInput(fake.user)
        compose.onNodeWithTag("field_password").performTextInput(fake.pass)

        // HTTP sem consentimento é recusado antes de qualquer requisição
        compose.onNodeWithText("Conectar").performClick()
        waitText("Para continuar, marque", substring = true)
        assert(fake.server.requestCount == 0) { "credenciais não podem sair sem consentimento" }

        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Conectar").performClick()

        // Tela inicial com catálogo do servidor simulado
        waitText("Exemplos (teste)", timeout = 30_000)
        compose.onNodeWithText("TV").performClick()
        waitText("Canal Exemplo Alfa", substring = true)
        compose.onNodeWithText("Canal Exemplo Gama", substring = true).assertExists()

        // Busca (sem acento/caixa)
        compose.onNodeWithTag("search_field").performTextInput("BETA")
        compose.waitUntilDoesNotExist(hasText("Canal Exemplo Alfa", substring = true), 10_000)
        waitText("Canal Exemplo Beta", substring = true)

        // Favorito
        compose.onNode(hasContentDescription("Adicionar aos favoritos")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Remover dos favoritos"), 5_000)

        // Player com o clipe de teste
        compose.onNodeWithText("Canal Exemplo Beta", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Voltar"), 10_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Pausar"), 30_000)
        scenario.recreate() // rotação durante a reprodução: o player continua no ViewModel
        compose.waitUntilAtLeastOneExists(hasContentDescription("Voltar"), 10_000)
        compose.onAllNodesWithText("Canal Exemplo Beta", substring = true).onFirst().assertExists()
        Espresso.pressBack()

        // Remoção com confirmação volta para a primeira utilização
        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Remover", substring = true), 10_000)
        compose.onAllNodes(hasContentDescription("Remover", substring = true)).onFirst().performClick()
        compose.onNodeWithText("Remover").performClick()
        waitText("Bem-vindo!")

        scenario.close()
    }
}
