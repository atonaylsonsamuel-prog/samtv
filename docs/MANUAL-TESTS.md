# Roteiro de testes manuais — fonte autorizada

Use uma conta/lista que você tem autorização para acessar. Não registre usuário, senha ou URLs com token em capturas de tela, relatórios ou tickets.

Aparelho: ________ Android: ____ Versão do app: ________ Fonte: Xtream / M3U (http / https)

| # | Passo | Resultado esperado | OK? |
|---|---|---|---|
| 1 | Instalar e abrir pela 1ª vez | Marca SAM TV OFICIAL; tela de boas-vindas com Xtream, M3U e instruções | |
| 2 | Xtream com senha errada | "Usuário ou senha incorretos…"; nada é salvo | |
| 3 | Xtream `http://` sem marcar consentimento | Mensagem pedindo consentimento; nenhuma requisição | |
| 4 | Xtream correto | Vai para Início; estado "Conta ativa · vence em …"; barra "Baixando canais/filmes/séries" | |
| 5 | Colar URL `get.php?username=…&password=…` no endereço | Campos usuário/senha preenchidos automaticamente | |
| 6 | TV ao vivo → categorias, busca "abc" sem acento | Lista filtra; busca ignora acentos/maiúsculas | |
| 7 | Rolar até o fim de uma categoria grande | Mais canais carregam sem travar | |
| 8 | Abrir canal | Vídeo inicia; "AO VIVO" só aparece se o player confirmar | |
| 9 | Anterior/próximo canal | Troca dentro da categoria | |
| 10 | Girar o aparelho durante a reprodução | Vídeo continua sem recomeçar; paisagem ocupa a tela | |
| 11 | Botão tela cheia em retrato | Gira para paisagem; voltar sai da tela cheia | |
| 12 | Proporção (Ajustar/Preencher/Esticar) | Muda o enquadramento | |
| 13 | Áudio/legendas em um filme com várias faixas | Botões só aparecem se houver faixas; troca funciona | |
| 14 | Desligar Wi-Fi durante a reprodução | "Reconectando (1/3)…" e depois mensagem de erro com "Tentar novamente" | |
| 15 | Home do Android durante a reprodução e voltar | Pausa ao sair; retoma ao voltar | |
| 16 | Filmes → detalhes → assistir, parar no meio, reabrir | "Continuar de mm:ss" | |
| 17 | Séries → temporada → episódio | Toca; detalhes mostram "Último assistido" | |
| 18 | Favoritar canal, filme e série | Aparecem em Favoritos e na Início; remover funciona | |
| 19 | Histórico | Sem duplicatas; limpar pede confirmação | |
| 20 | Ajustes → formato TV (HLS/TS) | Canais que falhavam passam a abrir (quando o servidor permite) | |
| 21 | Ajustes → atualizar catálogo / limpar cache | Datas e contagens atualizam; após limpar, listas pedem atualização | |
| 22 | Modo avião e abrir o app | Interface abre com catálogo salvo; estado "Sem conexão" | |
| 23 | Adicionar playlist M3U (https) | Canais por grupo; Filmes/Séries indicam indisponível em M3U | |
| 24 | M3U sem `group-title` | Categorias por letra inicial | |
| 25 | Editar fonte deixando a senha vazia | Mantém a senha salva | |
| 26 | Remover fonte (confirmação) | Some catálogo/favoritos/histórico dela; sem fontes → boas-vindas | |
| 27 | Servidor HTTPS com certificado inválido | Erro de certificado; conexão não é feita | |
| 28 | Conferir `adb logcat` durante os testes | Nenhuma senha/token em texto | |
