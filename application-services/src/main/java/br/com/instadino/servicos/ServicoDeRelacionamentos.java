package br.com.instadino.servicos;

import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.analise.social.ResumoSocial;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Painel social e listas de relacionamentos da importação mais recente do perfil ativo. */
public final class ServicoDeRelacionamentos {

    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final SessaoLocal sessao;
    private final RepositorioDeAnotacoes anotacoes;

    private String importacaoEmCache;
    private Map<TipoDeRelacionamento, List<Relacionamento>> cache;

    ServicoDeRelacionamentos(PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes, SessaoLocal sessao,
                             RepositorioDeAnotacoes anotacoes) {
        this.anotacoes = anotacoes;
        this.politica = politica;
        this.importacoes = importacoes;
        this.sessao = sessao;
    }

    synchronized void invalidarCache() {
        importacaoEmCache = null;
        cache = null;
    }

    public Optional<ResumoDoPainel> painel() {
        politica.exigir(Funcionalidade.DASHBOARD_SOCIAL);
        Optional<ResumoDaImportacao> ultima = importacoes.maisRecente(sessao.perfilAtivo());
        if (ultima.isEmpty()) {
            return Optional.empty();
        }
        Map<TipoDeRelacionamento, List<Relacionamento>> dados = carregar(ultima.get());
        ResumoSocial r = AnaliseDeSeguidores.analisar(nomes(dados, TipoDeRelacionamento.SEGUIDOR), nomes(dados, TipoDeRelacionamento.SEGUINDO));
        boolean temAsDuas = !dados.getOrDefault(TipoDeRelacionamento.SEGUIDOR, List.of()).isEmpty()
                && !dados.getOrDefault(TipoDeRelacionamento.SEGUINDO, List.of()).isEmpty();
        return Optional.of(new ResumoDoPainel(ultima.get(), r.totalSeguidores(), r.totalSeguindo(), r.mutuos().size(),
                r.naoSeguemDeVolta().size(), r.voceNaoSegue().size(), r.proporcaoSeguidoresPorSeguindo(), temAsDuas));
    }

    public Pagina<ContaListada> consultar(ConsultaDeRelacionamentos consulta) {
        politica.exigir(Funcionalidade.RELACIONAMENTOS_BASICO);
        if (!consulta.busca().isEmpty()) {
            politica.exigir(Funcionalidade.BUSCA_BASICA);
        }
        if (consulta.usaFiltrosCompostos()) {
            politica.exigir(Funcionalidade.FILTROS_COMPOSTOS);
        }
        if (consulta.somenteFavoritas()) {
            politica.exigir(Funcionalidade.FAVORITOS);
        }
        if (consulta.etiquetaId() != null) {
            politica.exigir(Funcionalidade.ETIQUETAS);
        }
        List<ContaListada> filtradas = filtrar(consulta);
        int total = filtradas.size();
        int de = Math.min(consulta.pagina() * consulta.tamanho(), total);
        int ate = Math.min(de + consulta.tamanho(), total);
        return new Pagina<>(filtradas.subList(de, ate), total, consulta.pagina(), consulta.tamanho());
    }

    /** Todas as contas da visão (sem paginação), na ordem alfabética. Usado pelas exportações. */
    public List<ContaListada> todas(VisaoDeRelacionamento visao) {
        politica.exigir(Funcionalidade.RELACIONAMENTOS_BASICO);
        return filtrar(ConsultaDeRelacionamentos.de(visao));
    }

    private List<ContaListada> filtrar(ConsultaDeRelacionamentos consulta) {
        Optional<ResumoDaImportacao> ultima = importacoes.maisRecente(sessao.perfilAtivo());
        if (ultima.isEmpty()) {
            return List.of();
        }
        Map<TipoDeRelacionamento, List<Relacionamento>> dados = carregar(ultima.get());
        List<ContaListada> base = visao(consulta.visao(), dados);
        String busca = consulta.busca().toLowerCase(Locale.ROOT);
        boolean usaAnotacoes = consulta.somenteFavoritas() || consulta.etiquetaId() != null;
        Map<String, RepositorioDeAnotacoes.Anotacao> marcadas = usaAnotacoes ? anotacoes.doPerfil(sessao.perfilAtivo()) : Map.of();
        List<ContaListada> resultado = new ArrayList<>();
        for (ContaListada c : base) {
            if (usaAnotacoes) {
                RepositorioDeAnotacoes.Anotacao a = marcadas.get(c.usuario());
                if (a == null || (consulta.somenteFavoritas() && !a.favorito())
                        || (consulta.etiquetaId() != null && a.etiquetas().stream().noneMatch(e -> e.id() == consulta.etiquetaId()))) {
                    continue;
                }
            }
            if (!busca.isEmpty() && !c.usuario().contains(busca)) {
                continue;
            }
            if (consulta.somenteComData() && c.registradaEm() == null) {
                continue;
            }
            if (consulta.desde() != null && (c.registradaEm() == null || c.registradaEm().isBefore(consulta.desde()))) {
                continue;
            }
            if (consulta.ate() != null && (c.registradaEm() == null || c.registradaEm().isAfter(consulta.ate()))) {
                continue;
            }
            resultado.add(c);
        }
        resultado.sort(comparador(consulta.ordenacao()));
        return resultado;
    }

    private static Comparator<ContaListada> comparador(ConsultaDeRelacionamentos.Ordenacao ordenacao) {
        Comparator<ContaListada> alfabetica = Comparator.comparing(ContaListada::usuario);
        Comparator<Instant> instantes = Comparator.nullsLast(Comparator.naturalOrder());
        return switch (ordenacao) {
            case ALFABETICA -> alfabetica;
            case ALFABETICA_INVERSA -> alfabetica.reversed();
            case MAIS_RECENTES -> Comparator.comparing(ContaListada::registradaEm, instantes.reversed()).thenComparing(alfabetica);
            case MAIS_ANTIGOS -> Comparator.comparing(ContaListada::registradaEm, instantes).thenComparing(alfabetica);
        };
    }

    private static List<ContaListada> visao(VisaoDeRelacionamento visao, Map<TipoDeRelacionamento, List<Relacionamento>> dados) {
        return switch (visao) {
            case SEGUIDORES -> contas(dados.getOrDefault(TipoDeRelacionamento.SEGUIDOR, List.of()));
            case SEGUINDO -> contas(dados.getOrDefault(TipoDeRelacionamento.SEGUINDO, List.of()));
            case PENDENTES -> contas(dados.getOrDefault(TipoDeRelacionamento.SOLICITACAO_PENDENTE, List.of()));
            case BLOQUEADOS -> contas(dados.getOrDefault(TipoDeRelacionamento.BLOQUEADO, List.of()));
            case RESTRITOS -> contas(dados.getOrDefault(TipoDeRelacionamento.RESTRITO, List.of()));
            case FAVORITOS -> contas(dados.getOrDefault(TipoDeRelacionamento.FAVORITO, List.of()));
            case MUTUOS, NAO_SEGUEM_DE_VOLTA, VOCE_NAO_SEGUE -> derivada(visao, dados);
        };
    }

    private static List<ContaListada> derivada(VisaoDeRelacionamento visao, Map<TipoDeRelacionamento, List<Relacionamento>> dados) {
        ResumoSocial r = AnaliseDeSeguidores.analisar(nomes(dados, TipoDeRelacionamento.SEGUIDOR), nomes(dados, TipoDeRelacionamento.SEGUINDO));
        Set<NomeDeUsuario> alvo = switch (visao) {
            case MUTUOS -> r.mutuos();
            case NAO_SEGUEM_DE_VOLTA -> r.naoSeguemDeVolta();
            default -> r.voceNaoSegue();
        };
        // a data registrada é a do relacionamento de origem: seguidor para "mútuos" e "você não segue", seguindo para "não seguem de volta"
        TipoDeRelacionamento origem = visao == VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA ? TipoDeRelacionamento.SEGUINDO : TipoDeRelacionamento.SEGUIDOR;
        Map<NomeDeUsuario, Long> datas = new java.util.HashMap<>();
        for (Relacionamento rel : dados.getOrDefault(origem, List.of())) {
            datas.put(rel.usuario(), rel.instanteEmSegundos());
        }
        List<ContaListada> lista = new ArrayList<>();
        for (NomeDeUsuario u : alvo) {
            Long ts = datas.get(u);
            lista.add(new ContaListada(u.valor(), u.urlDoPerfil(), ts == null ? null : Instant.ofEpochSecond(ts)));
        }
        return lista;
    }

    private static List<ContaListada> contas(List<Relacionamento> relacionamentos) {
        List<ContaListada> lista = new ArrayList<>(relacionamentos.size());
        for (Relacionamento r : relacionamentos) {
            lista.add(new ContaListada(r.usuario().valor(), r.usuario().urlDoPerfil(),
                    r.instanteEmSegundos() == null ? null : Instant.ofEpochSecond(r.instanteEmSegundos())));
        }
        return lista;
    }

    private static List<NomeDeUsuario> nomes(Map<TipoDeRelacionamento, List<Relacionamento>> dados, TipoDeRelacionamento tipo) {
        return dados.getOrDefault(tipo, List.of()).stream().map(Relacionamento::usuario).toList();
    }

    private synchronized Map<TipoDeRelacionamento, List<Relacionamento>> carregar(ResumoDaImportacao importacao) {
        if (importacao.id().equals(importacaoEmCache) && cache != null) {
            return cache;
        }
        Map<TipoDeRelacionamento, List<Relacionamento>> dados = new EnumMap<>(TipoDeRelacionamento.class);
        for (TipoDeRelacionamento tipo : TipoDeRelacionamento.values()) {
            dados.put(tipo, importacoes.relacionamentos(importacao.id(), tipo));
        }
        importacaoEmCache = importacao.id();
        cache = dados;
        return dados;
    }
}
