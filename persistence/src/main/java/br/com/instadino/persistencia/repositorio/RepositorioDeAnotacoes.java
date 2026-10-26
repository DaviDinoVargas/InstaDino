package br.com.instadino.persistencia.repositorio;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Favoritos, etiquetas e notas privadas sobre contas. Tudo pertence a um perfil local e é identificado pelo nome de
 * usuário normalizado, então sobrevive a novas importações. Etiquetas só podem ser aplicadas a contas do perfil
 * dono da etiqueta. Esta classe não aplica política de plano: isso é responsabilidade da camada de serviços.
 */
public final class RepositorioDeAnotacoes {

    public static final int TAMANHO_MAXIMO_DA_NOTA = 2000;
    public static final int TAMANHO_MAXIMO_DA_ETIQUETA = 40;
    public static final int LIMITE_DE_ETIQUETAS_POR_PERFIL = 100;

    public record Etiqueta(long id, String nome) {
    }

    public record Anotacao(String usuario, boolean favorito, String nota, List<Etiqueta> etiquetas) {
        public boolean vazia() {
            return !favorito && (nota == null || nota.isEmpty()) && etiquetas.isEmpty();
        }
    }

    private final BancoLocal banco;

    public RepositorioDeAnotacoes(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    // ---- leitura ----

    /** Todas as contas do perfil que têm favorito, nota ou etiqueta, por nome de usuário. */
    public Map<String, Anotacao> doPerfil(long perfilId) {
        return banco.emTransacao(c -> {
            Map<String, Boolean> favoritos = new HashMap<>();
            Map<String, String> notas = new HashMap<>();
            try (PreparedStatement p = c.prepareStatement("SELECT usuario, favorito, nota FROM anotacao_conta WHERE perfil_id = ?")) {
                p.setLong(1, perfilId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        favoritos.put(r.getString(1), r.getInt(2) == 1);
                        notas.put(r.getString(1), r.getString(3));
                    }
                }
            }
            Map<String, List<Etiqueta>> etiquetas = new HashMap<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT ce.usuario, e.id, e.nome FROM conta_etiqueta ce JOIN etiqueta e ON e.id = ce.etiqueta_id "
                            + "WHERE ce.perfil_id = ? ORDER BY e.nome COLLATE NOCASE")) {
                p.setLong(1, perfilId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        etiquetas.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(new Etiqueta(r.getLong(2), r.getString(3)));
                    }
                }
            }
            Map<String, Anotacao> resultado = new LinkedHashMap<>();
            java.util.TreeSet<String> usuarios = new java.util.TreeSet<>(favoritos.keySet());
            usuarios.addAll(etiquetas.keySet());
            for (String u : usuarios) {
                Anotacao a = new Anotacao(u, favoritos.getOrDefault(u, false), notas.get(u), List.copyOf(etiquetas.getOrDefault(u, List.of())));
                if (!a.vazia()) {
                    resultado.put(u, a);
                }
            }
            return resultado;
        });
    }

    public Optional<Anotacao> obter(long perfilId, String usuario) {
        String u = nome(usuario);
        return Optional.ofNullable(doPerfil(perfilId).get(u));
    }

    public List<Etiqueta> etiquetas(long perfilId) {
        return banco.emTransacao(c -> {
            List<Etiqueta> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT id, nome FROM etiqueta WHERE perfil_id = ? ORDER BY nome COLLATE NOCASE")) {
                p.setLong(1, perfilId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        lista.add(new Etiqueta(r.getLong(1), r.getString(2)));
                    }
                }
            }
            return lista;
        });
    }

    // ---- favoritos e notas ----

    public void definirFavorito(long perfilId, String usuario, boolean favorito) {
        String u = nome(usuario);
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO anotacao_conta (perfil_id, usuario, favorito, nota, atualizada_em) VALUES (?, ?, ?, NULL, ?) "
                            + "ON CONFLICT (perfil_id, usuario) DO UPDATE SET favorito = excluded.favorito, atualizada_em = excluded.atualizada_em")) {
                p.setLong(1, perfilId);
                p.setString(2, u);
                p.setInt(3, favorito ? 1 : 0);
                p.setString(4, Instant.now().toString());
                p.executeUpdate();
            }
            limparSeVazia(c, perfilId, u);
            return null;
        });
    }

    /** Define a nota privada. Texto em branco remove a nota. */
    public void definirNota(long perfilId, String usuario, String nota) {
        String u = nome(usuario);
        String limpa = nota == null ? "" : nota.strip();
        if (limpa.length() > TAMANHO_MAXIMO_DA_NOTA) {
            throw new IllegalArgumentException("A nota deve ter no máximo " + TAMANHO_MAXIMO_DA_NOTA + " caracteres.");
        }
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO anotacao_conta (perfil_id, usuario, favorito, nota, atualizada_em) VALUES (?, ?, 0, ?, ?) "
                            + "ON CONFLICT (perfil_id, usuario) DO UPDATE SET nota = excluded.nota, atualizada_em = excluded.atualizada_em")) {
                p.setLong(1, perfilId);
                p.setString(2, u);
                p.setString(3, limpa.isEmpty() ? null : limpa);
                p.setString(4, Instant.now().toString());
                p.executeUpdate();
            }
            limparSeVazia(c, perfilId, u);
            return null;
        });
    }

    // ---- etiquetas ----

    public Etiqueta criarEtiqueta(long perfilId, String nome) {
        String limpo = nomeDeEtiqueta(nome);
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT count(*) FROM etiqueta WHERE perfil_id = ?")) {
                p.setLong(1, perfilId);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    if (r.getInt(1) >= LIMITE_DE_ETIQUETAS_POR_PERFIL) {
                        throw new IllegalArgumentException("Limite de " + LIMITE_DE_ETIQUETAS_POR_PERFIL + " etiquetas por perfil atingido.");
                    }
                }
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO etiqueta (perfil_id, nome) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, perfilId);
                p.setString(2, limpo);
                try {
                    p.executeUpdate();
                } catch (java.sql.SQLException e) {
                    throw new IllegalArgumentException("Já existe uma etiqueta com esse nome.");
                }
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    return new Etiqueta(k.getLong(1), limpo);
                }
            }
        });
    }

    public void renomearEtiqueta(long perfilId, long etiquetaId, String novoNome) {
        String limpo = nomeDeEtiqueta(novoNome);
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("UPDATE etiqueta SET nome = ? WHERE id = ? AND perfil_id = ?")) {
                p.setString(1, limpo);
                p.setLong(2, etiquetaId);
                p.setLong(3, perfilId);
                try {
                    if (p.executeUpdate() == 0) {
                        throw new IllegalArgumentException("Etiqueta não encontrada.");
                    }
                } catch (java.sql.SQLException e) {
                    throw new IllegalArgumentException("Já existe uma etiqueta com esse nome.");
                }
            }
            return null;
        });
    }

    public void excluirEtiqueta(long perfilId, long etiquetaId) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM etiqueta WHERE id = ? AND perfil_id = ?")) {
                p.setLong(1, etiquetaId);
                p.setLong(2, perfilId);
                p.executeUpdate();
            }
            return null;
        });
    }

    /** Aplica a etiqueta à conta. A etiqueta precisa pertencer ao mesmo perfil; caso contrário nada é gravado. */
    public void aplicarEtiqueta(long perfilId, String usuario, long etiquetaId) {
        String u = nome(usuario);
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT OR IGNORE INTO conta_etiqueta (perfil_id, usuario, etiqueta_id) "
                            + "SELECT ?, ?, id FROM etiqueta WHERE id = ? AND perfil_id = ?")) {
                p.setLong(1, perfilId);
                p.setString(2, u);
                p.setLong(3, etiquetaId);
                p.setLong(4, perfilId);
                if (p.executeUpdate() == 0 && !existeAplicacao(c, perfilId, u, etiquetaId)) {
                    throw new IllegalArgumentException("Etiqueta não encontrada neste perfil.");
                }
            }
            return null;
        });
    }

    public void removerEtiqueta(long perfilId, String usuario, long etiquetaId) {
        String u = nome(usuario);
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM conta_etiqueta WHERE perfil_id = ? AND usuario = ? AND etiqueta_id = ?")) {
                p.setLong(1, perfilId);
                p.setString(2, u);
                p.setLong(3, etiquetaId);
                p.executeUpdate();
            }
            return null;
        });
    }

    // ---- internos ----

    private static boolean existeAplicacao(java.sql.Connection c, long perfilId, String usuario, long etiquetaId) throws java.sql.SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM conta_etiqueta WHERE perfil_id = ? AND usuario = ? AND etiqueta_id = ?")) {
            p.setLong(1, perfilId);
            p.setString(2, usuario);
            p.setLong(3, etiquetaId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        }
    }

    private static void limparSeVazia(java.sql.Connection c, long perfilId, String usuario) throws java.sql.SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM anotacao_conta WHERE perfil_id = ? AND usuario = ? AND favorito = 0 AND nota IS NULL")) {
            p.setLong(1, perfilId);
            p.setString(2, usuario);
            p.executeUpdate();
        }
    }

    private static String nome(String usuario) {
        return NomeDeUsuario.tentar(usuario).orElseThrow(() -> new IllegalArgumentException("Nome de usuário inválido.")).valor();
    }

    private static String nomeDeEtiqueta(String nome) {
        String limpo = nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
        if (limpo.isEmpty() || limpo.length() > TAMANHO_MAXIMO_DA_ETIQUETA) {
            throw new IllegalArgumentException("O nome da etiqueta deve ter entre 1 e " + TAMANHO_MAXIMO_DA_ETIQUETA + " caracteres.");
        }
        if (limpo.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("O nome da etiqueta contém caracteres inválidos.");
        }
        return limpo;
    }

    /** Ordenação estável para exibição. */
    public static Comparator<Etiqueta> porNome() {
        return Comparator.comparing(Etiqueta::nome, String.CASE_INSENSITIVE_ORDER);
    }
}
