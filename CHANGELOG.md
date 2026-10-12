# Changelog

## [1.0.0] — 2026-10-12 — primeira versão (celular Android)

### Adicionado
- Projeto multi-módulo: `:core` (Kotlin puro) e `:app` (Android, Compose + Material 3).
- Conexão **Xtream Codes**: validação real de `user_info`, estado/expiração da conta, formatos permitidos, TV ao vivo, filmes, séries (temporadas/episódios) e EPG curto.
- **Playlist M3U por URL**: parser incremental e tolerante (aspas, CRLF/BOM, `#EXTGRP`, `#EXTVLCOPT`, duplicatas, entradas incompletas), organização por grupo ou por letra.
- Catálogo em cache (Room) com paginação, busca sem acento e atualização sob demanda; funciona sem rede com o último catálogo.
- Player Media3: HLS/DASH/progressivo/MPEG-TS, tela cheia e paisagem, proporção, faixas de áudio e legendas (quando existirem), troca de canal, reconexão automática, retomada de VOD, liberação correta no ciclo de vida.
- Favoritos, histórico (sem duplicação, limite de 200 por fonte) e configurações (fontes, estado da conexão, catálogo, formato da TV, privacidade, versão).
- Segurança: credenciais cifradas com Android Keystore; HTTP somente com consentimento; redirecionamentos controlados; redação de segredos em erros; sem backup em nuvem.
- Testes: 42 no core + 7 JVM no app (executados) e suíte instrumentada com servidor simulado (pendente de execução).
- Workflow do GitHub Actions para gerar APKs e rodar testes.
- Documentação: README, INSTALL-WINDOWS, ARCHITECTURE, TEST-REPORT, contratos Roku e roteiro de testes manuais.

### Pendente
- Execução do build Gradle completo e dos testes instrumentados (ver TEST-REPORT.md).
- Catch-up/timeshift, guia XMLTV completo, PiP, Chromecast, internacionalização via `strings.xml`.
- Versão Roku (projeto separado).
