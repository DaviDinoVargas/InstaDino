package br.com.instadino.servicos;

import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade.ContaContada;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade.Filtro;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Análise da atividade REGISTRADA na exportação (curtidas, comentários, salvos, pesquisas, visualizações, interações com
 * stories). Premium. Só afirma o que os registros mostram — "N vezes", datas, contagens. Nunca interpreta intenção,
 * interesse ou sentimento, e lembra que os registros cobrem apenas o período e os tipos de dado presentes na exportação.
 */
public final class ServicoDeAtividade {

    public static final String LIMITACAO = "Estes números vêm apenas dos registros presentes na exportação, que cobrem um período e alguns tipos de "
            + "atividade definidos pelo Instagram. Mostram o que VOCÊ registrou (por exemplo, curtidas que deu), não o que outras pessoas fizeram "
            + "com seu perfil, e não permitem concluir interesse, intenção ou sentimento.";

    /** Categorias de atividade exibidas, na ordem de apresentação. */
    public static final List<CategoriaDeDados> CATEGORIAS = List.of(CategoriaDeDados.CURTIDAS, CategoriaDeDados.COMENTARIOS,
            CategoriaDeDados.SALVOS, CategoriaDeDados.INTERACOES_STORIES, CategoriaDeDados.VISUALIZACOES, CategoriaDeDados.PESQUISAS);

    public record ResumoPorCategoria(CategoriaDeDados categoria, int total, Instant de, Instant ate) {
    }

    public record Registro(CategoriaDeDados categoria, String subtipo, String conta, String texto, String referencia, Instant instante) {
    }

    /** Uma conta no ranking, com a situação dela nas suas listas (pode estar desatualizada ou incompleta). */
    public record ItemDeRanking(String conta, int quantidade, boolean voceSegue, boolean seguidor) {
    }

    public record ContaComInteracao(String conta, int interacoes) {
    }

    /** Cruzamento entre contas que você segue e que não aparecem como seguidores e a atividade que você registrou com elas. */
    public record CruzamentoDeNaoReciprocos(List<ContaComInteracao> comInteracao, List<String> semNenhumaInteracao, int totalDeNaoReciprocos) {
    }

    /** Tema ou anunciante registrado pelo Instagram, com a quantidade de vezes em que aparece. */
    public record ItemDeInteresse(String texto, int vezes) {
    }

    /** Conteúdo de um arquivo de anúncios e preferências (por exemplo, os temas inferidos ou os anunciantes que usam seus dados). */
    public record GrupoDeInteresses(String arquivo, int total, List<ItemDeInteresse> itens) {
    }

    public static final String LIMITACAO_DE_INTERESSES = "Estes itens são o que a exportação registra sobre anúncios, temas e anunciantes. Os rótulos "
            + "e nomes de arquivo variam entre versões da exportação; se algo parecer faltar, confira o diagnóstico da importação.";

    public record TermoBuscado(String termo, int vezes, boolean voceSegue) {
    }

    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final RepositorioDeAtividade atividade;
    private final ServicoDeRelacionamentos relacionamentos;
    private final SessaoLocal sessao;
    private final ZoneId fuso;

    ServicoDeAtividade(PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes, RepositorioDeAtividade atividade,
                       ServicoDeRelacionamentos relacionamentos, SessaoLocal sessao, ZoneId fuso) {
        this.politica = politica;
        this.importacoes = importacoes;
        this.atividade = atividade;
        this.relacionamentos = relacionamentos;
        this.sessao = sessao;
        this.fuso = fuso;
    }

    private Optional<ResumoDaImportacao> ultima() {
        politica.exigir(Funcionalidade.ANALISE_DE_ATIVIDADE);
        return importacoes.maisRecente(sessao.perfilAtivo());
    }

    /** Verdadeiro se a importação mais recente tem algum registro de atividade. */
    public boolean temDados() {
        return ultima().map(i -> atividade.contagemPorCategoria(i.id()).keySet().stream()
                .anyMatch(c -> CATEGORIAS.stream().anyMatch(x -> x.name().equals(c)))).orElse(false);
    }

    /** Há registros de anúncios e interesses na importação mais recente. */
    public boolean temInteresses() {
        return ultima().map(i -> atividade.contagemPorCategoria(i.id()).containsKey(CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS.name())).orElse(false);
    }

    /** "O que o Instagram registra sobre você": temas, anunciantes e anúncios vistos, agrupados por arquivo de origem. */
    public List<GrupoDeInteresses> interesses(int limitePorGrupo) {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        String categoria = CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS.name();
        List<GrupoDeInteresses> grupos = new ArrayList<>();
        for (var origem : atividade.subtipos(i.get().id(), categoria)) {
            List<ItemDeInteresse> itens = atividade.textosDoSubtipo(i.get().id(), categoria, origem.conta(), limitePorGrupo).stream()
                    .map(t -> new ItemDeInteresse(t.conta(), t.quantidade())).toList();
            grupos.add(new GrupoDeInteresses(origem.conta(), origem.quantidade(), itens));
        }
        return grupos;
    }

    public List<ResumoPorCategoria> resumo() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> contagem = atividade.contagemPorCategoria(i.get().id());
        List<ResumoPorCategoria> lista = new ArrayList<>();
        for (CategoriaDeDados c : CATEGORIAS) {
            Integer total = contagem.get(c.name());
            if (total == null) {
                continue;
            }
            long[] periodo = atividade.periodo(i.get().id(), c.name());
            lista.add(new ResumoPorCategoria(c, total, periodo.length == 0 ? null : Instant.ofEpochSecond(periodo[0]),
                    periodo.length == 0 ? null : Instant.ofEpochSecond(periodo[1])));
        }
        return lista;
    }

    /** Registros por mês (no fuso do computador), em ordem cronológica. */
    public TreeMap<YearMonth, Integer> porMes(CategoriaDeDados categoria) {
        TreeMap<YearMonth, Integer> mapa = new TreeMap<>();
        for (long t : instantes(categoria)) {
            mapa.merge(YearMonth.from(Instant.ofEpochSecond(t).atZone(fuso)), 1, Integer::sum);
        }
        return mapa;
    }

    /** Registros por dia (para o calendário de atividade). */
    public TreeMap<LocalDate, Integer> porDia(CategoriaDeDados categoria) {
        TreeMap<LocalDate, Integer> mapa = new TreeMap<>();
        for (long t : instantes(categoria)) {
            mapa.merge(Instant.ofEpochSecond(t).atZone(fuso).toLocalDate(), 1, Integer::sum);
        }
        return mapa;
    }

    /** Mapa de calor [dia da semana 0=segunda..6=domingo][hora 0..23] somando as categorias informadas. */
    public int[][] mapaDeCalor(List<CategoriaDeDados> categorias) {
        int[][] grade = new int[7][24];
        for (CategoriaDeDados c : categorias) {
            for (long t : instantes(c)) {
                var z = Instant.ofEpochSecond(t).atZone(fuso);
                grade[z.getDayOfWeek().getValue() - 1][z.getHour()]++;
            }
        }
        return grade;
    }

    private long[] instantes(CategoriaDeDados categoria) {
        Optional<ResumoDaImportacao> i = ultima();
        return i.isEmpty() ? new long[0] : atividade.instantes(i.get().id(), categoria.name());
    }

    public List<ItemDeRanking> ranking(CategoriaDeDados categoria, int limite) {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        Set<String> sigo = nomes(VisaoDeRelacionamento.SEGUINDO);
        Set<String> seguidores = nomes(VisaoDeRelacionamento.SEGUIDORES);
        List<ItemDeRanking> lista = new ArrayList<>();
        for (ContaContada c : atividade.ranking(i.get().id(), categoria.name(), limite)) {
            lista.add(new ItemDeRanking(c.conta(), c.quantidade(), sigo.contains(c.conta()), seguidores.contains(c.conta())));
        }
        return lista;
    }

    public Pagina<Registro> buscar(CategoriaDeDados categoria, String texto, Instant desde, Instant ate, int pagina, int tamanho) {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return new Pagina<>(List.of(), 0, pagina, tamanho);
        }
        var p = atividade.buscar(i.get().id(), new Filtro(categoria == null ? null : categoria.name(), texto,
                desde == null ? null : desde.getEpochSecond(), ate == null ? null : ate.getEpochSecond()), pagina, tamanho);
        return new Pagina<>(p.itens().stream().map(l -> new Registro(CategoriaDeDados.valueOf(l.categoria()), l.subtipo(), l.conta(), l.texto(),
                l.referencia(), l.instante() == null ? null : Instant.ofEpochSecond(l.instante()))).toList(), p.total(), pagina, Math.max(1, Math.min(tamanho, 500)));
    }

    // ---- cruzamentos (apenas contagens) ----

    /** Contas que você segue e que não aparecem entre os seguidores, separadas entre com e sem atividade registrada por você. */
    public CruzamentoDeNaoReciprocos cruzarNaoReciprocos() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return new CruzamentoDeNaoReciprocos(List.of(), List.of(), 0);
        }
        Set<String> naoSeguem = nomes(VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA);
        Map<String, Integer> totais = atividade.totaisPorConta(i.get().id(), CATEGORIAS.stream().filter(c -> c != CategoriaDeDados.PESQUISAS).map(Enum::name).toList());
        List<ContaComInteracao> com = new ArrayList<>();
        List<String> sem = new ArrayList<>();
        for (String conta : naoSeguem) {
            int n = totais.getOrDefault(conta, 0);
            if (n > 0) {
                com.add(new ContaComInteracao(conta, n));
            } else {
                sem.add(conta);
            }
        }
        com.sort(Comparator.comparingInt(ContaComInteracao::interacoes).reversed().thenComparing(ContaComInteracao::conta));
        sem.sort(Comparator.naturalOrder());
        return new CruzamentoDeNaoReciprocos(com, sem, naoSeguem.size());
    }

    /** Seguidores com os quais VOCÊ não registrou nenhuma atividade (não diz nada sobre o que eles fizeram com você). */
    public List<String> seguidoresSemAtividadeSua() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> totais = atividade.totaisPorConta(i.get().id(), CATEGORIAS.stream().filter(c -> c != CategoriaDeDados.PESQUISAS).map(Enum::name).toList());
        return nomes(VisaoDeRelacionamento.SEGUIDORES).stream().filter(c -> !totais.containsKey(c)).sorted().toList();
    }

    /** Termos pesquisados que são nomes de usuário, com a informação de você já os seguir ou não. Contagens apenas. */
    public List<TermoBuscado> perfisBuscados() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        Set<String> sigo = nomes(VisaoDeRelacionamento.SEGUINDO);
        List<TermoBuscado> lista = new ArrayList<>();
        for (ContaContada t : atividade.textosMaisFrequentes(i.get().id(), CategoriaDeDados.PESQUISAS.name())) {
            Optional<NomeDeUsuario> nome = NomeDeUsuario.tentar(t.conta());
            if (nome.isPresent() && t.conta().strip().indexOf(' ') < 0) {
                lista.add(new TermoBuscado(nome.get().valor(), t.quantidade(), sigo.contains(nome.get().valor())));
            }
        }
        return lista;
    }

    private Set<String> nomes(VisaoDeRelacionamento visao) {
        Set<String> nomes = new HashSet<>();
        relacionamentos.todas(visao).forEach(c -> nomes.add(c.usuario()));
        return nomes;
    }
}
