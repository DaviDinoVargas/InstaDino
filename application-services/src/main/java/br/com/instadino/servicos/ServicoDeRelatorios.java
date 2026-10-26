package br.com.instadino.servicos;

import br.com.instadino.analise.relatorio.DocumentoPdf;
import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.analise.social.ComparacaoDeImportacoes;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Anotacao;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Etiqueta;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Relatórios em PDF (Premium). Por padrão o relatório traz apenas números: listas de contas, etiquetas e notas privadas
 * só entram se o usuário marcar a opção, porque contêm dados de terceiros ou pessoais. Listas são limitadas em tamanho
 * (o CSV tem a lista completa). Nada é enviado para fora do computador.
 */
public final class ServicoDeRelatorios {

    public static final int MAXIMO_DE_CONTAS_POR_LISTA = 1000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"));

    /** O que incluir no relatório. O padrão não traz nenhum dado de terceiros nem notas. */
    public record Opcoes(boolean indicadores, boolean qualidadeDaImportacao, boolean evolucao, boolean listaDeNaoSeguemDeVolta,
                         boolean listaDeMutuos, boolean listaDeVoceNaoSegue, boolean favoritasEtiquetasENotas) {

        public static Opcoes padrao() {
            return new Opcoes(true, true, false, false, false, false, false);
        }

        public boolean temAlgumaLista() {
            return listaDeNaoSeguemDeVolta || listaDeMutuos || listaDeVoceNaoSegue;
        }
    }

    private final PoliticaDeAcesso politica;
    private final ServicoDeRelacionamentos relacionamentos;
    private final ServicoDeHistorico historico;
    private final ServicoDeAnotacoes anotacoes;
    private final ServicoDePerfis perfis;
    private final RegistroDeAuditoriaLocal auditoria;

    ServicoDeRelatorios(PoliticaDeAcesso politica, ServicoDeRelacionamentos relacionamentos, ServicoDeHistorico historico,
                        ServicoDeAnotacoes anotacoes, ServicoDePerfis perfis, RegistroDeAuditoriaLocal auditoria) {
        this.politica = politica;
        this.relacionamentos = relacionamentos;
        this.historico = historico;
        this.anotacoes = anotacoes;
        this.perfis = perfis;
        this.auditoria = auditoria;
    }

    /** Relatório da importação mais recente do perfil ativo. */
    public void gerarRelatorioDaImportacao(OutputStream saida, Opcoes opcoes) throws IOException {
        politica.exigir(Funcionalidade.RELATORIO_PDF);
        if (opcoes.evolucao()) {
            politica.exigir(Funcionalidade.GRAFICOS_HISTORICOS);
        }
        ResumoDoPainel painel = relacionamentos.painel().orElseThrow(() -> new IllegalStateException("Importe uma exportação antes de gerar o relatório."));
        ResumoDaImportacao importacao = painel.importacao();
        try (DocumentoPdf pdf = new DocumentoPdf("Relatório InstaDino", "InstaDino • Relatório da importação")) {
            cabecalho(pdf, "Relatório da importação", importacao);
            if (opcoes.indicadores()) {
                indicadores(pdf, painel);
            }
            if (opcoes.qualidadeDaImportacao()) {
                qualidade(pdf, importacao);
            }
            if (opcoes.evolucao()) {
                evolucao(pdf);
            }
            if (opcoes.listaDeNaoSeguemDeVolta()) {
                lista(pdf, "Contas que você segue e que não aparecem como seguidores", VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA, true);
            }
            if (opcoes.listaDeVoceNaoSegue()) {
                lista(pdf, "Seguidores que você não segue", VisaoDeRelacionamento.VOCE_NAO_SEGUE, false);
            }
            if (opcoes.listaDeMutuos()) {
                lista(pdf, "Relações mútuas", VisaoDeRelacionamento.MUTUOS, false);
            }
            if (opcoes.favoritasEtiquetasENotas()) {
                anotacoesDoPerfil(pdf);
            }
            pdf.secao("Como interpretar").paragrafo(AnaliseDeSeguidores.LIMITACAO);
            pdf.salvar(saida);
        }
        auditoria.registrar("RELATORIO_PDF_GERADO", "importacao");
    }

    /** Relatório comparativo entre duas importações do perfil ativo. */
    public void gerarRelatorioComparativo(OutputStream saida, String idAnterior, String idRecente, boolean incluirListas) throws IOException {
        politica.exigir(Funcionalidade.RELATORIOS_COMPARATIVOS);
        politica.exigir(Funcionalidade.COMPARACAO_ENTRE_PERIODOS);
        var seguidores = historico.comparar(idAnterior, idRecente, TipoDeRelacionamento.SEGUIDOR);
        var seguindo = historico.comparar(idAnterior, idRecente, TipoDeRelacionamento.SEGUINDO);
        ResumoDaImportacao a = historico.qualidade(idAnterior).resumo();
        ResumoDaImportacao b = historico.qualidade(idRecente).resumo();
        ResumoDaImportacao anterior = a.importadaEm().isAfter(b.importadaEm()) ? b : a;
        ResumoDaImportacao recente = anterior == a ? b : a;
        try (DocumentoPdf pdf = new DocumentoPdf("Relatório comparativo InstaDino", "InstaDino • Relatório comparativo")) {
            pdf.titulo("Relatório comparativo").nota("Perfil: " + perfis.ativo().nome() + "  •  Gerado em " + DATA_E_HORA.format(java.time.LocalDateTime.now()));
            pdf.secao("Períodos comparados");
            pdf.tabela(List.of("", "Importação anterior", "Importação mais recente"),
                    List.of(List.of("Data", data(anterior), data(recente)),
                            List.of("Registros válidos", String.valueOf(anterior.registrosValidos()), String.valueOf(recente.registrosValidos())),
                            List.of("Leitor (versão)", anterior.versaoDoParser(), recente.versaoDoParser())),
                    new float[] {2, 3, 3});
            if (!seguidores.confiavel() || !seguindo.confiavel()) {
                pdf.paragrafo("ATENÇÃO: uma ou mais listas estão ausentes ou incompletas em alguma das importações. As diferenças abaixo "
                        + "podem refletir o arquivo, e não mudanças reais.");
            }
            pdf.secao("Resumo das diferenças observadas");
            pdf.tabela(List.of("Lista", "Novas", "Ausentes agora", "Mantidas"),
                    List.of(List.of("Seguidores", String.valueOf(seguidores.novas().size()), String.valueOf(seguidores.ausentes().size()), String.valueOf(seguidores.mantidas().size())),
                            List.of("Contas seguidas", String.valueOf(seguindo.novas().size()), String.valueOf(seguindo.ausentes().size()), String.valueOf(seguindo.mantidas().size()))),
                    new float[] {3, 2, 2, 2});
            if (incluirListas) {
                listaNomes(pdf, "Seguidores novos", seguidores.novas());
                listaNomes(pdf, "Seguidores ausentes na exportação mais recente", seguidores.ausentes());
                listaNomes(pdf, "Contas seguidas novas", seguindo.novas());
                listaNomes(pdf, "Contas seguidas ausentes na exportação mais recente", seguindo.ausentes());
            } else {
                pdf.nota("As listas de contas não foram incluídas (opção desmarcada). Marque \"incluir listas de contas\" para vê-las.");
            }
            pdf.secao("Como interpretar").paragrafo(ComparacaoDeImportacoes.LIMITACAO);
            pdf.salvar(saida);
        }
        auditoria.registrar("RELATORIO_PDF_GERADO", "comparativo");
    }

    // ---- seções ----

    private void cabecalho(DocumentoPdf pdf, String titulo, ResumoDaImportacao importacao) {
        pdf.titulo(titulo).nota("Perfil: " + perfis.ativo().nome() + "  •  Importação de " + data(importacao) + "  •  Gerado em "
                + DATA_E_HORA.format(java.time.LocalDateTime.now()) + "  •  Processado localmente; nada foi enviado pela internet.");
    }

    private void indicadores(DocumentoPdf pdf, ResumoDoPainel painel) {
        pdf.secao("Indicadores");
        pdf.tabela(List.of("Indicador", "Valor"), List.of(
                List.of("Seguidores", numero(painel.seguidores())),
                List.of("Contas seguidas", numero(painel.seguindo())),
                List.of("Relações mútuas", numero(painel.mutuos())),
                List.of("Contas seguidas que não aparecem como seguidores", numero(painel.naoSeguemDeVolta())),
                List.of("Seguidores que você não segue", numero(painel.voceNaoSegue())),
                List.of("Proporção seguidores / seguidos", painel.proporcao() == null ? "indefinida" : String.format(Locale.forLanguageTag("pt-BR"), "%.2f", painel.proporcao()))),
                new float[] {4, 1.5f});
        painel.avisosDeCobertura().forEach(a -> pdf.paragrafo("ATENÇÃO: " + a));
        if (!painel.comparacaoDisponivel()) {
            pdf.nota("Uma das listas (seguidores ou seguidos) não estava na exportação; os indicadores que dependem dela não são confiáveis.");
        }
    }

    private void qualidade(DocumentoPdf pdf, ResumoDaImportacao importacao) {
        var q = historico.qualidade(importacao.id());
        pdf.secao("Qualidade da importação");
        pdf.tabela(List.of("Item", "Valor"), List.of(
                List.of("Data da importação", data(importacao)),
                List.of("Versão do leitor de exportações", importacao.versaoDoParser()),
                List.of("Registros válidos", numero(importacao.registrosValidos())),
                List.of("Registros descartados (sem nome de usuário válido)", numero(importacao.registrosInvalidos())),
                List.of("Avisos emitidos na importação", numero(q.avisos()))), new float[] {4, 2});
        List<List<String>> linhas = new ArrayList<>();
        importacao.categorias().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> linhas.add(List.of(
                rotulo(e.getKey()), String.valueOf(e.getValue()[0]), q.categoriasIncompletas().contains(e.getKey()) ? "possivelmente incompleta" : "ok")));
        if (!linhas.isEmpty()) {
            pdf.paragrafo("Categorias encontradas na exportação:");
            pdf.tabela(List.of("Categoria", "Arquivos", "Situação"), linhas, new float[] {4, 1.5f, 3});
        }
    }

    private void evolucao(DocumentoPdf pdf) {
        List<ServicoDeHistorico.PontoDaSerie> serie = historico.serieHistorica();
        List<ServicoDeHistorico.PontoDaSerie> completos = serie.stream().filter(p -> p.seguidores() != null && p.seguindo() != null).toList();
        pdf.secao("Evolução entre importações");
        if (completos.size() < 2) {
            pdf.paragrafo("São necessárias ao menos duas importações com as listas de seguidores e de seguidos para mostrar a evolução.");
            return;
        }
        Map<String, List<Integer>> series = new LinkedHashMap<>();
        series.put("Seguidores", completos.stream().map(ServicoDeHistorico.PontoDaSerie::seguidores).toList());
        series.put("Seguindo", completos.stream().map(ServicoDeHistorico.PontoDaSerie::seguindo).toList());
        series.put("Mútuos", completos.stream().map(ServicoDeHistorico.PontoDaSerie::mutuos).toList());
        pdf.graficoDeLinhas("Seguidores, contas seguidas e mútuos por importação", completos.stream().map(p -> DATA.format(p.importadaEm().atZone(ZoneId.systemDefault()))).toList(), series);
        List<List<String>> linhas = new ArrayList<>();
        for (var p : completos) {
            linhas.add(List.of(DATA.format(p.importadaEm().atZone(ZoneId.systemDefault())), numero(p.seguidores()), numero(p.seguindo()), numero(p.mutuos())));
        }
        pdf.tabela(List.of("Data", "Seguidores", "Seguindo", "Mútuos"), linhas, new float[] {2, 2, 2, 2});
        if (completos.size() < serie.size()) {
            pdf.nota((serie.size() - completos.size()) + " importação(ões) sem uma das listas foram omitidas do gráfico.");
        }
        pdf.nota("Os pontos são observações nas datas das importações. Não indicam o que aconteceu entre elas.");
    }

    private void lista(DocumentoPdf pdf, String titulo, VisaoDeRelacionamento visao, boolean avisarLimitacao) {
        List<String> nomes = relacionamentos.todas(visao).stream().map(ContaListada::usuario).toList();
        pdf.secao(titulo + " (" + numero(nomes.size()) + ")");
        if (nomes.isEmpty()) {
            pdf.paragrafo("Nenhuma conta nesta lista.");
            return;
        }
        pdf.listaEmColunas(nomes, MAXIMO_DE_CONTAS_POR_LISTA, 3);
    }

    private void listaNomes(DocumentoPdf pdf, String titulo, Set<NomeDeUsuario> conjunto) {
        List<String> nomes = conjunto.stream().map(NomeDeUsuario::valor).sorted().toList();
        pdf.secao(titulo + " (" + numero(nomes.size()) + ")");
        if (nomes.isEmpty()) {
            pdf.paragrafo("Nenhuma conta.");
            return;
        }
        pdf.listaEmColunas(nomes, MAXIMO_DE_CONTAS_POR_LISTA, 3);
    }

    private void anotacoesDoPerfil(DocumentoPdf pdf) {
        Map<String, Anotacao> todas = anotacoes.doPerfilAtivo();
        pdf.secao("Favoritas, etiquetas e notas privadas");
        pdf.nota("Atenção: esta seção contém anotações pessoais suas. Compartilhe este arquivo com cuidado.");
        if (todas.isEmpty()) {
            pdf.paragrafo("Nenhuma anotação neste perfil.");
            return;
        }
        List<List<String>> linhas = todas.values().stream().sorted(Comparator.comparing(Anotacao::usuario)).limit(MAXIMO_DE_CONTAS_POR_LISTA)
                .map(a -> List.of(a.usuario(), a.favorito() ? "sim" : "",
                        a.etiquetas().stream().map(Etiqueta::nome).collect(Collectors.joining(", ")),
                        a.nota() == null ? "" : (a.nota().length() > 300 ? a.nota().substring(0, 300) + "..." : a.nota())))
                .toList();
        pdf.tabela(List.of("Conta", "Favorita", "Etiquetas", "Nota"), linhas, new float[] {2.2f, 1.2f, 2.5f, 5});
    }

    // ---- formatação ----

    private static String data(ResumoDaImportacao r) {
        return DATA_E_HORA.format(r.importadaEm().atZone(ZoneId.systemDefault()));
    }

    private static String numero(long n) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,d", n);
    }

    private static String rotulo(String categoria) {
        try {
            return br.com.instadino.importacao.CategoriaDeDados.valueOf(categoria).rotulo();
        } catch (IllegalArgumentException e) {
            return categoria;
        }
    }
}
