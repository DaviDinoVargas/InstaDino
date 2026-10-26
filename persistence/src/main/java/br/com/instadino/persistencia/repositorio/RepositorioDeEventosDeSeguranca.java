package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Registro local de eventos de segurança relevantes (importação, exclusão, consentimento). Sem dados pessoais. */
public final class RepositorioDeEventosDeSeguranca {

    public record Evento(Instant ocorridoEm, String tipo, String detalhe) {
    }

    private static final Pattern TIPO_VALIDO = Pattern.compile("[A-Z][A-Z_]{2,39}");
    private static final int LIMITE_DETALHE = 300;

    private final BancoLocal banco;

    public RepositorioDeEventosDeSeguranca(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    public void registrar(String tipo, String detalhe) {
        if (tipo == null || !TIPO_VALIDO.matcher(tipo).matches()) {
            throw new IllegalArgumentException("Tipo de evento inválido.");
        }
        String limitado = detalhe == null ? null
                : detalhe.length() > LIMITE_DETALHE ? detalhe.substring(0, LIMITE_DETALHE) : detalhe;
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO evento_seguranca (ocorrido_em, tipo, detalhe) VALUES (?, ?, ?)")) {
                p.setString(1, Instant.now().toString());
                p.setString(2, tipo);
                p.setString(3, limitado);
                p.executeUpdate();
            }
            return null;
        });
    }

    public List<Evento> recentes(int limite) {
        int n = Math.max(1, Math.min(limite, 500));
        return banco.emTransacao(c -> {
            List<Evento> eventos = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT ocorrido_em, tipo, detalhe FROM evento_seguranca ORDER BY id DESC LIMIT ?")) {
                p.setInt(1, n);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        eventos.add(new Evento(Instant.parse(r.getString(1)), r.getString(2), r.getString(3)));
                    }
                }
            }
            return eventos;
        });
    }
}
