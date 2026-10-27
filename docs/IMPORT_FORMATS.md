# Formatos de Importação

> **Ressalva importante.** A estrutura abaixo foi implementada a partir do formato JSON/HTML comumente observado nas exportações do Instagram e validada **apenas com arquivos sintéticos** (`shared-test-fixtures`). Não foi possível testar com uma exportação real, e o Instagram pode alterar nomes de pastas e campos a qualquer momento. Por isso o importador é tolerante: o que não reconhece é inventariado e nunca interpretado. **Antes da primeira release, valide com uma exportação real sua** e ajuste o classificador/parsers se necessário.

## Estado por categoria

| Categoria | Inventário | Registros extraídos nesta versão |
|---|---|---|
| Seguidores | sim | **sim** (`followers_N.json` / `.html`) |
| Contas seguidas | sim | **sim** (`following.json` / `.html`) |
| Solicitações pendentes, bloqueados, restritos, amigos próximos | sim | **sim** |
| Outras listas (deixou de seguir recentemente, hashtags, etc.) | sim | não (apenas inventariadas) |
| Perfil, segurança, mensagens | sim (marcadas como sensíveis) | **não** (planejado, Fase 4, com consentimento) |
| Publicações, stories, reels, comentários, curtidas, salvos, pesquisas, anúncios | sim | **não** (planejado, Fase 4) |
| Fotos e vídeos | sim (catalogados pelo nome e tamanho declarado, sem descompactar) | não |
| Arquivos desconhecidos | sim | nunca processados |

## Estrutura de relacionamentos aceita

JSON em streaming, com limites de profundidade (64) e de tamanho de texto (100.000 caracteres):

- Raiz como lista, ou objeto cujas chaves de lista contêm os registros.
- Cada registro pode ter `title` e uma lista `string_list_data` com `value`, `href` e `timestamp`.
- O nome de usuário é obtido, nesta ordem, de `value`, de `title` e da URL em `href`.
- O primeiro candidato que for um nome de usuário válido é usado. Registros sem candidato válido são **contados como inválidos**, nunca descartados em silêncio.

HTML: apenas links cujo endereço contém `instagram.com` são considerados; scripts e outros links são ignorados. O HTML não traz data legível de forma confiável, portanto a data fica vazia.

## Normalização

- Minúsculas, sem `@`, apenas `a-z 0-9 . _`, 1 a 30 caracteres.
- Duplicatas (na mesma lista ou entre partes `followers_1`, `followers_2`) contam uma vez, mantendo o menor carimbo de tempo.
- A URL do perfil é sempre **reconstruída** a partir do nome validado (`https://www.instagram.com/<usuario>/`); nunca se usa a URL do arquivo para abrir o navegador.

## Versão do parser

`ImportadorDeExportacao.VERSAO_DO_PARSER` = `1.0.0`, gravada em cada importação.

## O que o programa NÃO afirma

Ver `AnaliseDeSeguidores.LIMITACAO` e `ComparacaoDeImportacoes.LIMITACAO`: uma conta ausente numa lista não prova que deixou de seguir; diferenças entre exportações são observações, não eventos datados.

## Descobertas com uma exportação real (2026-10-26)

Validação feita pelo responsável do produto, que colou o diagnóstico por arquivo (estrutura e contagens, sem nomes):

- **Exportação com intervalo de datas.** Uma exportação pedida com "último ano" trouxe `followers_1.json` com registros só dos últimos 12 meses (52 de 616 seguidores), enquanto `following.json` cobria desde 2018 (625 contas). O leitor não perdeu nada; o arquivo já vinha parcial. O importador agora detecta essa assinatura (uma lista cobrindo ~1 ano terminando na data da exportação e a outra com pelo menos 6 meses a mais), marca a lista como **possivelmente incompleta**, guarda o aviso com a importação e o mostra no painel, nas listas e nos relatórios PDF. É um indício, não uma prova.
- **`label_values`.** Listas como `blocked_profiles.json` e `close_friends.json` usam `{fbid, label_values:[{label, value}...], media, timestamp}`; os rótulos mudam com o idioma, então o nome de usuário é procurado pelo endereço do perfil e, na falta dele, em rótulos que contenham "user"/"usu".
- **Registro único na raiz.** `pending_follow_requests.json` é UM registro (`{timestamp, label_values:[...]}`), não uma lista; a raiz é lida como o próprio elemento.
- `following.json` usa `{title:<usuário>, string_list_data:[{href, timestamp}]}` sem `value`.
- Arquivos como `follow_requests_you've_received.json`, `recent_follow_requests.json` e `recently_unfollowed_profiles.json` são inventariados como "outras listas de relacionamentos" e ainda **não** são processados.

Limitação: estas conclusões vêm de UMA exportação, em português. Outros idiomas e versões do Instagram podem diferir.

## Atividade e conteúdo (leitor 1.4.0)

**Validação: somente com exportações sintéticas montadas a partir dos formatos descritos e do diagnóstico real dos arquivos de relacionamentos. Os formatos de atividade e conteúdo ainda NÃO foram conferidos com uma exportação real.** O diagnóstico por arquivo mostra, para cada arquivo, elementos lidos, aceitos, descartados e a forma do JSON; use-o para conferir.

- **Atividade** (`CURTIDAS`, `COMENTARIOS`, `SALVOS`, `PESQUISAS`, `VISUALIZACOES`, `INTERACOES_STORIES`): leitura independente do idioma dos rótulos. Instante: `timestamp` do elemento ou o primeiro carimbo encontrado (milissegundos são convertidos). Conta: `title`, um valor que seja nome de usuário ou o endereço de perfil; endereços de publicação (`/p/`, `/reel/`, `/stories/`) nunca viram conta. Texto só em comentários e pesquisas. Dicas de rótulo (comment/comentário, owner/dono/autor/usuário) apenas desempatam.
- **Conteúdo** (`PUBLICACOES`, `REELS`, `STORIES`): `media[{uri, creation_timestamp, title}]` ou `uri` direto. As mídias não são lidas nem extraídas; o caminho declarado é resolvido contra o inventário do ZIP para obter tipo e tamanho.
- **Texto corrompido:** exportações JSON gravam UTF-8 como Latin-1 ("ção" vira "Ã§Ã£o"); a correção só é aplicada quando o texto inteiro cabe em Latin-1 e forma UTF-8 válido.
- **Limites:** 1.000.000 registros de atividade e 200.000 publicações por importação (o excedente é descartado com aviso e a categoria é marcada incompleta). Arquivos HTML dessas categorias não são lidos (aviso para exportar em JSON).
- **Free:** atividade e conteúdo não são lidos nem guardados; ao assinar é preciso importar de novo.
