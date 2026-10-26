package br.com.instadino.persistencia.repositorio;

import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.RegistroDeConsentimento;
import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Objects;

/** Consentimentos explícitos para módulos sensíveis. Sem registro, não há consentimento. */
public final class RepositorioDeConsentimentos implements RegistroDeConsentimento {

    private final BancoLocal banco;

    public RepositorioDeConsentimentos(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    public void conceder(Funcionalidade funcionalidade) {
        if (!funcionalidade.sensivel()) {
            throw new IllegalArgumentException("A funcionalidade não exige consentimento.");
        }
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT OR REPLACE INTO consentimento (funcionalidade, concedido_em) VALUES (?, ?)")) {
                p.setString(1, funcionalidade.id());
                p.setString(2, Instant.now().toString());
                p.executeUpdate();
            }
            return null;
        });
    }

    public void revogar(Funcionalidade funcionalidade) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM consentimento WHERE funcionalidade = ?")) {
                p.setString(1, funcionalidade.id());
                p.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean concedido(Funcionalidade funcionalidade) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM consentimento WHERE funcionalidade = ?")) {
                p.setString(1, funcionalidade.id());
                try (ResultSet r = p.executeQuery()) {
                    return r.next();
                }
            }
        });
    }
}
