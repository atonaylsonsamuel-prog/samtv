# SAM TV OFICIAL

Aplicativo Android de streaming da **SAM DEVELOP** para celulares (retrato e paisagem). O usuário conecta as **próprias fontes IPTV autorizadas** — Xtream Codes ou playlist M3U por URL — e assiste TV ao vivo, filmes e séries que essas fontes disponibilizam.

> O aplicativo não fornece canais nem conteúdo. Não contém dados de canais, logos ou sinopses: tudo o que aparece vem da fonte configurada pelo usuário.

Esta é a primeira implementação de um produto multiplataforma. A versão Roku será um projeto separado; os contratos estão em [`docs/ROKU-CONTRACTS.md`](docs/ROKU-CONTRACTS.md).

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.1 (JDK 17) |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Arquitetura | MVVM + Repository Pattern + casos de uso; módulo `:core` em Kotlin puro |
| Assíncrono | Coroutines + Flow |
| Player | AndroidX Media3 / ExoPlayer (HLS, DASH, progressivo/MPEG-TS) com OkHttp |
| Rede | OkHttp (redirecionamentos controlados manualmente) |
| Persistência | Room (catálogo em cache, favoritos, histórico, perfis) + DataStore (preferências) |
| Segurança | Android Keystore (AES-256/GCM) para senhas e URLs de playlist |
| Imagens | Coil 2 |
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| Testes | JUnit 4, testes instrumentados (Compose UI Test, MockWebServer) |

## Estrutura de diretórios

```
SamTvOficial/
├── core/                       # Kotlin puro — reutilizável e referência para o Roku
│   └── src/main/kotlin/com/samdevelop/samtv/core/
│       ├── model/              # Modelos de domínio (ConnectionProfile, Channel, Movie, Series…)
│       ├── repository/         # Contratos: AuthenticationRepository, LiveTvRepository, VODRepository…
│       ├── source/             # SourceConnector (interface comum dos conectores)
│       ├── xtream/             # Conector Xtream Codes (player_api.php)
│       ├── m3u/                # Parser M3U incremental + conector M3U
│       ├── catalog/            # Regras: busca, IDs estáveis, agrupamento M3U, histórico
│       ├── net/                # HttpClient abstrato, validação de URL, redação de segredos
│       └── common/             # AppResult / AppError
│   └── src/test/               # 42 testes + fixtures (m3u/json) reutilizáveis pelo Roku
├── app/                        # Aplicativo Android
│   └── src/main/java/com/samdevelop/samtv/
│       ├── MainActivity.kt, SamTvApplication.kt, AppContainer.kt (DI manual)
│       ├── navigation/         # Rotas e NavHost
│       ├── ui/theme, ui/components, ui/common
│       ├── ui/screens/         # Home, Connection, LiveTv, ChannelPlayer, Movies/Series (VodScreens),
│       │                       # Details, Search/Favorites/History (LibraryScreens), Settings
│       ├── domain/usecase/     # ValidateConnectionUseCase, SessionManager (sincronização/estado)
│       ├── data/remote/        # OkHttpHttpClient
│       ├── data/local/         # Room (entidades, DAOs), DataStore, mapeadores
│       ├── data/repository/    # Implementações dos contratos do core
│       ├── player/             # PlayerFactory + PlayerController (integração Media3)
│       └── security/           # CredentialStore (Keystore)
│   └── src/test/               # Testes JVM (validação de formulário, mensagens de erro)
│   └── src/androidTest/        # Testes instrumentados (Room, Keystore, rede, player, fluxo completo)
├── .github/workflows/android.yml  # Build na nuvem (APK) + testes
├── docs/ROKU-CONTRACTS.md, docs/MANUAL-TESTS.md
├── README.md, INSTALL-WINDOWS.md, ARCHITECTURE.md, TEST-REPORT.md, CHANGELOG.md
```

## Requisitos

- Android Studio (versão estável recente, com suporte a AGP 8.10) — inclui o JDK 17+ (JetBrains Runtime).
- Android SDK Platform 36 e Build-Tools (o Android Studio instala ao sincronizar).
- Celular com **Android 8.0 (API 26)** ou superior.

## Opção 1 — Gerar o APK sem instalar nada (GitHub Actions)

1. Crie um repositório (pode ser privado) em github.com.
2. Envie o conteúdo deste zip para o repositório (pelo site: *Add file → Upload files*, arrastando todas as pastas, incluindo `.github`).
3. Abra a aba **Actions** → workflow **Android build**. Ele roda sozinho a cada envio (ou clique em *Run workflow*).
4. Ao terminar (≈ 8–12 min), abra a execução e baixe o artefato **samtv-apks**.
5. Dentro dele: `app-debug.apk` (para instalar e testar) e `app-release-unsigned.apk` (para assinar).

O mesmo workflow roda os testes de unidade e publica os relatórios (`test-reports`). Os testes instrumentados podem ser executados num emulador da própria nuvem: *Run workflow* → marque "Rodar também os testes instrumentados".

## Opção 2 — Android Studio

1. *File → Open* e selecione a pasta `SamTvOficial`.
2. Aguarde o *Gradle Sync* (primeira vez baixa as dependências).
3. Selecione o módulo `app` e o seu celular → ▶ *Run*.

Linha de comando (na pasta do projeto):

```bash
./gradlew :core:test :app:testDebugUnitTest   # testes de unidade
./gradlew :app:assembleDebug                  # APK de debug  → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                # APK de release não assinado → app/build/outputs/apk/release/
./gradlew :app:connectedDebugAndroidTest      # testes instrumentados (celular/emulador conectado)
```

No Windows use `gradlew.bat` no lugar de `./gradlew`. Detalhes em [INSTALL-WINDOWS.md](INSTALL-WINDOWS.md).

## Instalar no celular

- **Pelo APK:** copie `app-debug.apk` para o celular, abra pelo gerenciador de arquivos e permita "instalar apps de fontes desconhecidas" quando o Android pedir.
- **Pelo cabo (ADB):** ative *Opções do desenvolvedor → Depuração USB* e rode `adb install -r app-debug.apk`.

O build de debug usa o id `com.samdevelop.samtv.debug` e pode coexistir com o de release (`com.samdevelop.samtv`).

### Assinatura de release

O projeto **não contém chave de assinatura** (de propósito). Para publicar:

```bash
keytool -genkeypair -v -keystore samtv-release.jks -alias samtv -keyalg RSA -keysize 4096 -validity 10000
apksigner sign --ks samtv-release.jks --out SamTV-1.0.0.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Guarde o `.jks` e as senhas fora do repositório. Para a Play Store, prefira *Build → Generate Signed Bundle (AAB)*.

## Configurar uma fonte IPTV autorizada

**Xtream Codes:** endereço do servidor com porta (ex.: `https://servidor.com:8443`), usuário e senha fornecidos pelo provedor. Também é possível colar a URL `get.php?username=…&password=…` no campo de endereço — o app separa os campos.

**Playlist M3U:** cole a URL completa da lista. Ela é guardada criptografada, pois normalmente contém credenciais.

**HTTP sem criptografia:** muitos provedores só oferecem `http://`. O app só aceita depois que você marca *Permitir conexão sem criptografia*, ciente de que usuário e senha trafegam sem proteção.

A conexão só é salva depois que o servidor confirma os dados (para Xtream, `user_info.auth = 1` e conta ativa; para M3U, uma playlist válida com pelo menos um canal). Em seguida o catálogo é baixado em segundo plano.

## Diagnóstico de falhas

| Sintoma | Causa provável / ação |
|---|---|
| "Usuário ou senha incorretos" | Credenciais erradas ou conta bloqueada no painel. |
| "Sua assinatura expirou" | O servidor informou `status: Expired`. Renove com o provedor. |
| "formato inesperado" / HTTP 404 | Endereço não é de um servidor Xtream (confira porta e prefixo de caminho). |
| "certificado de segurança" | HTTPS com certificado inválido. O app não ignora isso; use o endereço correto ou `http://` com consentimento. |
| "tentou redirecionar… bloqueada" | O servidor redirecionou a requisição com sua senha para outro domínio. Fale com o provedor. |
| Canal não abre / "Formato não suportado" | Em Ajustes → *Formato da TV ao vivo*, alterne entre HLS e MPEG-TS. |
| "não suporta o codec" | O aparelho não tem decodificador para aquele stream (ex.: HEVC 10-bit em aparelhos antigos). |
| "Acesso ao stream negado (HTTP 403)" | Limite de conexões simultâneas da conta atingido, ou bloqueio do provedor. |
| Catálogo vazio | Ajustes → *Atualizar catálogo*; veja o estado em *Catálogo*. |

O app não grava logs com URLs ou senhas; mensagens de erro passam por um redator de segredos (`SecretRedactor`).

## Limitações conhecidas

- **APK não foi gerado no ambiente de desenvolvimento** desta entrega (sem acesso ao repositório Maven do Google). Use o workflow do GitHub ou o Android Studio. Ver [TEST-REPORT.md](TEST-REPORT.md).
- Catch-up/timeshift (TV arquivada) não implementado — não há botão para isso.
- EPG: apenas a programação curta do Xtream (`get_short_epg`). Guia XMLTV completo não implementado.
- M3U: todas as entradas são tratadas como canais (playlists não distinguem filmes/séries de forma confiável).
- Fontes com DRM não são suportadas (e não haverá mecanismo para contorná-lo).
- Textos da interface estão em português no código (sem `strings.xml` para tradução).
- Catálogos muito grandes (centenas de milhares de itens) são baixados por inteiro em cada atualização; o uso de memória pode ser alto em aparelhos modestos.
- Picture-in-Picture e Chromecast não implementados.
- Criptografia local reduz a exposição das credenciais, mas não protege contra um aparelho comprometido (root/malware).

## Licença e uso

Propriedade da SAM DEVELOP. Use apenas com fontes e conteúdos que você tem autorização para acessar.
