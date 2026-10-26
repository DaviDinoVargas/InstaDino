# Segurança do Banco de Dados Local

Estado: implementado e testado em Linux (JDK 21). **Não verificado em Windows real** (ver "Pendências").

## O que está implementado

| Requisito | Implementação | Teste |
|---|---|---|
| DB-01 Cifragem em repouso | SQLite3 Multiple Ciphers via `io.github.willena:sqlite-jdbc`, cifra `chacha20` (ChaCha20-Poly1305), chave bruta de 256 bits | `BancoLocalTest.arquivoNaoContemCabecalhoNemTextoEmClaro`, `chaveErradaERecusada…`, `bancoCifradoNaoAbreSemChave` |
| DB-01 Verificação ativa | Após migrar, `BancoLocal.verificarCifragemAtiva()` faz checkpoint e recusa continuar se o arquivo tiver o cabeçalho `SQLite format 3` | idem |
| DB-02 Sem chave embutida | Chave aleatória (`SecureRandom`) por instalação em `ArmazemDeChave`, guardada **fora do banco** | `ArmazemDeChaveTest` |
| DB-03 DPAPI | `ProtetorDpapi` (JNA `Crypt32Util`) com entropia específica do aplicativo; fora do Windows o app **recusa** criar o banco, salvo opt-in explícito de desenvolvimento | `ArmazemDeChaveTest.foraDoWindows…`; `ProtetorDpapiTest` só executa no Windows |
| DB-04 Consultas parametrizadas | Somente `PreparedStatement`; comandos de migração vêm de arquivos versionados | `entradaMaliciosaEGravadaComoTexto…`, `ConsultasParametrizadasTest` (varredura estática com autoteste do detector) |
| DB-05 Migrações | `Migrador`: transação por migração, checksum SHA-256, recusa banco mais novo que o aplicativo, sequência sem lacunas | `MigradorTest` (9 testes) |
| DB-06 Integridade | `PRAGMA foreign_keys=ON`, `integrity_check`, `foreign_key_check`; restrições `CHECK` | `BancoLocalTest` |
| DB-07 Backup | Dois formatos. (1) `BackupLocal`: `VACUUM INTO` herda a cifragem e a chave da instalação; a restauração verifica o backup em cópia temporária antes de trocar e preserva o banco anterior em `.antes`. (2) `BackupComSenha` (Premium, recurso `backup.protegido`): arquivo portátil recifrado com chave derivada de uma senha do usuário (PBKDF2-HMAC-SHA256, 600.000 iterações, sal de 16 bytes, senha ≥ 12 caracteres, NFC); a restauração recifra para a chave da instalação de destino | `BancoLocalTest.backup…`, `restauracaoComChaveErrada…`, `BackupComSenhaTest` (11), `CasosDeUsoTest.backup…` |
| DB-08 Exclusão | `BancoLocal.excluirArquivos` sobrescreve com zeros e remove banco, `-wal`, `-shm`, `-journal`; `secure_delete=ON` | `exclusaoRemoveBancoEArquivosAuxiliares` |
| DB-09 Erros sem dados sensíveis | `BancoException` nunca carrega a URL, a chave ou a causa do driver; só o código SQLite | `chaveErradaERecusadaSemVazarDetalhes` |
| Transações/rollback | `emTransacao` usa `BEGIN IMMEDIATE`, desfaz em qualquer falha | `falhaNoMeioDaTransacaoDesfazTudo` |
| Recuperação após encerramento inesperado | WAL + `synchronous=FULL`; processo filho é encerrado com `Runtime.halt` no meio de uma transação | `RecuperacaoAposQuedaTest` |
| Acesso concorrente | 8 threads × 25 escritas, sem perda e com integridade preservada | `escritasConcorrentesNaoSePerdemNemCorrompem` |

## Achado durante a implementação (importante)

Ao validar o driver, passar `cipher` e `hexkey` pelo objeto `Properties` **não cifrou o arquivo e não gerou erro**: o banco ficou em claro e abria sem chave. Executar `PRAGMA hexkey` após conectar cifra, mas o driver aplica pragmas de configuração antes e impede reabrir o arquivo. O único caminho comprovado é passar `cipher` e `hexkey` como **parâmetros da URL**. Consequências adotadas:

1. A URL contém a chave em hexadecimal; por isso nunca é registrada em log nem anexada a exceções.
2. A verificação ativa de cifragem é obrigatória e testada, para que uma regressão (por exemplo, uma atualização do driver) seja detectada em vez de ficar silenciosa.

## Limites de proteção (declarados)

- **DPAPI** vincula a chave ao usuário do Windows. Protege contra leitura do disco por outras contas e por quem copia os arquivos. **Não protege** contra malware executando como o mesmo usuário, contra administradores, nem em perfil Windows compartilhado entre pessoas.
- **Backup comum** (`BackupLocal`) usa a chave da instalação, que é protegida pela DPAPI: **não restaura em outro computador ou conta Windows**. Por isso o aplicativo oferece o **backup protegido por senha**, que é portátil.
- **Backup com senha**: a segurança é a da senha. O arquivo pode ser atacado offline sem limite de tentativas; o custo por tentativa é o do PBKDF2 (600.000 iterações), não resistente a hardware dedicado como o Argon2 (que não está na JDK). Senha fraca = backup fraco. Sem a senha ninguém recupera os dados, nem o InstaDino. A senha digitada passa por `char[]` zerado após o uso, mas cópias imutáveis (`String` da normalização, campo da interface) podem permanecer na memória até a coleta. A restauração substitui todos os dados; o banco anterior fica em `instadino.db.antes` (o evento de restauração não é registrado porque o banco de eventos é o que foi substituído).
- **Exclusão segura** é melhor esforço: em SSDs com nivelamento de desgaste e em sistemas de arquivos com cópia ao escrever, a sobrescrita não garante a eliminação física. A cifragem é a proteção principal; apagar a chave também inutiliza o banco.
- A memória do processo contém a chave enquanto o banco está aberto. O aplicativo zera a cópia interna ao fechar o `BancoLocal`.
- Os temporários da verificação de backup são cifrados e removidos ao final.

## Pendências

- [x] Fluxo completo em Windows real **no CI do GitHub** (`mvn verify` + teste de fumaça que confere o blob DPAPI no arquivo da chave). Falta o teste manual em máquina de usuário (ver `WINDOWS_BUILD.md`).
- [x] Backup portátil com senha. - [ ] Rotação periódica da chave da instalação (o `trocarChave` já existe para backups, mas não há fluxo para o banco ativo).
- [ ] Permissões de ACL do diretório de dados no Windows (hoje depende do perfil do usuário).
- [ ] Revisar licença e procedência do binário nativo `sqlite-jdbc` da `willena` para o inventário de terceiros.
