package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Perfis locais. Cada perfil agrupa as importações de uma conta do Instagram analisada. */
public final class RepositorioDePerfis {

    public record Perfil(long id, String nome, String usuarioInstagram, Instant criadoEm) {
    }

    private final BancoLocal banco;

    public RepositorioDePerfis(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    public Perfil criar(String nome, String usuarioInstagram) {
        String nomeLimpo = Objects.requireNonNull(nome, "nome").strip();
        if (nomeLimpo.isEmpty() || nomeLimpo.length() > 100) {
            throw new IllegalArgumentException("O nome do perfil deve ter entre 1 e 100 caracteres.");
        }
        Instant agora = Instant.now();
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO perfil_local (nome, usuario_instagram, criado_em) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, nomeLimpo);
                p.setString(2, usuarioInstagram);
                p.setString(3, agora.toString());
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return new Perfil(k.getLong(1), nomeLimpo, usuarioInstagram, agora);
                }
            }
        });
    }

    public List<Perfil> listar() {
        return banco.emTransacao(c -> {
            List<Perfil> perfis = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT id, nome, usuario_instagram, criado_em FROM perfil_local ORDER BY id")) {
                while (r.next()) {
                    perfis.add(new Perfil(r.getLong(1), r.getString(2), r.getString(3), Instant.parse(r.getString(4))));
                }
            }
            return perfis;
        });
    }

    public Optional<Perfil> obter(long id) {
        return listar().stream().filter(p -> p.id() == id).findFirst();
    }

    /** Devolve o primeiro perfil, criando o perfil padrão na primeira execução. */
    public Perfil obterOuCriarPadrao() {
        return listar().stream().findFirst().orElseGet(() -> criar("Perfil principal", null));
    }

    public void excluir(long id) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM perfil_local WHERE id = ?")) {
                p.setLong(1, id);
                p.executeUpdate();
            }
            return null;
        });
    }
}
