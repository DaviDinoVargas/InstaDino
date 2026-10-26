# Modelo de Dados Local

Banco SQLite cifrado. Esquema definido por migrações versionadas em `persistence/src/main/resources/db/migracoes/`. Migrações publicadas **nunca são editadas** (o checksum é conferido); mudanças entram como nova versão.

## Versão atual: V001

| Tabela | Finalidade | Observações |
|---|---|---|
| `perfil_local` | Perfis locais (vários no Premium) | `nome` limitado por `CHECK` |
| `importacao` | Uma linha por importação: data, versão do parser, origem, contagens de registros válidos/inválidos | `ON DELETE CASCADE` a partir do perfil |
| `importacao_categoria` | Categorias detectadas por importação (arquivos e registros) | PK composta |
| `relacionamento` | Seguidores, seguindo e demais listas, por importação | `tipo` restrito por `CHECK`; `WITHOUT ROWID` |
| `preferencia` | Preferências locais (tema, idioma) | chave/valor |
| `consentimento` | Consentimentos explícitos para módulos sensíveis | ausência = sem consentimento |
| `evento_seguranca` | Eventos relevantes (importação, exclusão) sem dados pessoais | detalhe limitado a 300 caracteres |
| `esquema_migracoes` | Histórico, checksum e data de cada migração | gerida pelo `Migrador` |

Tabelas de etiquetas, favoritos, notas, atividade, conteúdo e mensagens serão adicionadas por migrações futuras, junto com os recursos correspondentes.

## Regras

- Chaves estrangeiras ativas em toda conexão; exclusão de uma importação remove seus registros em cascata.
- Nenhum dado desta base é enviado ao backend comercial.
