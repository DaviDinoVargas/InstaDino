# Modelo de Dados Local

Banco SQLite cifrado. Esquema definido por migrações versionadas em `persistence/src/main/resources/db/migracoes/`. Migrações publicadas **nunca são editadas** (o checksum é conferido); mudanças entram como nova versão.

## Versão atual: V004

| Tabela | Finalidade | Observações |
|---|---|---|
| `perfil_local` | Perfis locais (vários no Premium) | `nome` limitado por `CHECK` |
| `importacao` | Uma linha por importação: data, versão do parser, origem, contagens de registros válidos/inválidos | `ON DELETE CASCADE` a partir do perfil |
| `importacao_categoria` | Categorias detectadas por importação (arquivos e registros) | PK composta |
| `relacionamento` | Seguidores, seguindo e demais listas, por importação | `tipo` restrito por `CHECK`; `WITHOUT ROWID` |
| `preferencia` | Preferências locais (tema, idioma) | chave/valor |
| `consentimento` | Consentimentos explícitos para módulos sensíveis | ausência = sem consentimento |
| `evento_seguranca` | Eventos relevantes (importação, exclusão) sem dados pessoais | detalhe limitado a 300 caracteres |
| `etiqueta` | Etiquetas criadas pelo usuário, por perfil | nome 1–40 caracteres, único por perfil sem distinguir caixa; máximo de 100 por perfil (regra do repositório) |
| `anotacao_conta` | Favorito e nota privada (≤ 2000 caracteres) sobre uma conta, por perfil | PK (perfil, usuário); a linha só existe enquanto houver favorito ou nota |
| `conta_etiqueta` | Etiquetas aplicadas a contas | só aceita etiqueta do mesmo perfil (verificado no repositório e por teste) |
| `relacionamento` (V003) | Recriada para aceitar os tipos `DEIXEI_DE_SEGUIR_RECENTE`, `SOLICITACAO_RECENTE` e `SOLICITACAO_RECEBIDA` | dados preservados na migração (testado) |
| `atividade` (V004) | Curtidas, comentários, salvos, pesquisas, visualizações e interações com stories: categoria, subtipo (nome do arquivo), conta, texto (≤ 1000), referência (≤ 500), instante | por importação; `ON DELETE CASCADE`; só é gravada no Premium |
| `publicacao` e `midia` (V004) | Catálogo de publicações, reels e stories e metadados das mídias (caminho, tipo, tamanho) | **as mídias em si não são copiadas**; ficam no ZIP original |
| `esquema_migracoes` | Histórico, checksum e data de cada migração | gerida pelo `Migrador` |

As anotações (V002) pertencem ao **perfil** e ao nome de usuário normalizado, não a uma importação: sobrevivem a novas importações e à exclusão de importações, e são apagadas junto com o perfil.

Tabelas de mensagens serão adicionadas por migração futura, junto com o recurso correspondente.

## Regras

- Chaves estrangeiras ativas em toda conexão; exclusão de uma importação remove seus registros em cascata.
- Nenhum dado desta base é enviado ao backend comercial.
