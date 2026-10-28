package br.com.instadino.servicos;

import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDeConteudo;
import br.com.instadino.persistencia.repositorio.RepositorioDeConteudo.Filtro;
import br.com.instadino.persistencia.repositorio.RepositorioDeConteudo.TotalDeMidia;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Catálogo local de publicações, reels e stories (Premium): busca por legenda, filtros por categoria, tipo de mídia e
 * período, e estatísticas de frequência. Guarda apenas metadados; as mídias permanecem no ZIP original da exportação.
 */
public final class ServicoDeConteudo {

    public static final String LIMITACAO = "O catálogo guarda legenda, data e o caminho, tipo e tamanho das mídias. As fotos e vídeos em si ficam "
            + "no arquivo ZIP da exportação e não são copiados para o InstaDino.";

    public static final List<CategoriaDeDados> CATEGORIAS = List.of(CategoriaDeDados.PUBLICACOES, CategoriaDeDados.REELS, CategoriaDeDados.STORIES);

    public record Midia(String caminho, String tipo, Long tamanhoEmBytes) {
    }

    public record Item(CategoriaDeDados categoria, String legenda, Instant data, List<Midia> midias) {
    }

    public record Totais(Map<CategoriaDeDados, Integer> porCategoria, List<TotalDeMidia> midias) {
        public int total() {
            return porCategoria.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Frequência de publicação de uma categoria, calculada sobre os meses com ao menos uma publicação datada. */
    public record Frequencia(int publicacoesComData, int mesesNoPeriodo, int mesesComPublicacao, double mediaPorMesNoPeriodo, YearMonth mesMaisAtivo,
                             int publicacoesNoMesMaisAtivo, Instant primeira, Instant ultima) {
    }

    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final RepositorioDeConteudo conteudo;
    private final SessaoLocal sessao;
    private final ZoneId fuso;

    ServicoDeConteudo(PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes, RepositorioDeConteudo conteudo, SessaoLocal sessao, ZoneId fuso) {
        this.politica = politica;
        this.importacoes = importacoes;
        this.conteudo = conteudo;
        this.sessao = sessao;
        this.fuso = fuso;
    }

    private Optional<ResumoDaImportacao> ultima() {
        politica.exigir(Funcionalidade.BIBLIOTECA_DE_CONTEUDO);
        return importacoes.maisRecente(sessao.perfilAtivo());
    }

    public boolean temDados() {
        return ultima().map(i -> !conteudo.contagemPorCategoria(i.id()).isEmpty()).orElse(false);
    }

    public Totais totais() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return new Totais(Map.of(), List.of());
        }
        Map<CategoriaDeDados, Integer> mapa = new java.util.EnumMap<>(CategoriaDeDados.class);
        conteudo.contagemPorCategoria(i.get().id()).forEach((c, n) -> mapa.put(CategoriaDeDados.valueOf(c), n));
        return new Totais(mapa, conteudo.totaisDeMidia(i.get().id()));
    }

    public Pagina<Item> buscar(CategoriaDeDados categoria, String legenda, String tipoDeMidia, Instant desde, Instant ate, int pagina, int tamanho) {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return new Pagina<>(List.of(), 0, pagina, tamanho);
        }
        var p = conteudo.buscar(i.get().id(), new Filtro(categoria == null ? null : categoria.name(), legenda, tipoDeMidia,
                desde == null ? null : desde.getEpochSecond(), ate == null ? null : ate.getEpochSecond()), pagina, tamanho);
        return new Pagina<>(p.itens().stream().map(x -> new Item(CategoriaDeDados.valueOf(x.categoria()), x.legenda(),
                x.instante() == null ? null : Instant.ofEpochSecond(x.instante()),
                x.midias().stream().map(m -> new Midia(m.caminho(), m.tipo(), m.tamanho())).toList())).toList(),
                p.total(), pagina, Math.max(1, Math.min(tamanho, 200)));
    }

    public TreeMap<YearMonth, Integer> porMes(CategoriaDeDados categoria) {
        TreeMap<YearMonth, Integer> mapa = new TreeMap<>();
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isPresent()) {
            for (long t : conteudo.instantes(i.get().id(), categoria.name())) {
                mapa.merge(YearMonth.from(Instant.ofEpochSecond(t).atZone(fuso)), 1, Integer::sum);
            }
        }
        return mapa;
    }

    public Optional<Frequencia> frequencia(CategoriaDeDados categoria) {
        TreeMap<YearMonth, Integer> meses = porMes(categoria);
        if (meses.isEmpty()) {
            return Optional.empty();
        }
        int total = meses.values().stream().mapToInt(Integer::intValue).sum();
        int noPeriodo = (int) (meses.firstKey().until(meses.lastKey(), java.time.temporal.ChronoUnit.MONTHS) + 1);
        var maisAtivo = meses.entrySet().stream().max(Map.Entry.<YearMonth, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).orElseThrow();
        Optional<ResumoDaImportacao> i = ultima();
        long[] instantes = conteudo.instantes(i.orElseThrow().id(), categoria.name());
        return Optional.of(new Frequencia(total, noPeriodo, meses.size(), (double) total / noPeriodo, maisAtivo.getKey(), maisAtivo.getValue(),
                Instant.ofEpochSecond(instantes[0]), Instant.ofEpochSecond(instantes[instantes.length - 1])));
    }
}
