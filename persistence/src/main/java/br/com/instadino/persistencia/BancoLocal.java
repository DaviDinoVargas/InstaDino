package br.com.instadino.persistencia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Acesso ao banco SQLite local cifrado (SQLite3 Multiple Ciphers, ChaCha20-Poly1305).
 *
 * <p>A chave é aplicada por parâmetros da URL do driver, o único caminho que o experimento do projeto
 * comprovou cifrar o arquivo (passar a chave via {@code Properties} deixou o arquivo em claro). Por isso
 * a URL nunca é registrada em log nem anexada a exceções, e {@link #verificarCifragemAtiva()} confere o
 * arquivo em disco.
 */
public final class BancoLocal implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(BancoLocal.class.getName());
    private static final byte[] CABECALHO_SQLITE_EM_CLARO = "SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    private static final String CIFRA = "chacha20";

    /** Operação executada dentro de uma transação. */
    @FunctionalInterface
    public interface Transacao<T> {
        T executar(Connection conexao) throws SQLException;
    }

    private final Path arquivo;
    private final byte[] chave;
    private boolean fechado;

    private BancoLocal(Path arquivo, byte[] chave) {
        this.arquivo = arquivo.toAbsolutePath();
        this.chave = Arrays.copyOf(chave, chave.length);
    }

    /** Abre (ou cria) o banco, aplica as migrações e confirma que o arquivo está cifrado. */
    public static BancoLocal abrir(Path arquivo, byte[] chave, List<br.com.instadino.persistencia.migracao.Migracao> migracoes) {
        Objects.requireNonNull(arquivo, "arquivo");
        Objects.requireNonNull(chave, "chave");
        if (chave.length != 32) {
            throw new IllegalArgumentException("A chave deve ter 256 bits.");
        }
        try {
            Files.createDirectories(arquivo.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new BancoException("Não foi possível criar o diretório de dados.");
        }
        BancoLocal banco = new BancoLocal(arquivo, chave);
        try {
            new br.com.instadino.persistencia.migracao.Migrador(banco).aplicar(migracoes);
            banco.verificarCifragemAtiva();
        } catch (RuntimeException e) {
            banco.close();
            throw e;
        }
        return banco;
    }

    /** Abre um banco existente somente para verificação (sem migrar nem gravar), como na restauração de backups. */
    public static BancoLocal abrirParaVerificacao(Path arquivo, byte[] chave) {
        Objects.requireNonNull(arquivo, "arquivo");
        if (chave == null || chave.length != 32) {
            throw new IllegalArgumentException("A chave deve ter 256 bits.");
        }
        if (!Files.isRegularFile(arquivo)) {
            throw new BancoException("O arquivo do banco não existe.");
        }
        BancoLocal banco = new BancoLocal(arquivo, chave);
        try {
            banco.verificarAcessoComChave();
        } catch (RuntimeException e) {
            banco.close();
            throw e;
        }
        return banco;
    }

    private void verificarAcessoComChave() {
        try (Connection c = conexao(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM sqlite_master")) {
            r.next();
        } catch (SQLException e) {
            throw new BancoException("Não foi possível ler o banco com a chave informada.", e.getErrorCode());
        }
    }

    public Path arquivo() {
        return arquivo;
    }

    /** Abre uma nova conexão; quem chama deve fechá-la. */
    public Connection conexao() {
        if (fechado) {
            throw new BancoException("O banco local está fechado.");
        }
        String hex = HexFormat.of().formatHex(chave);
        String url = "jdbc:sqlite:file:" + arquivo + "?cipher=" + CIFRA + "&hexkey=" + hex;
        Connection conexao = null;
        try {
            conexao = DriverManager.getConnection(url);
            try (Statement s = conexao.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA secure_delete = ON");
                s.execute("PRAGMA busy_timeout = 10000");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("PRAGMA synchronous = FULL");
            }
            return conexao;
        } catch (SQLException e) {
            fecharSilencioso(conexao);
            // Nunca propagar a causa: a mensagem do driver pode incluir detalhes da conexão.
            LOG.log(System.Logger.Level.WARNING, "Falha ao abrir o banco local (código {0}).", e.getErrorCode());
            throw new BancoException("Não foi possível abrir o banco local. Verifique a chave e a integridade do arquivo.", e.getErrorCode());
        }
    }

    /** Executa a operação em uma transação exclusiva; confirma no sucesso e desfaz em qualquer falha. */
    public <T> T emTransacao(Transacao<T> operacao) {
        try (Connection c = conexao(); Statement s = c.createStatement()) {
            s.execute("BEGIN IMMEDIATE");
            try {
                T resultado = operacao.executar(c);
                s.execute("COMMIT");
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    s.execute("ROLLBACK");
                } catch (SQLException ignorada) {
                    LOG.log(System.Logger.Level.WARNING, "Falha ao desfazer transação (código {0}).", ignorada.getErrorCode());
                }
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Falha em transação (código {0}).", e.getErrorCode());
            throw new BancoException("Falha ao acessar o banco local.", e.getErrorCode());
        }
    }

    /** Lê o conjunto de problemas de integridade; lista vazia significa íntegro. */
    public List<String> verificarIntegridade() {
        List<String> problemas = new ArrayList<>();
        try (Connection c = conexao(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
                while (r.next()) {
                    String linha = r.getString(1);
                    if (!"ok".equalsIgnoreCase(linha)) {
                        problemas.add("integridade: " + linha);
                    }
                }
            }
            try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
                while (r.next()) {
                    problemas.add("chave estrangeira violada na tabela " + r.getString("table"));
                }
            }
        } catch (SQLException e) {
            throw new BancoException("Falha ao verificar a integridade do banco local.", e.getErrorCode());
        }
        return problemas;
    }

    /**
     * Confirma, no arquivo em disco, que o conteúdo NÃO está em claro. Força um checkpoint para que o arquivo
     * principal reflita os dados. Lança exceção se encontrar o cabeçalho padrão do SQLite.
     */
    public void verificarCifragemAtiva() {
        try (Connection c = conexao(); Statement s = c.createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException e) {
            throw new BancoException("Falha ao consolidar o banco local.", e.getErrorCode());
        }
        try {
            if (Files.size(arquivo) < CABECALHO_SQLITE_EM_CLARO.length) {
                throw new BancoException("O banco local não foi gravado como esperado.");
            }
            byte[] inicio = new byte[CABECALHO_SQLITE_EM_CLARO.length];
            try (var in = Files.newInputStream(arquivo)) {
                int lidos = in.readNBytes(inicio, 0, inicio.length);
                if (lidos == inicio.length && Arrays.equals(inicio, CABECALHO_SQLITE_EM_CLARO)) {
                    throw new BancoException("A cifragem do banco local não está ativa. Operação abortada.");
                }
            }
        } catch (IOException e) {
            throw new BancoException("Não foi possível verificar o arquivo do banco local.");
        }
    }

    /** Remove o banco e seus arquivos auxiliares, sobrescrevendo-os antes (melhor esforço; ver limites em DATABASE_SECURITY.md). */
    public static void excluirArquivos(Path arquivo) {
        for (String sufixo : List.of("", "-wal", "-shm", "-journal")) {
            Path alvo = Path.of(arquivo.toAbsolutePath() + sufixo);
            try {
                if (Files.isRegularFile(alvo)) {
                    sobrescrever(alvo);
                    Files.delete(alvo);
                }
            } catch (IOException e) {
                throw new BancoException("Não foi possível excluir os dados locais.");
            }
        }
    }

    private static void sobrescrever(Path alvo) throws IOException {
        long tamanho = Files.size(alvo);
        byte[] zeros = new byte[8192];
        try (var canal = java.nio.channels.FileChannel.open(alvo, java.nio.file.StandardOpenOption.WRITE)) {
            long escrito = 0;
            while (escrito < tamanho) {
                int n = (int) Math.min(zeros.length, tamanho - escrito);
                canal.write(java.nio.ByteBuffer.wrap(zeros, 0, n), escrito);
                escrito += n;
            }
            canal.force(true);
        }
    }

    private static void fecharSilencioso(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException ignorada) {
                // nada a fazer
            }
        }
    }

    @Override
    public void close() {
        fechado = true;
        Arrays.fill(chave, (byte) 0);
    }
}
