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
