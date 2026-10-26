package br.com.instadino.persistencia.migracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigradorTest {

    @TempDir
    Path pasta;

    private static final Migracao V1 = Migracao.deTexto(1, "cria_a", "CREATE TABLE a (id INTEGER PRIMARY KEY);");
    private static final Migracao V2 = Migracao.deTexto(2, "cria_b", "CREATE TABLE b (id INTEGER PRIMARY KEY);\nINSERT INTO b (id) VALUES (7);");

    private BancoLocal abrir(List<Migracao> migracoes) {
        return BancoLocal.abrir(pasta.resolve("m.db"), Chaves.A, migracoes);
    }

    private static boolean existeTabela(BancoLocal banco, String nome) {
        return banco.emTransacao(c -> {
            try (var p = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                p.setString(1, nome);
                try (ResultSet r = p.executeQuery()) {
                    return r.next();
                }
            }
        });
    }

    @Test
    void aplicaMigracoesEmOrdemERegistraHistorico() {
        try (BancoLocal banco = abrir(List.of(V1, V2))) {
            assertThat(existeTabela(banco, "a")).isTrue();
            assertThat(existeTabela(banco, "b")).isTrue();
            int registradas = banco.emTransacao(c -> {
                try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM esquema_migracoes")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertThat(registradas).isEqualTo(2);
        }
    }

    @Test
    void reaplicarEIdempotenteEMigraSomenteAsNovas() {
        abrir(List.of(V1)).close();
        try (BancoLocal banco = abrir(List.of(V1, V2))) {
            assertThat(existeTabela(banco, "b")).isTrue();
        }
        try (BancoLocal banco = abrir(List.of(V1, V2))) {
            assertThat(existeTabela(banco, "a")).isTrue();
        }
    }

    @Test
    void migracaoAlteradaDepoisDeAplicadaEDetectada() {
        abrir(List.of(V1)).close();
        Migracao adulterada = Migracao.deTexto(1, "cria_a", "CREATE TABLE a (id INTEGER PRIMARY KEY, extra TEXT);");
        assertThatThrownBy(() -> abrir(List.of(adulterada)))
                .isInstanceOf(MigracaoException.class)
                .hasMessageContaining("alterada");
    }

    @Test
    void bancoMaisNovoQueOAplicativoERecusado() {
        abrir(List.of(V1, V2)).close();
        assertThatThrownBy(() -> abrir(List.of(V1)))
                .isInstanceOf(MigracaoException.class)
                .hasMessageContaining("mais recente");
    }

    @Test
    void falhaNaMigracaoNaoDeixaAlteracaoParcial() {
        Migracao quebrada = Migracao.deTexto(2, "quebrada",
                "CREATE TABLE parcial (id INTEGER PRIMARY KEY);\nINSERT INTO tabela_inexistente VALUES (1);");
        abrir(List.of(V1)).close();
        assertThatThrownBy(() -> abrir(List.of(V1, quebrada))).isInstanceOf(MigracaoException.class);
        try (BancoLocal banco = abrir(List.of(V1))) {
            assertThat(existeTabela(banco, "parcial")).as("tabela criada antes da falha deve ter sido desfeita").isFalse();
            assertThat(existeTabela(banco, "a")).isTrue();
        }
    }

    @Test
    void sequenciaComLacunaEInvalida() {
        Migracao v3 = Migracao.deTexto(3, "pula", "CREATE TABLE c (id INTEGER);");
        assertThatThrownBy(() -> abrir(List.of(V1, v3))).isInstanceOf(MigracaoException.class);
    }

    @Test
    void divisaoIgnoraComentariosEExigeTerminador() {
        assertThat(Migracao.dividir("-- comentario\nCREATE TABLE x (a INT);\n\nINSERT INTO x VALUES (1);\n")).hasSize(2);
        assertThatThrownBy(() -> Migracao.dividir("CREATE TABLE x (a INT)")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void esquemaInicialDoProjetoCarregaEAplica() {
        try (BancoLocal banco = abrir(CarregadorDeMigracoes.doClasspath())) {
            for (String tabela : List.of("perfil_local", "importacao", "importacao_categoria", "relacionamento",
                    "preferencia", "consentimento", "evento_seguranca")) {
                assertThat(existeTabela(banco, tabela)).as(tabela).isTrue();
            }
            assertThat(banco.verificarIntegridade()).isEmpty();
        }
    }

    @Test
    void checksumIgnoraDiferencaDeFimDeLinha() {
        var lf = Migracao.deTexto(1, "x", "CREATE TABLE a (id INT);\n");
        var crlf = Migracao.deTexto(1, "x", "CREATE TABLE a (id INT);\r\n");
        assertThat(lf.checksum()).isEqualTo(crlf.checksum());
    }
}
