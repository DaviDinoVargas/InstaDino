# ADR-0002 — SQLite cifrado e proteção da chave

Status: aceita (verificação em Windows real pendente)

**Contexto.** Requisito DB-01…DB-03: dados em repouso protegidos, sem criptografia própria e sem chave embutida.

**Decisão.** Driver `io.github.willena:sqlite-jdbc` (SQLite3 Multiple Ciphers), cifra ChaCha20-Poly1305/SQLCipher compatível. Chave de 256 bits gerada com `SecureRandom` na primeira execução e protegida com DPAPI (`Crypt32Util` do JNA) no Windows. O arquivo da chave protegida fica fora do banco.

**Alternativas.** SQLCipher oficial exige distribuição nativa e licenciamento próprios; `sqlite-jdbc` do xerial não cifra.

**Consequências.** Em Linux/macOS (apenas desenvolvimento) a chave é protegida por permissões de arquivo e o modo é marcado como NÃO SEGURO para produção. Teste obrigatório: o arquivo não contém o cabeçalho `SQLite format 3`.
