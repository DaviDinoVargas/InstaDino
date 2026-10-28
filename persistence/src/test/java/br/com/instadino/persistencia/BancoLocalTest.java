package br.com.instadino.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BancoLocalTest {

    @TempDir
    Path pasta;

    private BancoLocal abrir(byte[] chave) {
        return BancoLocal.abrir(pasta.resolve("dados.db"), chave, CarregadorDeMigracoes.doClasspath());
    }

    private void inserirPreferencia(BancoLocal banco, String chave, String valor) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO preferencia (chave, valor) VALUES (?, ?)")) {
                p.setString(1, chave);
                p.setString(2, valor);
                p.executeUpdate();
            }
            return null;
        });
    }

    // ---- DB-01: cifragem em repouso ----

    @Test
    void arquivoNaoContemCabecalhoNemTextoEmClaro() throws Exception {
        try (BancoLocal banco = abrir(Chaves.A)) {
            inserirPreferencia(banco, "tema", "SEGREDO_UNICO_DO_TESTE_98765");
            banco.verificarCifragemAtiva();
        }
        byte[] bytes = Files.readAllBytes(pasta.resolve("dados.db"));
        String texto = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(texto).doesNotContain("SQLite format 3");
        assertThat(texto).doesNotContain("SEGREDO_UNICO_DO_TESTE_98765");
        assertThat(texto).doesNotContain("CREATE TABLE").doesNotContain("perfil_local").doesNotContain("relacionamento");
    }

    @Test
    void chaveCorretaLeOsDadosAposReabrir() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            inserirPreferencia(banco, "idioma", "pt-BR");
        }
        try (BancoLocal banco = abrir(Chaves.A)) {
            String valor = banco.emTransacao(c -> {
                try (PreparedStatement p = c.prepareStatement("SELECT valor FROM preferencia WHERE chave = ?")) {
                    p.setString(1, "idioma");
                    try (ResultSet r = p.executeQuery()) {
                        r.next();
                        return r.getString(1);
                    }
                }
            });
            assertThat(valor).isEqualTo("pt-BR");
        }
    }

    @Test
    void chaveErradaERecusadaSemVazarDetalhes() {
        abrir(Chaves.A).close();
        assertThatThrownBy(() -> abrir(Chaves.B))
                .isInstanceOf(BancoException.class)
                .hasMessageNotContainingAny("hexkey", "jdbc:", "0202020202");
    }

    @Test
    void bancoCifradoNaoAbreSemChave() throws Exception {
        abrir(Chaves.A).close();
        String url = "jdbc:sqlite:file:" + pasta.resolve("dados.db");
        assertThatThrownBy(() -> {
            try (var c = java.sql.DriverManager.getConnection(url); var s = c.createStatement()) {
                s.executeQuery("SELECT * FROM preferencia").close();
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void chaveComTamanhoInvalidoERejeitada() {
        assertThatThrownBy(() -> BancoLocal.abrir(pasta.resolve("x.db"), new byte[16], List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- DB-04: SQL injection ----

    @Test
    void entradaMaliciosaEGravadaComoTextoENaoExecutada() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            String ataque = "x'); DROP TABLE preferencia; --";
            inserirPreferencia(banco, ataque, "' OR '1'='1");
            Integer tabelas = banco.emTransacao(c -> {
                try (var s = c.createStatement();
                     ResultSet r = s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name = 'preferencia'")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertThat(tabelas).isEqualTo(1);
            String lido = banco.emTransacao(c -> {
                try (PreparedStatement p = c.prepareStatement("SELECT valor FROM preferencia WHERE chave = ?")) {
                    p.setString(1, ataque);
                    try (ResultSet r = p.executeQuery()) {
                        r.next();
                        return r.getString(1);
                    }
                }
            });
            assertThat(lido).isEqualTo("' OR '1'='1");
        }
    }

    // ---- DB-06: integridade ----

    @Test
    void chavesEstrangeirasSaoAplicadas() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            assertThatThrownBy(() -> banco.emTransacao(c -> {
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO importacao (id, perfil_id, importada_em, versao_parser, origem) VALUES ('i1', 999, 'x', '1', 'zip')")) {
                    p.executeUpdate();
                }
                return null;
            })).isInstanceOf(BancoException.class);
            assertThat(banco.verificarIntegridade()).isEmpty();
        }
    }

    @Test
    void exclusaoDoPerfilRemoveImportacoesEmCascata() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            banco.emTransacao(c -> {
                try (var s = c.createStatement()) {
                    s.execute("INSERT INTO perfil_local (id, nome, criado_em) VALUES (1, 'Principal', 'x')");
                    s.execute("INSERT INTO importacao (id, perfil_id, importada_em, versao_parser, origem) VALUES ('i1', 1, 'x', '1', 'zip')");
                    s.execute("INSERT INTO relacionamento (importacao_id, tipo, usuario) VALUES ('i1', 'SEGUIDOR', 'fulano')");
                    s.execute("DELETE FROM perfil_local WHERE id = 1");
                }
                return null;
            });
            int restantes = banco.emTransacao(c -> {
                try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM relacionamento")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertThat(restantes).isZero();
        }
    }

    @Test
    void restricaoDeCheckRejeitaTipoInvalido() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            assertThatThrownBy(() -> banco.emTransacao(c -> {
                try (var s = c.createStatement()) {
                    s.execute("INSERT INTO perfil_local (id, nome, criado_em) VALUES (1, 'P', 'x')");
                    s.execute("INSERT INTO importacao (id, perfil_id, importada_em, versao_parser, origem) VALUES ('i1', 1, 'x', '1', 'zip')");
                    s.execute("INSERT INTO relacionamento (importacao_id, tipo, usuario) VALUES ('i1', 'INVENTADO', 'a')");
                }
                return null;
            })).isInstanceOf(BancoException.class);
        }
    }

    // ---- Transações e rollback ----

    @Test
    void falhaNoMeioDaTransacaoDesfazTudo() {
        try (BancoLocal banco = abrir(Chaves.A)) {
            assertThatThrownBy(() -> banco.emTransacao(c -> {
                try (var s = c.createStatement()) {
                    s.execute("INSERT INTO preferencia (chave, valor) VALUES ('a', '1')");
                }
                throw new IllegalStateException("falha simulada");
            })).isInstanceOf(IllegalStateException.class);
            int total = banco.emTransacao(c -> {
                try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM preferencia")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertThat(total).isZero();
        }
    }

    // ---- Acesso concorrente ----

    @Test
    void escritasConcorrentesNaoSePerdemNemCorrompem() throws Exception {
        try (BancoLocal banco = abrir(Chaves.A)) {
            int threads = 8;
            int porThread = 25;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<Void>> futuros = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int id = t;
                Callable<Void> tarefa = () -> {
                    for (int i = 0; i < porThread; i++) {
                        inserirPreferencia(banco, "t" + id + "-" + i, "v");
                    }
                    return null;
                };
                futuros.add(pool.submit(tarefa));
            }
            for (Future<Void> f : futuros) {
                f.get();
            }
            pool.shutdown();
            int total = banco.emTransacao(c -> {
                try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM preferencia")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertThat(total).isEqualTo(threads * porThread);
            assertThat(banco.verificarIntegridade()).isEmpty();
        }
    }

    // ---- DB-08: exclusão ----

    @Test
    void exclusaoRemoveBancoEArquivosAuxiliares() throws Exception {
        try (BancoLocal banco = abrir(Chaves.A)) {
            inserirPreferencia(banco, "a", "b");
        }
        Path db = pasta.resolve("dados.db");
        BancoLocal.excluirArquivos(db);
        assertThat(db).doesNotExist();
        assertThat(Path.of(db + "-wal")).doesNotExist();
        assertThat(Path.of(db + "-shm")).doesNotExist();
        try (BancoLocal novo = abrir(Chaves.A)) {
            assertThat(novo.verificarIntegridade()).isEmpty();
        }
    }

    // ---- DB-07: backup e restauração ----

    @Test
    void backupEstaCifradoERestauraOsDados() throws Exception {
        Path backup = pasta.resolve("copia.bak");
        try (BancoLocal banco = abrir(Chaves.A)) {
            inserirPreferencia(banco, "chave", "VALOR_DO_BACKUP_4242");
            BackupLocal.criar(banco, backup);
        }
        String texto = new String(Files.readAllBytes(backup), StandardCharsets.ISO_8859_1);
        assertThat(texto).doesNotContain("SQLite format 3").doesNotContain("VALOR_DO_BACKUP_4242");

        Path db = pasta.resolve("dados.db");
        BancoLocal.excluirArquivos(db);
        BackupLocal.restaurar(backup, db, Chaves.A);
        try (BancoLocal banco = abrir(Chaves.A)) {
            String valor = banco.emTransacao(c -> {
                try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT valor FROM preferencia WHERE chave = 'chave'")) {
                    r.next();
                    return r.getString(1);
                }
            });
            assertThat(valor).isEqualTo("VALOR_DO_BACKUP_4242");
        }
    }

    @Test
    void restauracaoComChaveErradaNaoAlteraOBancoAtual() throws Exception {
        Path backup = pasta.resolve("copia.bak");
        try (BancoLocal banco = abrir(Chaves.A)) {
            inserirPreferencia(banco, "x", "y");
            BackupLocal.criar(banco, backup);
        }
        Path db = pasta.resolve("dados.db");
        long tamanhoAntes = Files.size(db);
        assertThatThrownBy(() -> BackupLocal.restaurar(backup, db, Chaves.B)).isInstanceOf(BancoException.class);
        assertThat(Files.size(db)).isEqualTo(tamanhoAntes);
        assertThat(Path.of(db + ".antes")).doesNotExist();
    }

    @Test
    void backupNaoSobrescreveArquivoExistente() throws Exception {
        Path backup = pasta.resolve("copia.bak");
        Files.writeString(backup, "existente");
        try (BancoLocal banco = abrir(Chaves.A)) {
            assertThatThrownBy(() -> BackupLocal.criar(banco, backup)).isInstanceOf(BancoException.class);
        }
        assertThat(Files.readString(backup)).isEqualTo("existente");
    }
}
