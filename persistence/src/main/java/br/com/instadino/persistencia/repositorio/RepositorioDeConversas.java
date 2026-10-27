package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consultas sobre os agregados de conversas de uma importação (módulo de mensagens). Não existe texto de mensagem
 * no banco, então nada aqui o devolve. Todas as consultas usam SQL fixo (DB-04).
 */
public final class RepositorioDeConversas {

    public record Conversa(long id, String titulo, int participantes, int total, int enviadas, int midias, Long primeiro, Long ultimo) {
        public boolean grupo() {
            return participantes > 2;
        }

        public boolean titularIdentificado() {
            return enviadas >= 0;
        }
    }

    public record Mes(String mes, int total, int enviadas) {
    }

    public static final int TAMANHO_MAXIMO = 1000;

    private final BancoLocal banco;

    public RepositorioDeConversas(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    /** Conversas da importação, da mais movimentada para a menos. */
    public List<Conversa> listar(String importacaoId, int limite) {
        return banco.emTransacao(c -> {
            List<Conversa> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id, titulo, participantes, total, enviadas, midias, primeiro, ultimo FROM conversa "
                            + "WHERE importacao_id = ? ORDER BY total DESC, id LIMIT ?")) {
                p.setString(1, importacaoId);
                p.setInt(2, Math.max(1, Math.min(limite, TAMANHO_MAXIMO)));
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long primeiro = r.getLong(7);
                        boolean semPrimeiro = r.wasNull();
                        long ultimo = r.getLong(8);
                        boolean semUltimo = r.wasNull();
                        lista.add(new Conversa(r.getLong(1), r.getString(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6),
                                semPrimeiro ? null : primeiro, semUltimo ? null : ultimo));
                    }
                }
            }
            return lista;
        });
    }

    public int contar(String importacaoId) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT count(*) FROM conversa WHERE importacao_id = ?")) {
                p.setString(1, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    return r.getInt(1);
                }
            }
        });
    }

    /** Mensagens por mês somando todas as conversas da importação, em ordem cronológica. */
    public List<Mes> porMes(String importacaoId) {
        return banco.emTransacao(c -> {
            List<Mes> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT m.mes, sum(m.total), sum(CASE WHEN m.enviadas >= 0 THEN m.enviadas ELSE 0 END), min(m.enviadas) "
                            + "FROM conversa_mes m JOIN conversa v ON v.id = m.conversa_id WHERE v.importacao_id = ? "
                            + "GROUP BY m.mes ORDER BY m.mes")) {
                p.setString(1, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        int minimo = r.getInt(4);
                        lista.add(new Mes(r.getString(1), r.getInt(2), minimo < 0 ? -1 : r.getInt(3)));
                    }
                }
            }
            return lista;
        });
    }

    /** Mensagens por mês de uma conversa. */
    public List<Mes> porMes(long conversaId) {
        return banco.emTransacao(c -> {
            List<Mes> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT mes, total, enviadas FROM conversa_mes WHERE conversa_id = ? ORDER BY mes")) {
                p.setLong(1, conversaId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        lista.add(new Mes(r.getString(1), r.getInt(2), r.getInt(3)));
                    }
                }
            }
            return lista;
        });
    }

    /** Apaga os agregados de mensagens de todas as importações (revogação do consentimento). */
    public int excluirTodas() {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM conversa")) {
                return p.executeUpdate();
            }
        });
    }
}
