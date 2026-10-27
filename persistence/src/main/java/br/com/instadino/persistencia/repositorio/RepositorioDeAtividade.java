package br.com.instadino.persistencia.repositorio;

import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Consultas sobre a atividade de uma importação. O SQL é sempre fixo: filtros opcionais usam o padrão
 * {@code (? IS NULL OR coluna ...)} e nada é montado por concatenação (DB-04).
 */
public final class RepositorioDeAtividade {

    public record Linha(String categoria, String subtipo, String conta, String texto, String referencia, Long instante) {
    }

    public record Pagina<T>(List<T> itens, int total) {
    }

    public record ContaContada(String conta, int quantidade) {
    }

    /** Filtro de busca; campos nulos não filtram. {@code texto} procura no texto e no nome da conta. */
    public record Filtro(String categoria, String texto, Long desde, Long ate) {
    }

    public static final int TAMANHO_MAXIMO_DE_PAGINA = 500;

    private static final String CONTAR = "SELECT count(*) FROM atividade WHERE importacao_id = ? AND (? IS NULL OR categoria = ?) "
            + "AND (? IS NULL OR texto LIKE ? ESCAPE '\\' OR conta LIKE ? ESCAPE '\\') "
            + "AND (? IS NULL OR instante >= ?) AND (? IS NULL OR instante <= ?)";
    private static final String LISTAR = "SELECT categoria, subtipo, conta, texto, referencia, instante FROM atividade "
            + "WHERE importacao_id = ? AND (? IS NULL OR categoria = ?) "
            + "AND (? IS NULL OR texto LIKE ? ESCAPE '\\' OR conta LIKE ? ESCAPE '\\') "
            + "AND (? IS NULL OR instante >= ?) AND (? IS NULL OR instante <= ?) "
            + "ORDER BY instante DESC, id DESC LIMIT ? OFFSET ?";

    private final BancoLocal banco;

    public RepositorioDeAtividade(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    /** Quantidade de registros por categoria. */
    public Map<String, Integer> contagemPorCategoria(String importacaoId) {
        return banco.emTransacao(c -> {
            Map<String, Integer> mapa = new LinkedHashMap<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT categoria, count(*) FROM atividade WHERE importacao_id = ? GROUP BY categoria ORDER BY categoria")) {
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

    /** Menor e maior instante (segundos) da categoria, ou vetor vazio se não houver datas. */
    public long[] periodo(String importacaoId, String categoria) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT min(instante), max(instante) FROM atividade WHERE importacao_id = ? AND categoria = ? AND instante IS NOT NULL")) {
                p.setString(1, importacaoId);
                p.setString(2, categoria);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    long min = r.getLong(1);
                    if (r.wasNull()) {
                        return new long[0];
                    }
                    return new long[] {min, r.getLong(2)};
                }
            }
        });
    }

    /** Todos os instantes (segundos) com data da categoria, em ordem crescente. Usado para calendário e gráficos. */
    public long[] instantes(String importacaoId, String categoria) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT instante FROM atividade WHERE importacao_id = ? AND categoria = ? AND instante IS NOT NULL ORDER BY instante")) {
                p.setString(1, importacaoId);
                p.setString(2, categoria);
                long[] v = new long[1024];
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

    /** Contas com mais registros na categoria (ignora registros sem conta). */
    public List<ContaContada> ranking(String importacaoId, String categoria, int limite) {
        return banco.emTransacao(c -> {
            List<ContaContada> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT conta, count(*) AS n FROM atividade WHERE importacao_id = ? AND categoria = ? AND conta IS NOT NULL "
                            + "GROUP BY conta ORDER BY n DESC, conta LIMIT ?")) {
                p.setString(1, importacaoId);
                p.setString(2, categoria);
                p.setInt(3, Math.max(1, Math.min(limite, 1000)));
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        lista.add(new ContaContada(r.getString(1), r.getInt(2)));
                    }
                }
            }
            return lista;
        });
    }

    /** Total de registros por conta somando as categorias informadas (para cruzar com as listas de relacionamentos). */
    public Map<String, Integer> totaisPorConta(String importacaoId, List<String> categorias) {
        return banco.emTransacao(c -> {
            Map<String, Integer> mapa = new HashMap<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT conta, count(*) FROM atividade WHERE importacao_id = ? AND conta IS NOT NULL AND categoria = ? GROUP BY conta")) {
                for (String categoria : categorias) {
                    p.setString(1, importacaoId);
                    p.setString(2, categoria);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) {
                            mapa.merge(r.getString(1), r.getInt(2), Integer::sum);
                        }
                    }
                }
            }
            return mapa;
        });
    }

    /** Textos (em minúsculas) da categoria, com a quantidade de vezes em que aparecem. Usado para os termos de pesquisa. */
    public List<ContaContada> textosMaisFrequentes(String importacaoId, String categoria) {
        return banco.emTransacao(c -> {
            List<ContaContada> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT lower(texto), count(*) AS n FROM atividade WHERE importacao_id = ? AND categoria = ? AND texto IS NOT NULL "
                            + "GROUP BY lower(texto) ORDER BY n DESC, lower(texto)")) {
                p.setString(1, importacaoId);
                p.setString(2, categoria);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        lista.add(new ContaContada(r.getString(1), r.getInt(2)));
                    }
                }
            }
            return lista;
        });
    }

    public Pagina<Linha> buscar(String importacaoId, Filtro filtro, int pagina, int tamanho) {
        int tam = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_DE_PAGINA));
        int de = Math.max(pagina, 0) * tam;
        String padrao = filtro.texto() == null || filtro.texto().isBlank() ? null : "%" + escapar(filtro.texto().strip()) + "%";
        return banco.emTransacao(c -> {
            int total;
            try (PreparedStatement p = c.prepareStatement(CONTAR)) {
                ligar(p, importacaoId, filtro, padrao);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    total = r.getInt(1);
                }
            }
            List<Linha> itens = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(LISTAR)) {
                int proximo = ligar(p, importacaoId, filtro, padrao);
                p.setInt(proximo++, tam);
                p.setInt(proximo, de);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long instante = r.getLong(6);
                        boolean semInstante = r.wasNull(); // wasNull() vale só para a ÚLTIMA coluna lida: capturar já
                        itens.add(new Linha(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), semInstante ? null : instante));
                    }
                }
            }
            return new Pagina<>(itens, total);
        });
    }

    static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Liga os 10 parâmetros do filtro e devolve o índice do próximo parâmetro. */
    private static int ligar(PreparedStatement p, String importacaoId, Filtro f, String padrao) throws SQLException {
        int i = 1;
        p.setString(i++, importacaoId);
        i = texto(p, i, f.categoria());
        i = texto(p, i, f.categoria());
        i = texto(p, i, padrao);
        i = texto(p, i, padrao);
        i = texto(p, i, padrao);
        i = numero(p, i, f.desde());
        i = numero(p, i, f.desde());
        i = numero(p, i, f.ate());
        return numero(p, i, f.ate());
    }

    static int texto(PreparedStatement p, int i, String valor) throws SQLException {
        if (valor == null) {
            p.setNull(i, Types.VARCHAR);
        } else {
            p.setString(i, valor);
        }
        return i + 1;
    }

    static int numero(PreparedStatement p, int i, Long valor) throws SQLException {
        if (valor == null) {
            p.setNull(i, Types.INTEGER);
        } else {
            p.setLong(i, valor);
        }
        return i + 1;
    }
}
