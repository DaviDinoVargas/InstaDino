package br.com.instadino.persistencia.migracao;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica migrações versionadas. Garantias:
 * <ul>
 *   <li>cada migração roda em uma transação: falhou, nada dela permanece;</li>
 *   <li>o checksum de migrações já aplicadas é conferido (migração alterada depois de publicada é rejeitada);</li>
 *   <li>um banco mais novo que o aplicativo é recusado, para não corromper dados de versões futuras.</li>
 * </ul>
 */
public final class Migrador {

    private static final System.Logger LOG = System.getLogger(Migrador.class.getName());

    private final BancoLocal banco;

    public Migrador(BancoLocal banco) {
        this.banco = banco;
    }

    public void aplicar(List<Migracao> migracoes) {
        validarSequencia(migracoes);
        banco.emTransacao(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS esquema_migracoes ("
                        + "versao INTEGER PRIMARY KEY, descricao TEXT NOT NULL, "
                        + "checksum TEXT NOT NULL, aplicada_em TEXT NOT NULL)");
            }
            return null;
        });
        Map<Integer, String> aplicadas = lerAplicadas();
        int maiorConhecida = migracoes.isEmpty() ? 0 : migracoes.get(migracoes.size() - 1).versao();
        for (Map.Entry<Integer, String> e : aplicadas.entrySet()) {
            if (e.getKey() > maiorConhecida) {
                throw new MigracaoException("O banco local foi criado por uma versão mais recente do aplicativo.");
            }
        }
        for (Migracao m : migracoes) {
            String checksumAplicado = aplicadas.get(m.versao());
            if (checksumAplicado != null) {
                if (!checksumAplicado.equals(m.checksum())) {
                    throw new MigracaoException("A migração V" + m.versao() + " foi alterada após ser aplicada.");
                }
                continue;
            }
            aplicarUma(m);
        }
    }

    private void aplicarUma(Migracao m) {
        try {
            banco.emTransacao(c -> {
                try (Statement s = c.createStatement()) {
                    for (String comando : m.comandos()) {
                        s.execute(comando);
                    }
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO esquema_migracoes (versao, descricao, checksum, aplicada_em) VALUES (?, ?, ?, ?)")) {
                    p.setInt(1, m.versao());
                    p.setString(2, m.descricao());
                    p.setString(3, m.checksum());
                    p.setString(4, Instant.now().toString());
                    p.executeUpdate();
                }
                return null;
            });
            LOG.log(System.Logger.Level.INFO, "Migração V{0} aplicada.", m.versao());
        } catch (br.com.instadino.persistencia.BancoException e) {
            throw new MigracaoException("Falha ao aplicar a migração V" + m.versao() + "; nenhuma alteração foi mantida.", e.codigoSqlite());
        }
    }

    private Map<Integer, String> lerAplicadas() {
        Map<Integer, String> aplicadas = new HashMap<>();
        try (var c = banco.conexao(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT versao, checksum FROM esquema_migracoes")) {
            while (r.next()) {
                aplicadas.put(r.getInt(1), r.getString(2));
            }
        } catch (SQLException e) {
            throw new MigracaoException("Falha ao ler o histórico de migrações.", e.getErrorCode());
        }
        return aplicadas;
    }

    private static void validarSequencia(List<Migracao> migracoes) {
        int anterior = 0;
        for (Migracao m : migracoes) {
            if (m.versao() != anterior + 1) {
                throw new MigracaoException("Sequência de migrações inválida: esperada V" + (anterior + 1) + ".");
            }
            anterior = m.versao();
        }
    }
}
