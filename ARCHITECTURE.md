# Arquitetura — SAM TV OFICIAL

## Visão geral

```
┌──────────────────────────────── :app (Android) ─────────────────────────────────┐
│  UI (Compose)  ──►  ViewModels  ──►  Casos de uso / SessionManager             │
│      ▲                 │                     │                                  │
│      │ StateFlow       ▼                     ▼                                  │
│      │           Repositórios (data/repository) ─► Room / DataStore / Keystore  │
│      │                 │                                                        │
│      │                 ▼                                                        │
│  PlayerView ◄── PlayerController ◄── PlaybackSourceProvider                     │
│                   (Media3)                 │                                    │
└────────────────────────────────────────────┼────────────────────────────────────┘
                                             ▼
┌──────────────────────────────── :core (Kotlin puro) ────────────────────────────┐
│  Contratos de repositório · Modelos · SourceConnector                           │
│      ├─ XtreamConnector (player_api.php)                                        │
│      └─ M3uConnector ─ M3uParser                                                │
│  Regras: SearchText · StableIds · M3uCatalogMapper · HistoryPolicy · UrlRules   │
│  HttpClient (interface) ◄── implementado no app por OkHttpHttpClient           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Por que dois módulos

O enunciado sugeria um único módulo `app`. Separamos o **`:core`** (Kotlin/JVM, sem Android) porque:

1. **Testabilidade:** parser M3U, cliente Xtream, validação de URL e regras de negócio rodam em JUnit puro, sem emulador. Foi esse módulo que pôde ser compilado e testado no ambiente desta entrega.
2. **Fronteira clara:** é impossível o domínio depender de Android/ExoPlayer — o compilador impede.
3. **Roku:** o `:core` é a especificação executável do comportamento que a versão BrightScript deve reproduzir, com as mesmas fixtures.

## Responsabilidades

| Componente | Responsabilidade | Não faz |
|---|---|---|
| Telas Compose | Exibir estado e emitir eventos | Rede, banco, parsing |
| ViewModels | Coordenar estado (Loading/Success/Empty/Error), paginação, debounce de busca | Montar URLs, acessar OkHttp |
| `SessionManager` | Fonte ativa, estado da conexão, sincronização do catálogo (LIVE → MOVIE → SERIES) | UI |
| `ValidateConnectionUseCase` | Validar/normalizar o formulário e separar perfil × segredos | Rede |
| Repositórios (`data/repository`) | Implementar os contratos do core: cache em Room, paginação, favoritos, histórico | Lógica de protocolo |
| `ConnectorProvider` | Criar/reaproveitar o conector de cada fonte com os segredos descriptografados | Persistir segredos |
| `XtreamConnector` / `M3uConnector` | Protocolo, validação de respostas, mapeamento para o domínio, URL de reprodução | Cache, UI |
| `PlayerController` | Integrar Media3: estados, faixas, proporção, reconexão, liberação | Resolver URL |
| `PlaybackSourceProvider` | Resolver `PlaybackRequest` → `StreamSource` no momento de tocar | Persistir URLs |
| `CredentialStore` | Cifrar/decifrar segredos com Android Keystore | — |

## Fluxo de dados

**Cadastro de fonte**
`ConnectionScreen` → `ConnectionViewModel.submit()` → `ValidateConnectionUseCase` (perfil + segredos) → `AuthenticationRepository.saveAndActivate()` → conector `authenticate()` → só em caso de sucesso: segredos → `CredentialStore`, perfil → Room, fonte ativa → DataStore → `SessionManager` detecta a nova fonte, verifica a conexão e baixa o catálogo.

**Catálogo**
`SessionManager.refreshCatalog()` → `CatalogRepositoryImpl.refresh*()` → conector → transação Room (apaga e regrava o catálogo da fonte em blocos de 500). As telas leem do Room com consultas paginadas (120 itens por página) e busca por termos normalizados (`searchName LIKE ?` por termo). Sem rede, o app continua usando o último catálogo.

**Reprodução**
Tela → rota com **identificadores apenas** → `PlayerViewModel` → (canal: lê `streamKey` do cache) → `PlaybackSourceProvider.resolve()` → conector `resolveStream()` (aplica a política HTTP) → `PlayerController.play()`. A URL resolvida vive só em memória; o histórico guarda id, título, imagem e posição.

## Segurança

| Ameaça | Medida |
|---|---|
| Senha em disco | AES-256/GCM com chave do Android Keystore; Room e DataStore não recebem senhas nem URLs de playlist; backup em nuvem desativado. |
| Credenciais em HTTP | Bloqueadas até consentimento explícito por fonte (`allowInsecureHttp`), verificado no conector **e** no cliente HTTP. |
| Credenciais enviadas a outro domínio | Redirecionamentos seguidos manualmente; com credenciais na URL, só para o mesmo host. `server_info.url` do Xtream é ignorado para montar URLs. |
| Rebaixamento https→http | Bloqueado no cliente de API e no OkHttp do player (`followSslRedirects(false)` para fontes HTTPS). |
| TLS | Somente CAs do sistema; a verificação nunca é desativada. |
| Vazamento em logs/erros | `SecretRedactor` remove userinfo, parâmetros sensíveis e caminhos `/live/usuario/senha/`; `toString()` de `StreamSource`, `SourceSecrets` e `HttpRequest` é redigido. |
| Injeção na URL de stream | Extensões validadas (`[a-z0-9]{1,5}`), segmentos de caminho codificados. |
| SSRF | Não se aplica a esta versão (sem backend). Se houver API própria SAM TV no futuro, o backend deve validar destinos (bloquear IPs privados/metadados, limitar esquemas e redirecionamentos). |

## Persistência

- **Room** (`samtv.db`): `profiles`, `categories`, `channels`, `movies`, `series`, `favorites`, `history`, `sync_state`. Índices por `(sourceId, categoryId, position)`, `(sourceId, searchName)` e `(sourceId, lastWatchedAt)`.
- **DataStore**: fonte ativa e formato preferido da TV ao vivo.
- **SharedPreferences cifradas manualmente** (`samtv_secure_credentials`): apenas texto cifrado (IV + dados GCM).

## Player e ciclo de vida

- O `ExoPlayer` é criado **uma vez por tela de player** dentro do `PlayerViewModel`: sobrevive à rotação e não é recriado em recomposições; `release()` em `onCleared`.
- `ON_STOP` pausa e salva a posição; `ON_START` retoma se estava tocando.
- Reconexão automática para erros transitórios: 3 tentativas (ao vivo) ou 2 (VOD), com espera de 1 s, 2 s e 4 s; `BEHIND_LIVE_WINDOW` volta para a borda ao vivo.
- Controles só aparecem quando suportados: barra de progresso apenas se o player confirma que o item é buscável e tem duração; selo **AO VIVO** apenas se `isCurrentMediaItemLive`; áudio quando há mais de uma faixa; legendas quando existem.

## Portabilidade para Roku

O que é portado: modelos, contratos dos conectores, regras de agrupamento/IDs/busca/histórico, validação de URL, política de segurança, fixtures de teste e comportamentos esperados — tudo em [`docs/ROKU-CONTRACTS.md`](docs/ROKU-CONTRACTS.md).

O que **não** é portado: Compose, ViewModels, Room, ExoPlayer. No Roku, a UI é SceneGraph, a rede é `roUrlTransfer` em Task nodes e o player é o nó `Video`.

**Compatibilidade não é garantida entre plataformas.** Um stream que toca no Android pode não tocar no Roku (e vice-versa): o Roku tem suporte nativo a HLS/DASH/MP4, mas MPEG-TS progressivo, alguns codecs de áudio (ex.: AC-3 sem passthrough) e cabeçalhos HTTP customizados têm restrições próprias. Por isso o formato da TV ao vivo é configurável e deve preferir HLS no Roku.
