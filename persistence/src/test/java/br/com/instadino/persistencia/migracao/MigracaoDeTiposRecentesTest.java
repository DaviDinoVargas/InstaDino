package br.com.instadino.persistencia.migracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** V003 recria a tabela relacionamento para aceitar novos tipos; os dados anteriores precisam sobreviver. */
class MigracaoDeTiposRecentesTest {

    @TempDir
    Path pasta;

    private void inserir(BancoLocal banco, String tipo, String usuario) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO relacionamento (importacao_id, tipo, usuario, data_registro) VALUES ('imp1', ?, ?, 123)")) {
                p.setString(1, tipo);
                p.setString(2, usuario);
                p.executeUpdate();
            }
            return null;
        });
    }

    private int total(BancoLocal banco) {
        return banco.emTransacao(c -> {
            try (var s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM relacionamento")) {
                r.next();
                return r.getInt(1);
            }
        });
    }

    @Test
    void dadosDaV002SobrevivemEOsTiposNovosPassamAValer() {
        List<Migracao> todas = CarregadorDeMigracoes.doClasspath();
        assertThat(todas).extracting(Migracao::versao).contains(1, 2, 3);
        Path arquivo = pasta.resolve("m.db");
        try (BancoLocal antigo = BancoLocal.abrir(arquivo, Chaves.A, todas.subList(0, 2))) {
            antigo.emTransacao(c -> {
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO importacao (id, perfil_id, importada_em, versao_parser, origem) VALUES ('imp1', 1, '2026-01-01T00:00:00Z', '1.0.0', 'x')")) {
                    c.createStatement().execute("INSERT INTO perfil_local (nome, criado_em) VALUES ('P', '2026-01-01T00:00:00Z')");
                    p.executeUpdate();
                }
                return null;
            });
            inserir(antigo, "SEGUIDOR", "ana");
            inserir(antigo, "SEGUINDO", "bruno");
            assertThatThrownBy(() -> inserir(antigo, "DEIXEI_DE_SEGUIR_RECENTE", "x")).isInstanceOf(RuntimeException.class);
        }
        try (BancoLocal novo = BancoLocal.abrir(arquivo, Chaves.A, todas)) {
            assertThat(total(novo)).isEqualTo(2);
            inserir(novo, "DEIXEI_DE_SEGUIR_RECENTE", "carla");
            inserir(novo, "SOLICITACAO_RECENTE", "diego");
            inserir(novo, "SOLICITACAO_RECEBIDA", "eva");
            assertThat(total(novo)).isEqualTo(5);
            assertThatThrownBy(() -> inserir(novo, "TIPO_INVENTADO", "x")).isInstanceOf(RuntimeException.class);
            // a chave estrangeira com a importação continua valendo, assim como a exclusão em cascata
            novo.emTransacao(c -> {
                c.createStatement().execute("DELETE FROM importacao WHERE id = 'imp1'");
                return null;
            });
            assertThat(total(novo)).isZero();
        }
    }
}
