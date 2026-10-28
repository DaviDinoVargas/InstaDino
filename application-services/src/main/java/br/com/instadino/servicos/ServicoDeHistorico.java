package br.com.instadino.servicos;

import br.com.instadino.analise.social.ComparacaoDeImportacoes;
import br.com.instadino.analise.social.DiferencaDeConjuntos;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Histórico de importações, comparação entre períodos (Premium) e comparação manual de listas (Free). */
public final class ServicoDeHistorico {

    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final SessaoLocal sessao;
    private final RegistroDeAuditoriaLocal auditoria;
    private final Runnable aoAlterarDados;
    private final ObjectMapper json = new ObjectMapper();

    ServicoDeHistorico(PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes, SessaoLocal sessao,
                       RegistroDeAuditoriaLocal auditoria, Runnable aoAlterarDados) {
        this.politica = politica;
        this.importacoes = importacoes;
        this.sessao = sessao;
        this.auditoria = auditoria;
        this.aoAlterarDados = aoAlterarDados;
    }

    /** Premium: todo o histórico. Free: apenas a importação mais recente. */
    public List<ResumoDaImportacao> listar() {
        List<ResumoDaImportacao> todas = importacoes.listar(sessao.perfilAtivo());
        if (politica.permite(Funcionalidade.HISTORICO_COMPLETO) || todas.isEmpty()) {
            return todas;
        }
        return List.of(todas.get(0));
    }

    public Optional<ResumoDaImportacao> maisRecente() {
        return importacoes.maisRecente(sessao.perfilAtivo());
    }

    public void excluir(String importacaoId) {
        politica.exigir(Funcionalidade.EXCLUSAO_DE_DADOS);
        ResumoDaImportacao r = importacoes.obter(importacaoId).orElseThrow(() -> new NoSuchElementException("Importação inexistente."));
        if (r.perfilId() != sessao.perfilAtivo()) {
            throw new NoSuchElementException("Importação inexistente.");
        }
        importacoes.excluir(importacaoId);
        auditoria.registrar("IMPORTACAO_EXCLUIDA", null);
        aoAlterarDados.run();
    }

    /** Compara a mesma lista em duas importações. Exige o plano Premium. */
    public ComparacaoDeImportacoes comparar(String idAnterior, String idMaisRecente, TipoDeRelacionamento tipo) {
        politica.exigir(Funcionalidade.COMPARACAO_ENTRE_PERIODOS);
        ResumoDaImportacao anterior = daPerfil(idAnterior);
        ResumoDaImportacao recente = daPerfil(idMaisRecente);
        if (anterior.importadaEm().isAfter(recente.importadaEm())) {
            ResumoDaImportacao troca = anterior;
            anterior = recente;
            recente = troca;
        }
        return ComparacaoDeImportacoes.comparar(
                nomes(anterior.id(), tipo), nomes(recente.id(), tipo), completa(anterior, tipo), completa(recente, tipo));
    }

    /**
     * Um ponto do gráfico histórico: o que a exportação continha NAQUELA data. Os valores são observações nas datas das
     * importações, não uma série contínua. {@code null} indica que a lista não existia (ou veio incompleta) nessa importação.
     */
    public record PontoDaSerie(String importacaoId, java.time.Instant importadaEm, Integer seguidores, Integer seguindo, Integer mutuos) {
    }

    /** Quantidade máxima de importações no gráfico (as mais recentes), para limitar custo e tamanho. */
    public static final int MAXIMO_DE_PONTOS = 36;

    /** Série para o gráfico histórico (Premium), da importação mais antiga para a mais recente. */
    public List<PontoDaSerie> serieHistorica() {
        politica.exigir(Funcionalidade.GRAFICOS_HISTORICOS);
        List<ResumoDaImportacao> todas = new ArrayList<>(importacoes.listar(sessao.perfilAtivo()));
        todas.sort(java.util.Comparator.comparing(ResumoDaImportacao::importadaEm));
        if (todas.size() > MAXIMO_DE_PONTOS) {
            todas = todas.subList(todas.size() - MAXIMO_DE_PONTOS, todas.size());
        }
        List<PontoDaSerie> serie = new ArrayList<>();
        for (ResumoDaImportacao r : todas) {
            var c = importacoes.contagens(r.id());
            boolean seguidoresOk = completa(r, TipoDeRelacionamento.SEGUIDOR);
            boolean seguindoOk = completa(r, TipoDeRelacionamento.SEGUINDO);
            serie.add(new PontoDaSerie(r.id(), r.importadaEm(), seguidoresOk ? c.seguidores() : null, seguindoOk ? c.seguindo() : null,
                    seguidoresOk && seguindoOk ? c.mutuos() : null));
        }
        return serie;
    }

    /** Dados de qualidade de uma importação, para o relatório de qualidade. */
    public record QualidadeDaImportacao(ResumoDaImportacao resumo, Set<String> categoriasIncompletas, int avisos) {
    }

    public QualidadeDaImportacao qualidade(String importacaoId) {
        ResumoDaImportacao r = daPerfil(importacaoId);
        int avisos = 0;
        try {
            String bruto = importacoes.resumoJson(r.id());
            if (bruto != null) {
                avisos = json.readTree(bruto).path("avisos").asInt(0);
            }
        } catch (java.io.IOException e) {
            avisos = 0;
        }
        Set<String> incompletas = incompletas(r).stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(java.util.TreeSet::new));
        return new QualidadeDaImportacao(r, incompletas, avisos);
    }

    /** Comparação manual de duas listas coladas pelo usuário, uma conta por linha. Plano Free. */
    public DiferencaDeConjuntos<NomeDeUsuario> compararManualmente(String listaA, String listaB) {
        politica.exigir(Funcionalidade.COMPARACAO_MANUAL_LISTAS);
        return DiferencaDeConjuntos.entre(interpretar(listaA), interpretar(listaB));
    }

    static Set<NomeDeUsuario> interpretar(String texto) {
        if (texto == null) {
            return Set.of();
        }
        return texto.lines().map(String::strip).filter(l -> !l.isEmpty())
                .map(l -> NomeDeUsuario.deUrlDePerfil(l).or(() -> NomeDeUsuario.tentar(l)))
                .flatMap(Optional::stream).collect(Collectors.toSet());
    }

    private ResumoDaImportacao daPerfil(String id) {
        ResumoDaImportacao r = importacoes.obter(id).orElseThrow(() -> new NoSuchElementException("Importação inexistente."));
        if (r.perfilId() != sessao.perfilAtivo()) {
            throw new NoSuchElementException("Importação inexistente.");
        }
        return r;
    }

    private List<NomeDeUsuario> nomes(String importacaoId, TipoDeRelacionamento tipo) {
        List<NomeDeUsuario> lista = new ArrayList<>();
        importacoes.relacionamentos(importacaoId, tipo).forEach(r -> lista.add(r.usuario()));
        return lista;
    }

    private boolean completa(ResumoDaImportacao importacao, TipoDeRelacionamento tipo) {
        // a lista só é confiável se a categoria existia na importação e não foi marcada como incompleta
        String categoria = switch (tipo) {
            case SEGUIDOR -> "SEGUIDORES";
            case SEGUINDO -> "SEGUINDO";
            case SOLICITACAO_PENDENTE -> "SOLICITACOES_PENDENTES";
            case BLOQUEADO -> "BLOQUEADOS";
            case RESTRITO -> "RESTRITOS";
            case FAVORITO -> "FAVORITOS";
            case DEIXEI_DE_SEGUIR_RECENTE -> "DEIXEI_DE_SEGUIR";
            case SOLICITACAO_RECENTE -> "SOLICITACOES_RECENTES";
            case SOLICITACAO_RECEBIDA -> "SOLICITACOES_RECEBIDAS";
        };
        if (!importacao.categorias().containsKey(categoria)) {
            return false;
        }
        return !incompletas(importacao).contains(categoria.toLowerCase(Locale.ROOT));
    }

    private Set<String> incompletas(ResumoDaImportacao importacao) {
        try {
            String bruto = importacoes.resumoJson(importacao.id());
            if (bruto == null) {
                return Set.of();
            }
            JsonNode no = json.readTree(bruto).path("incompletas");
            Set<String> nomes = new java.util.HashSet<>();
            no.forEach(n -> nomes.add(n.asText().toLowerCase(Locale.ROOT)));
            return nomes;
        } catch (java.io.IOException e) {
            return Set.of("*");
        }
    }
}
