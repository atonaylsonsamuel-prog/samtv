# Contratos para a futura versão Roku

Este documento descreve **dados, regras e comportamentos** que a versão Roku (BrightScript + SceneGraph) deve reproduzir. As classes Kotlin não são compartilhadas; a referência executável é o módulo `:core` e suas fixtures em `core/src/test/resources/fixtures/`.

## 1. Modelos (representação sugerida: `roAssociativeArray`)

Campos com `?` são opcionais (podem faltar/ser `invalid`). Datas em epoch **segundos**, exceto onde indicado `Millis`.

| Modelo | Campos |
|---|---|
| ConnectionProfile | `id`, `name`, `type` ("XTREAM"\|"M3U"), `address`, `username?`, `allowInsecureHttp`, `createdAtMillis`, `updatedAtMillis` |
| SourceSecrets | `password?`, `playlistUrl?` — guardar em `roRegistrySection` próprio; nunca exibir/logar |
| AuthenticationResult | `status` (ACTIVE\|EXPIRED\|BANNED\|DISABLED\|UNKNOWN), `expiresAtEpochSeconds?`, `maxConnections?`, `activeConnections?`, `allowedOutputFormats[]`, `serverTimezone?`, `capabilities{live,movies,series,epg}`, `serverMessage?`, `itemCount?` |
| ChannelCategory | `id`, `sourceId`, `name`, `kind` (LIVE\|MOVIE\|SERIES), `position` |
| Channel | `id`, `sourceId`, `name`, `number?`, `logoUrl?`, `categoryId?`, `epgChannelId?`, `hasArchive`, `streamKey`, `containerExtension?`, `httpHeaders{}`, `position` |
| Movie | `id`, `sourceId`, `name`, `posterUrl?`, `categoryId?`, `rating?`, `year?`, `containerExtension?`, `addedEpochSeconds?`, `position` |
| MovieDetails | `movie`, `plot?`, `genre?`, `cast?`, `director?`, `releaseDate?`, `durationSeconds?`, `backdropUrl?` |
| Series | `id`, `sourceId`, `name`, `coverUrl?`, `categoryId?`, `plot?`, `genre?`, `releaseDate?`, `rating?`, `position` |
| Season | `number`, `name`, `coverUrl?`, `episodeCount?` |
| Episode | `id`, `seriesId`, `seasonNumber`, `episodeNumber?`, `title`, `plot?`, `durationSeconds?`, `imageUrl?`, `containerExtension?` |
| ElectronicProgramGuideEntry | `channelEpgId?`, `title`, `description?`, `startEpochSeconds`, `endEpochSeconds` |
| PlaybackRequest | `sourceId`, `contentId`, `type` (CHANNEL\|MOVIE\|SERIES\|EPISODE), `title`, `imageUrl?`, `streamKey`, `containerExtension?`, `httpHeaders{}`, `parentId?` |
| StreamSource | `url`, `kind` (LIVE\|VOD), `formatHint?` ("hls"\|"dash"\|"progressive"\|"rtsp"), `httpHeaders{}` — **somente em memória** |
| Favorite | `sourceId`, `contentId`, `type`, `title`, `imageUrl?`, `addedAtMillis` |
| PlaybackHistory | `sourceId`, `contentId`, `type`, `title`, `imageUrl?`, `parentId?`, `positionMs`, `durationMs?`, `lastWatchedAtMillis` |

## 2. Identificadores (devem ser idênticos entre plataformas)

- Categoria: `"{sourceId}:{kind minúsculo}:cat:{idOriginal}"` (Xtream: `category_id`; M3U: SHA-1 do nome em minúsculas, 20 primeiros hex).
- Xtream: canal `"{sourceId}:live:{stream_id}"`, filme `"{sourceId}:vod:{stream_id}"`, série `"{sourceId}:series:{series_id}"`, episódio `"{sourceId}:ep:{id}"`. O id do servidor é o trecho após o último `:`.
- M3U: `"{sourceId}:ch:" + sha1(tvgId + "|" + título.lower() + "|" + categoria.lower())[0..20]`, com sufixo `-2`, `-3`… em colisões. **A URL não entra no id** (tokens mudam a cada atualização; favoritos precisam sobreviver).

## 3. Conector Xtream Codes

Todas as chamadas: `GET {base}/player_api.php?username={u}&password={p}[&action=…]` com parâmetros codificados (`application/x-www-form-urlencoded`).

| Ação | Uso |
|---|---|
| *(sem action)* | Autenticação |
| `get_live_categories`, `get_live_streams` | TV ao vivo |
| `get_vod_categories`, `get_vod_streams`, `get_vod_info&vod_id=` | Filmes |
| `get_series_categories`, `get_series`, `get_series_info&series_id=` | Séries |
| `get_short_epg&stream_id=&limit=` | EPG curto (títulos/descrições podem vir em Base64) |

**Validação da autenticação** (HTTP 200 não basta):
1. Corpo vazio, HTML ou JSON inválido → *resposta inválida*.
2. Sem objeto `user_info` ou sem campo `auth` → *resposta inválida*.
3. `auth` ≠ 1/"1"/true → *não autorizado*. HTTP 401/403 → *não autorizado*.
4. `status` "Expired" → *conta expirada* (com `exp_date`); "Banned"/"Disabled" → *conta indisponível*.
5. `allowed_output_formats` define o formato da TV ao vivo.

**Tolerância de tipos:** qualquer campo pode vir como número, string, string vazia, `null` ou array. Listas podem vir como `[]`, `{}` ou `null` (= vazio). Itens sem id ou sem nome são descartados. Categorias sem itens são omitidas.

**Episódios:** `episodes` pode ser objeto `{"1":[…]}` ou array de arrays. Temporada = `season` do episódio, senão a chave. Só são exibidas temporadas com episódios.

**URLs de reprodução** (segmentos codificados; sempre no `base` configurado, nunca em `server_info.url`):
- ao vivo: `{base}/live/{u}/{p}/{stream_id}.{ext}` — ext: preferência do usuário se permitida; senão `m3u8` se permitido; senão `ts`.
- filme: `{base}/movie/{u}/{p}/{stream_id}.{container_extension|mp4}`
- episódio: `{base}/series/{u}/{p}/{episode_id}.{container_extension|mp4}`
- extensão válida apenas se casar com `[a-z0-9]{1,5}`.

## 4. Parser M3U (regras)

Ver `core/.../m3u/M3uParser.kt` e `M3uParserTest`. Resumo:
- Linhas: remover `\r`, BOM na 1ª linha, espaços nas pontas; ignorar vazias.
- `#EXTM3U` marca cabeçalho (opcional). `#EXTINF:` inicia entrada; `#EXTGRP:` define grupo da próxima entrada; `#EXTVLCOPT:http-user-agent|http-referrer|http-origin` viram cabeçalhos; outras linhas `#` são ignoradas.
- `#EXTINF:<duração> atributos,título` — o título começa na **primeira vírgula fora de aspas**; atributos aceitam `"…"`, `'…'` ou sem aspas; chaves em minúsculas.
- Linha não-comentário = URL; esquemas aceitos `http`, `https`, `rtsp`; demais → problema `UNSUPPORTED_URL`.
- `#EXTINF` sem URL → `MISSING_URL`. Duplicata = mesma URL **e** mesmo `tvg-id` (ou título, se não houver `tvg-id`) → descartada.
- Título vazio → `tvg-name` → nome derivado da URL.

**Organização M3U:** com `group-title` → uma categoria por grupo (ordem de aparição); entradas sem grupo, quando outras têm → "Sem categoria"; nenhuma com grupo → categorias por letra inicial (A–Z, `#` para o resto, ordenadas com `#` no fim). Todas as entradas são canais.

## 5. Regras compartilhadas

- **Busca:** minúsculas, remover acentos (NFD sem marcas), não alfanuméricos → espaço; todos os termos devem estar contidos no nome (qualquer ordem).
- **Histórico:** uma linha por `(fonte, tipo, conteúdo)`; episódios de uma série ocupam **uma** linha (chave `fonte|series|{seriesId}`); máximo 200 por fonte; mais recente primeiro. Nunca guardar URL de stream.
- **Retomar VOD:** oferecer "continuar" se posição > 10 s e < duração − 30 s.
- **HTTP:** nenhuma requisição `http://` sem `allowInsecureHttp`; nunca seguir redirecionamento https→http; com credenciais na URL, só seguir redirecionamentos para o mesmo host.
- **Reconexão:** ao vivo 3 tentativas, VOD 2; espera 1 s, 2 s, 4 s; erros 401/403/404, codec e DRM não são repetidos.
- **Mensagens:** as mesmas categorias de erro de `AppError` (ver `app/.../ErrorMessages.kt` para os textos).

## 6. Diferenças de plataforma (Android × Roku)

| Tema | Android (esta versão) | Roku |
|---|---|---|
| HLS | Media3 HLS | Nativo (`streamFormat: "hls"`) — preferir |
| MPEG-TS progressivo (`.ts`) | Suportado (extrator TS) | Suporte limitado; usar HLS sempre que o servidor permitir |
| MKV | Suportado (extrator Matroska) | Suportado com restrições de codec |
| Cabeçalhos por stream | OkHttp por stream | `HttpSendCookies`/`setHeaders` no nó `Video` (verificar suporte por firmware) |
| Credenciais | Android Keystore | `roRegistrySection` (não é cifrado por hardware — documentar) |
| Rede | OkHttp em coroutines | `roUrlTransfer` dentro de `Task` nodes (nunca na render thread) |
| Navegação | Toque | Controle remoto (foco, `onKeyEvent`) |

## 7. Fixtures reutilizáveis

`core/src/test/resources/fixtures/` — `m3u/valid.m3u`, `m3u/quirks.m3u`, `xtream/auth_ok.json`, `xtream/live_categories.json`, `xtream/live_streams.json`, `xtream/series_info.json`, `xtream/vod_info.json`. Todos são dados de exemplo (domínios `*.example.test`). Os casos esperados estão nos testes `M3uParserTest`, `XtreamConnectorTest`, `UrlAndSecurityTest` e `CatalogRulesTest` — o Roku deve passar nos mesmos casos.
