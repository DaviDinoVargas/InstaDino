package br.com.instadino.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Encerra de fato um processo no meio de uma transação (halt, sem finalizadores) e confere a recuperação. */
class RecuperacaoAposQuedaTest {

    @TempDir
    Path pasta;

    @Test
    void transacaoConfirmadaSobreviveENaoConfirmadaDesaparece() throws Exception {
        Path db = pasta.resolve("dados.db");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                QuedaSimulada.class.getName(), db.toString(), HexFormat.of().formatHex(Chaves.A))
                .redirectErrorStream(true).start();
        String saida = new String(p.getInputStream().readAllBytes());
        int codigo = p.waitFor();
        assertThat(codigo).as(saida).isEqualTo(137);

        try (BancoLocal banco = BancoLocal.abrir(db, Chaves.A, CarregadorDeMigracoes.doClasspath())) {
            assertThat(banco.verificarIntegridade()).isEmpty();
            int confirmadas = contar(banco, "confirmada");
            int pendentes = contar(banco, "pendente");
            assertThat(confirmadas).isEqualTo(1);
            assertThat(pendentes).isZero();
        }
    }

    private static int contar(BancoLocal banco, String chave) {
        return banco.emTransacao(c -> {
            try (var ps = c.prepareStatement("SELECT count(*) FROM preferencia WHERE chave = ?")) {
                ps.setString(1, chave);
                try (ResultSet r = ps.executeQuery()) {
                    r.next();
                    return r.getInt(1);
                }
            }
        });
    }
}
