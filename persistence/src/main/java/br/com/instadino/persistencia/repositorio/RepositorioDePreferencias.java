package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Optional;

/** Preferências locais (tema, idioma etc.). Todas as consultas são parametrizadas. */
public final class RepositorioDePreferencias {

    private final BancoLocal banco;

    public RepositorioDePreferencias(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    public void definir(String chave, String valor) {
        Objects.requireNonNull(chave, "chave");
        Objects.requireNonNull(valor, "valor");
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO preferencia (chave, valor) VALUES (?, ?) "
                            + "ON CONFLICT (chave) DO UPDATE SET valor = excluded.valor")) {
                p.setString(1, chave);
                p.setString(2, valor);
                p.executeUpdate();
            }
            return null;
        });
    }

    public Optional<String> obter(String chave) {
        Objects.requireNonNull(chave, "chave");
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT valor FROM preferencia WHERE chave = ?")) {
                p.setString(1, chave);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? Optional.of(r.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }
}
