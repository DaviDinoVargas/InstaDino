package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade.Pagina;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Catálogo de publicações, reels e stories de uma importação. O SQL é sempre fixo (sem concatenação). */
public final class RepositorioDeConteudo {

    public record Midia(String caminho, String tipo, Long tamanho) {
    }

    public record Item(long id, String categoria, String legenda, Long instante, List<Midia> midias) {
    }

    /** Filtro; campos nulos não filtram. {@code tipoDeMidia} considera itens que tenham ao menos uma mídia daquele tipo. */
    public record Filtro(String categoria, String texto, String tipoDeMidia, Long desde, Long ate) {
    }

    public record TotalDeMidia(String tipo, int quantidade, long bytes) {
    }

    private static final String CONTAR = "SELECT count(*) FROM publicacao pb WHERE pb.importacao_id = ? AND (? IS NULL OR pb.categoria = ?) "
            + "AND (? IS NULL OR pb.legenda LIKE ? ESCAPE '\\') "
            + "AND (? IS NULL OR EXISTS (SELECT 1 FROM midia m WHERE m.publicacao_id = pb.id AND m.tipo = ?)) "
            + "AND (? IS NULL OR pb.instante >= ?) AND (? IS NULL OR pb.instante <= ?)";
    private static final String LISTAR = "SELECT pb.id, pb.categoria, pb.legenda, pb.instante FROM publicacao pb "
            + "WHERE pb.importacao_id = ? AND (? IS NULL OR pb.categoria = ?) "
            + "AND (? IS NULL OR pb.legenda LIKE ? ESCAPE '\\') "
            + "AND (? IS NULL OR EXISTS (SELECT 1 FROM midia m WHERE m.publicacao_id = pb.id AND m.tipo = ?)) "
            + "AND (? IS NULL OR pb.instante >= ?) AND (? IS NULL OR pb.instante <= ?) "
            + "ORDER BY pb.instante DESC, pb.id DESC LIMIT ? OFFSET ?";
    private static final String MIDIAS_DA_PAGINA = "SELECT m.publicacao_id, m.caminho, m.tipo, m.tamanho FROM midia m WHERE m.publicacao_id IN ("
            + "SELECT pb.id FROM publicacao pb WHERE pb.importacao_id = ? AND (? IS NULL OR pb.categoria = ?) "
            + "AND (? IS NULL OR pb.legenda LIKE ? ESCAPE '\\') "
            + "AND (? IS NULL OR EXISTS (SELECT 1 FROM midia m2 WHERE m2.publicacao_id = pb.id AND m2.tipo = ?)) "
            + "AND (? IS NULL OR pb.instante >= ?) AND (? IS NULL OR pb.instante <= ?) "
            + "ORDER BY pb.instante DESC, pb.id DESC LIMIT ? OFFSET ?) ORDER BY m.publicacao_id, m.ordem";

    private final BancoLocal banco;

    public RepositorioDeConteudo(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    public Map<String, Integer> contagemPorCategoria(String importacaoId) {
        return banco.emTransacao(c -> {
            Map<String, Integer> mapa = new LinkedHashMap<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT categoria, count(*) FROM publicacao WHERE importacao_id = ? GROUP BY categoria ORDER BY categoria")) {
                p.setString(1, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        mapa.put(r.getString(1), r.getInt(2));
                    }
                }
            }
            return mapa;
        });
    }

    public long[] instantes(String importacaoId, String categoria) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT instante FROM publicacao WHERE importacao_id = ? AND categoria = ? AND instante IS NOT NULL ORDER BY instante")) {
                p.setString(1, importacaoId);
                p.setString(2, categoria);
                long[] v = new long[256];
                int n = 0;
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        if (n == v.length) {
                            v = Arrays.copyOf(v, n * 2);
                        }
                        v[n++] = r.getLong(1);
                    }
                }
                return Arrays.copyOf(v, n);
            }
        });
    }

    /** Quantidade e tamanho total das mídias associadas, por tipo (tamanhos desconhecidos contam como zero). */
    public List<TotalDeMidia> totaisDeMidia(String importacaoId) {
        return banco.emTransacao(c -> {
            List<TotalDeMidia> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT m.tipo, count(*), coalesce(sum(m.tamanho), 0) FROM midia m JOIN publicacao pb ON pb.id = m.publicacao_id "
                            + "WHERE pb.importacao_id = ? GROUP BY m.tipo ORDER BY m.tipo")) {
                p.setString(1, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        lista.add(new TotalDeMidia(r.getString(1), r.getInt(2), r.getLong(3)));
                    }
                }
            }
            return lista;
        });
    }

    public Pagina<Item> buscar(String importacaoId, Filtro filtro, int pagina, int tamanho) {
        int tam = Math.max(1, Math.min(tamanho, 200));
        int de = Math.max(pagina, 0) * tam;
        String padrao = filtro.texto() == null || filtro.texto().isBlank() ? null : "%" + RepositorioDeAtividade.escapar(filtro.texto().strip()) + "%";
        return banco.emTransacao(c -> {
            int total;
            try (PreparedStatement p = c.prepareStatement(CONTAR)) {
                ligar(p, importacaoId, filtro, padrao);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    total = r.getInt(1);
                }
            }
            Map<Long, Item> itens = new LinkedHashMap<>();
            try (PreparedStatement p = c.prepareStatement(LISTAR)) {
                int proximo = ligar(p, importacaoId, filtro, padrao);
                p.setInt(proximo++, tam);
                p.setInt(proximo, de);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long instante = r.getLong(4);
                        boolean semInstante = r.wasNull(); // wasNull() vale só para a ÚLTIMA coluna lida: capturar já
                        itens.put(r.getLong(1), new Item(r.getLong(1), r.getString(2), r.getString(3), semInstante ? null : instante, new ArrayList<>()));
                    }
                }
            }
            if (!itens.isEmpty()) {
                try (PreparedStatement p = c.prepareStatement(MIDIAS_DA_PAGINA)) {
                    int proximo = ligar(p, importacaoId, filtro, padrao);
                    p.setInt(proximo++, tam);
                    p.setInt(proximo, de);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) {
                            long tamanhoDaMidia = r.getLong(4);
                            boolean semTamanho = r.wasNull();
                            Item item = itens.get(r.getLong(1));
                            if (item != null) {
                                item.midias().add(new Midia(r.getString(2), r.getString(3), semTamanho ? null : tamanhoDaMidia));
                            }
                        }
                    }
                }
            }
            return new Pagina<>(List.copyOf(itens.values()), total);
        });
    }

    /** Liga os 10 parâmetros do filtro e devolve o índice do próximo parâmetro. */
    private static int ligar(PreparedStatement p, String importacaoId, Filtro f, String padrao) throws SQLException {
        int i = 1;
        p.setString(i++, importacaoId);
        i = RepositorioDeAtividade.texto(p, i, f.categoria());
        i = RepositorioDeAtividade.texto(p, i, f.categoria());
        i = RepositorioDeAtividade.texto(p, i, padrao);
        i = RepositorioDeAtividade.texto(p, i, padrao);
        i = RepositorioDeAtividade.texto(p, i, f.tipoDeMidia());
        i = RepositorioDeAtividade.texto(p, i, f.tipoDeMidia());
        i = RepositorioDeAtividade.numero(p, i, f.desde());
        i = RepositorioDeAtividade.numero(p, i, f.desde());
        i = RepositorioDeAtividade.numero(p, i, f.ate());
        return RepositorioDeAtividade.numero(p, i, f.ate());
    }
}
