package br.com.instadino.persistencia.repositorio;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.BancoLocal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Importações e seus relacionamentos. Uma importação é gravada em UMA transação: se algo falhar, nada dela
 * permanece e o histórico anterior continua intacto.
 */
public final class RepositorioDeImportacoes {

    /** Dados necessários para gravar uma importação. */
    public record NovaImportacao(long perfilId, String versaoDoParser, String origem, String nomeDoArquivo, Long tamanhoEmBytes,
                                 int registrosValidos, int registrosInvalidos, Map<String, int[]> categorias,
                                 List<Relacionamento> relacionamentos, String resumoJson, List<AtividadeGravada> atividades,
                                 List<PublicacaoGravada> publicacoes, List<ConversaGravada> conversas) {

        public NovaImportacao(long perfilId, String versaoDoParser, String origem, String nomeDoArquivo, Long tamanhoEmBytes,
                              int registrosValidos, int registrosInvalidos, Map<String, int[]> categorias,
                              List<Relacionamento> relacionamentos, String resumoJson, List<AtividadeGravada> atividades,
                              List<PublicacaoGravada> publicacoes) {
            this(perfilId, versaoDoParser, origem, nomeDoArquivo, tamanhoEmBytes, registrosValidos, registrosInvalidos, categorias,
                    relacionamentos, resumoJson, atividades, publicacoes, List.of());
        }

        /** Importação sem atividade nem conteúdo (apenas relacionamentos). */
        public NovaImportacao(long perfilId, String versaoDoParser, String origem, String nomeDoArquivo, Long tamanhoEmBytes,
                              int registrosValidos, int registrosInvalidos, Map<String, int[]> categorias,
                              List<Relacionamento> relacionamentos, String resumoJson) {
            this(perfilId, versaoDoParser, origem, nomeDoArquivo, tamanhoEmBytes, registrosValidos, registrosInvalidos, categorias,
                    relacionamentos, resumoJson, List.of(), List.of());
        }
    }

    /** Registro de atividade a gravar; {@code categoria} é o nome da categoria de dados. */
    public record AtividadeGravada(String categoria, String subtipo, String conta, String texto, String referencia, Long instante) {
    }

    /** Agregados de uma conversa; nenhum texto de mensagem. {@code enviadas} é -1 quando o titular não foi identificado. */
    public record ConversaGravada(String titulo, int participantes, int total, int enviadas, int midias, Long primeiro, Long ultimo,
                                  List<MesGravado> meses) {
    }

    public record MesGravado(String mes, int total, int enviadas) {
    }

    public record MidiaGravada(String caminho, String tipo, Long tamanho) {
    }

    public record PublicacaoGravada(String categoria, String legenda, Long instante, List<MidiaGravada> midias) {
    }

    /** Linha do histórico. {@code categorias} mapeia o nome da categoria para {arquivos, registros}. */
    public record ResumoDaImportacao(String id, long perfilId, Instant importadaEm, String versaoDoParser, String origem,
                                     String nomeDoArquivo, Long tamanhoEmBytes, int registrosValidos, int registrosInvalidos,
                                     Map<String, int[]> categorias) {
    }

    /** Totais de uma importação; os mútuos são a interseção entre seguidores e seguindo (mesma regra da análise). */
    public record Contagens(int seguidores, int seguindo, int mutuos) {
    }

    private static final int LOTE = 5000;

    private final BancoLocal banco;

    public RepositorioDeImportacoes(BancoLocal banco) {
        this.banco = Objects.requireNonNull(banco, "banco");
    }

    /**
     * Grava a importação e, se {@code manterNoMaximo} for positivo, remove as mais antigas do perfil até restar esse
     * número, tudo na mesma transação. Devolve o identificador da nova importação.
     */
    public String salvar(NovaImportacao nova, int manterNoMaximo) {
        String id = UUID.randomUUID().toString();
        Instant agora = Instant.now();
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO importacao (id, perfil_id, importada_em, versao_parser, origem, nome_arquivo, tamanho_bytes, "
                            + "registros_validos, registros_invalidos, resumo_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                p.setString(1, id);
                p.setLong(2, nova.perfilId());
                p.setString(3, agora.toString());
                p.setString(4, nova.versaoDoParser());
                p.setString(5, nova.origem());
                p.setString(6, nova.nomeDoArquivo());
                if (nova.tamanhoEmBytes() == null) {
                    p.setNull(7, java.sql.Types.INTEGER);
                } else {
                    p.setLong(7, nova.tamanhoEmBytes());
                }
                p.setInt(8, nova.registrosValidos());
                p.setInt(9, nova.registrosInvalidos());
                p.setString(10, nova.resumoJson());
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO importacao_categoria (importacao_id, categoria, arquivos, registros) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, int[]> e : nova.categorias().entrySet()) {
                    p.setString(1, id);
                    p.setString(2, e.getKey());
                    p.setInt(3, e.getValue()[0]);
                    p.setInt(4, e.getValue()[1]);
                    p.addBatch();
                }
                p.executeBatch();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO relacionamento (importacao_id, tipo, usuario, url_perfil, data_registro) VALUES (?, ?, ?, ?, ?)")) {
                int pendentes = 0;
                for (Relacionamento r : nova.relacionamentos()) {
                    p.setString(1, id);
                    p.setString(2, r.tipo().name());
                    p.setString(3, r.usuario().valor());
                    p.setString(4, r.usuario().urlDoPerfil());
                    if (r.instanteEmSegundos() == null) {
                        p.setNull(5, java.sql.Types.INTEGER);
                    } else {
                        p.setLong(5, r.instanteEmSegundos());
                    }
                    p.addBatch();
                    if (++pendentes % LOTE == 0) {
                        p.executeBatch();
                    }
                }
                p.executeBatch();
            }
            gravarAtividades(c, id, nova.atividades());
            gravarPublicacoes(c, id, nova.publicacoes());
            gravarConversas(c, id, nova.conversas());
            if (manterNoMaximo > 0) {
                try (PreparedStatement p = c.prepareStatement(
                        "DELETE FROM importacao WHERE perfil_id = ? AND id NOT IN ("
                                + "SELECT id FROM importacao WHERE perfil_id = ? ORDER BY importada_em DESC, rowid DESC LIMIT ?)")) {
                    p.setLong(1, nova.perfilId());
                    p.setLong(2, nova.perfilId());
                    p.setInt(3, manterNoMaximo);
                    p.executeUpdate();
                }
            }
            return null;
        });
        return id;
    }

    private static void gravarAtividades(java.sql.Connection c, String id, List<AtividadeGravada> atividades) throws java.sql.SQLException {
        if (atividades.isEmpty()) {
            return;
        }
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO atividade (importacao_id, categoria, subtipo, conta, texto, referencia, instante) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int pendentes = 0;
            for (AtividadeGravada a : atividades) {
                p.setString(1, id);
                p.setString(2, a.categoria());
                p.setString(3, a.subtipo());
                p.setString(4, a.conta());
                p.setString(5, a.texto());
                p.setString(6, a.referencia());
                if (a.instante() == null) {
                    p.setNull(7, java.sql.Types.INTEGER);
                } else {
                    p.setLong(7, a.instante());
                }
                p.addBatch();
                if (++pendentes % LOTE == 0) {
                    p.executeBatch();
                }
            }
            p.executeBatch();
        }
    }

    private static void gravarConversas(java.sql.Connection c, String id, List<ConversaGravada> conversas) throws java.sql.SQLException {
        if (conversas.isEmpty()) {
            return;
        }
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO conversa (importacao_id, titulo, participantes, total, enviadas, midias, primeiro, ultimo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS);
             PreparedStatement m = c.prepareStatement("INSERT INTO conversa_mes (conversa_id, mes, total, enviadas) VALUES (?, ?, ?, ?)")) {
            for (ConversaGravada conversa : conversas) {
                p.setString(1, id);
                p.setString(2, conversa.titulo().length() > 200 ? conversa.titulo().substring(0, 200) : conversa.titulo());
                p.setInt(3, conversa.participantes());
                p.setInt(4, conversa.total());
                p.setInt(5, conversa.enviadas());
                p.setInt(6, conversa.midias());
                RepositorioDeAtividade.numero(p, 7, conversa.primeiro());
                RepositorioDeAtividade.numero(p, 8, conversa.ultimo());
                p.executeUpdate();
                long conversaId;
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    conversaId = k.getLong(1);
                }
                for (MesGravado mes : conversa.meses()) {
                    m.setLong(1, conversaId);
                    m.setString(2, mes.mes());
                    m.setInt(3, mes.total());
                    m.setInt(4, mes.enviadas());
                    m.addBatch();
                }
                m.executeBatch();
            }
        }
    }

    private static void gravarPublicacoes(java.sql.Connection c, String id, List<PublicacaoGravada> publicacoes) throws java.sql.SQLException {
        if (publicacoes.isEmpty()) {
            return;
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO publicacao (importacao_id, categoria, legenda, instante) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
             PreparedStatement m = c.prepareStatement("INSERT INTO midia (publicacao_id, ordem, caminho, tipo, tamanho) VALUES (?, ?, ?, ?, ?)")) {
            for (PublicacaoGravada pub : publicacoes) {
                p.setString(1, id);
                p.setString(2, pub.categoria());
                p.setString(3, pub.legenda());
                if (pub.instante() == null) {
                    p.setNull(4, java.sql.Types.INTEGER);
                } else {
                    p.setLong(4, pub.instante());
                }
                p.executeUpdate();
                long publicacaoId;
                try (ResultSet k = p.getGeneratedKeys()) {
                    k.next();
                    publicacaoId = k.getLong(1);
                }
                int ordem = 0;
                for (MidiaGravada md : pub.midias()) {
                    m.setLong(1, publicacaoId);
                    m.setInt(2, ordem++);
                    m.setString(3, md.caminho());
                    m.setString(4, md.tipo());
                    if (md.tamanho() == null) {
                        m.setNull(5, java.sql.Types.INTEGER);
                    } else {
                        m.setLong(5, md.tamanho());
                    }
                    m.addBatch();
                }
                m.executeBatch();
            }
        }
    }

    /** Importações do perfil, da mais recente para a mais antiga. */
    public List<ResumoDaImportacao> listar(long perfilId) {
        return banco.emTransacao(c -> {
            List<ResumoDaImportacao> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id, perfil_id, importada_em, versao_parser, origem, nome_arquivo, tamanho_bytes, registros_validos, "
                            + "registros_invalidos FROM importacao WHERE perfil_id = ? ORDER BY importada_em DESC, rowid DESC")) {
                p.setLong(1, perfilId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long tamanho = r.getLong(7);
                        boolean semTamanho = r.wasNull(); // wasNull() vale só para a ÚLTIMA coluna lida: capturar já
                        lista.add(new ResumoDaImportacao(r.getString(1), r.getLong(2), Instant.parse(r.getString(3)), r.getString(4),
                                r.getString(5), r.getString(6), semTamanho ? null : tamanho, r.getInt(8), r.getInt(9),
                                categoriasDe(c, r.getString(1))));
                    }
                }
            }
            return lista;
        });
    }

    public Optional<ResumoDaImportacao> maisRecente(long perfilId) {
        return listar(perfilId).stream().findFirst();
    }

    public Optional<ResumoDaImportacao> obter(String id) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id, perfil_id, importada_em, versao_parser, origem, nome_arquivo, tamanho_bytes, registros_validos, "
                            + "registros_invalidos FROM importacao WHERE id = ?")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) {
                        return Optional.<ResumoDaImportacao>empty();
                    }
                    long tamanho = r.getLong(7);
                    boolean semTamanho = r.wasNull();
                    return Optional.of(new ResumoDaImportacao(r.getString(1), r.getLong(2), Instant.parse(r.getString(3)), r.getString(4),
                            r.getString(5), r.getString(6), semTamanho ? null : tamanho, r.getInt(8), r.getInt(9), categoriasDe(c, id)));
                }
            }
        });
    }

    public List<Relacionamento> relacionamentos(String importacaoId, TipoDeRelacionamento tipo) {
        return banco.emTransacao(c -> {
            List<Relacionamento> lista = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT usuario, data_registro FROM relacionamento WHERE importacao_id = ? AND tipo = ? ORDER BY usuario")) {
                p.setString(1, importacaoId);
                p.setString(2, tipo.name());
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long data = r.getLong(2);
                        Long instante = r.wasNull() ? null : data;
                        lista.add(new Relacionamento(tipo, new NomeDeUsuario(r.getString(1)), instante));
                    }
                }
            }
            return lista;
        });
    }

    /** JSON de resumo gravado com a importação (categorias incompletas etc.); nulo se não houver. */
    /** Conta seguidores, seguindo e mútuos direto no banco, sem carregar as listas. */
    public Contagens contagens(String importacaoId) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT (SELECT count(*) FROM relacionamento WHERE importacao_id = ? AND tipo = 'SEGUIDOR'), "
                            + "(SELECT count(*) FROM relacionamento WHERE importacao_id = ? AND tipo = 'SEGUINDO'), "
                            + "(SELECT count(*) FROM relacionamento a JOIN relacionamento b "
                            + "ON b.importacao_id = a.importacao_id AND b.usuario = a.usuario AND b.tipo = 'SEGUINDO' "
                            + "WHERE a.importacao_id = ? AND a.tipo = 'SEGUIDOR')")) {
                p.setString(1, importacaoId);
                p.setString(2, importacaoId);
                p.setString(3, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    return new Contagens(r.getInt(1), r.getInt(2), r.getInt(3));
                }
            }
        });
    }

    public String resumoJson(String importacaoId) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT resumo_json FROM importacao WHERE id = ?")) {
                p.setString(1, importacaoId);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? r.getString(1) : null;
                }
            }
        });
    }

    public int contar(String importacaoId, TipoDeRelacionamento tipo) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT count(*) FROM relacionamento WHERE importacao_id = ? AND tipo = ?")) {
                p.setString(1, importacaoId);
                p.setString(2, tipo.name());
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    return r.getInt(1);
                }
            }
        });
    }

    public void excluir(String importacaoId) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM importacao WHERE id = ?")) {
                p.setString(1, importacaoId);
                p.executeUpdate();
            }
            return null;
        });
    }

    public void excluirTodas(long perfilId) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM importacao WHERE perfil_id = ?")) {
                p.setLong(1, perfilId);
                p.executeUpdate();
            }
            return null;
        });
    }

    private static Map<String, int[]> categoriasDe(java.sql.Connection c, String id) throws java.sql.SQLException {
        Map<String, int[]> mapa = new LinkedHashMap<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT categoria, arquivos, registros FROM importacao_categoria WHERE importacao_id = ? ORDER BY categoria")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    mapa.put(r.getString(1), new int[] {r.getInt(2), r.getInt(3)});
                }
            }
        }
        return mapa;
    }
}
