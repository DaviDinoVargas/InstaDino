package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatoriosPdfTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal ctx;

    private ContextoLocal abrir() {
        ctx = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        return ctx;
    }

    @AfterEach
    void fechar() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private String importar(ConstrutorDeExportacao e) {
        Path zip = e.escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
        var r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        String id = ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 10L);
        try {
            Thread.sleep(15);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return id;
    }

    private String texto(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d).replaceAll("\\s+", " ");
        }
    }

    private byte[] relatorio(ServicoDeRelatorios.Opcoes opcoes) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ctx.relatorios().gerarRelatorioDaImportacao(saida, opcoes);
        return saida.toByteArray();
    }

    // ---- política ----

    @Test
    void relatoriosSaoPremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        assertThatThrownBy(() -> relatorio(ServicoDeRelatorios.Opcoes.padrao())).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.relatorios().gerarRelatorioComparativo(new ByteArrayOutputStream(), "a", "b", false))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.historico().serieHistorica()).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void semImportacaoNaoHaRelatorio() {
        abrir();
        assertThatThrownBy(() -> relatorio(ServicoDeRelatorios.Opcoes.padrao())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Importe");
    }

    // ---- conteúdo e privacidade ----

    @Test
    void relatorioPadraoTrazNumerosEQualidadeMasNenhumaContaNemNota() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        ctx.anotacoes().favoritar("ana", true);
        ctx.anotacoes().definirNota("ana", "NOTA_PRIVADA_SECRETA_123");
        String t = texto(relatorio(ServicoDeRelatorios.Opcoes.padrao()));
        assertThat(t).contains("Relatório da importação").contains("Indicadores").contains("Seguidores").contains("Relações mútuas")
                .contains("Qualidade da importação").contains("Versão do leitor").contains("Como interpretar")
                .contains("Perfil principal");
        // dados de terceiros e anotações NÃO entram por padrão
        assertThat(t).doesNotContain("bruno").doesNotContain("carla").doesNotContain("diego").doesNotContain("NOTA_PRIVADA_SECRETA_123");
        assertThat(t).doesNotContain("e.zip"); // nome do arquivo de origem pode conter dados pessoais
    }

    @Test
    void listasSoEntramQuandoPedidasECarregamAsContas() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        var comListas = new ServicoDeRelatorios.Opcoes(true, false, false, true, true, true, false);
        String t = texto(relatorio(comListas));
        assertThat(t).contains("não aparecem como seguidores").contains("Relações mútuas (2)");
        var painel = ctx.relacionamentos().painel().orElseThrow();
        assertThat(painel.mutuos()).isEqualTo(2);
        for (var c : ctx.relacionamentos().todas(VisaoDeRelacionamento.MUTUOS)) {
            assertThat(t).contains(c.usuario());
        }
    }

    @Test
    void anotacoesSoEntramComAOpcaoExplicitaEComAviso() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        var etiqueta = ctx.anotacoes().criarEtiqueta("Família");
        ctx.anotacoes().favoritar("ana", true);
        ctx.anotacoes().aplicarEtiqueta("ana", etiqueta.id());
        ctx.anotacoes().definirNota("ana", "minha nota privada");
        String t = texto(relatorio(new ServicoDeRelatorios.Opcoes(true, false, false, false, false, false, true)));
        assertThat(t).contains("Favoritas, etiquetas e notas privadas").contains("minha nota privada").contains("Família").contains("anotações pessoais");
    }

    @Test
    void listasGrandesSaoLimitadasNoRelatorio() throws IOException {
        abrir();
        var c = new ConstrutorDeExportacao();
        String[] seguidores = new String[0];
        String[] muitos = new String[ServicoDeRelatorios.MAXIMO_DE_CONTAS_POR_LISTA + 250];
        for (int i = 0; i < muitos.length; i++) {
            muitos[i] = "conta" + i;
        }
        c.seguindo(muitos);
        c.seguidores(seguidores);
        importar(c);
        byte[] pdf = relatorio(new ServicoDeRelatorios.Opcoes(false, false, false, true, false, false, false));
        String t = texto(pdf);
        assertThat(t).contains("e mais 250 não mostradas");
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertThat(d.getNumberOfPages()).isLessThan(30);
        }
    }

    // ---- evolução / série histórica ----

    @Test
    void evolucaoComUmaImportacaoExplicaQueFaltamDados() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        String t = texto(relatorio(new ServicoDeRelatorios.Opcoes(false, false, true, false, false, false, false)));
        assertThat(t).contains("Evolução entre importações").contains("ao menos duas importações");
    }

    @Test
    void serieHistoricaTemOsTotaisPorImportacaoEOsMutuosCorretos() {
        abrir();
        importar(new ConstrutorDeExportacao().seguidores("ana", "bia", "caio").seguindo("ana", "caio", "dani"));
        importar(new ConstrutorDeExportacao().seguidores("ana", "bia", "caio", "eva").seguindo("ana", "eva"));
        var serie = ctx.historico().serieHistorica();
        assertThat(serie).hasSize(2);
        assertThat(serie.get(0).importadaEm()).isBefore(serie.get(1).importadaEm());
        assertThat(serie).extracting(ServicoDeHistorico.PontoDaSerie::seguidores).containsExactly(3, 4);
        assertThat(serie).extracting(ServicoDeHistorico.PontoDaSerie::seguindo).containsExactly(3, 2);
        assertThat(serie).extracting(ServicoDeHistorico.PontoDaSerie::mutuos).containsExactly(2, 2);
    }

    @Test
    void evolucaoComVariasImportacoesDesenhaOGraficoEATabela() throws IOException {
        abrir();
        importar(new ConstrutorDeExportacao().seguidores("ana").seguindo("ana"));
        importar(new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana"));
        importar(new ConstrutorDeExportacao().seguidores("ana", "bia", "caio").seguindo("ana", "bia"));
        String t = texto(relatorio(new ServicoDeRelatorios.Opcoes(false, false, true, false, false, false, false)));
        assertThat(t).contains("Seguidores, contas seguidas e mútuos por importação").contains("Data").contains("Mútuos")
                .contains("observações nas datas das importações");
    }

    @Test
    void importacaoSemUmaDasListasNaoEntraNaSerieComoZero() {
        abrir();
        importar(new ConstrutorDeExportacao().seguidores("ana", "bia"));
        var serie = ctx.historico().serieHistorica();
        assertThat(serie).hasSize(1);
        assertThat(serie.get(0).seguidores()).isEqualTo(2);
        assertThat(serie.get(0).seguindo()).isNull();
        assertThat(serie.get(0).mutuos()).isNull();
    }

    // ---- comparativo ----

    @Test
    void comparativoMostraDiferencasObservadasELimitacao() throws IOException {
        abrir();
        String antes = importar(new ConstrutorDeExportacao().seguidores("ana", "bia", "caio").seguindo("ana"));
        String depois = importar(new ConstrutorDeExportacao().seguidores("ana", "caio", "dani").seguindo("ana", "eva"));
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ctx.relatorios().gerarRelatorioComparativo(saida, antes, depois, false);
        String t = texto(saida.toByteArray());
        assertThat(t).contains("Relatório comparativo").contains("Períodos comparados").contains("Resumo das diferenças observadas")
                .contains("não eventos confirmados").contains("listas de contas não foram incluídas");
        assertThat(t).doesNotContain("bia").doesNotContain("dani");

        ByteArrayOutputStream comListas = new ByteArrayOutputStream();
        ctx.relatorios().gerarRelatorioComparativo(comListas, depois, antes, true);  // ordem trocada: o serviço ordena por data
        String t2 = texto(comListas.toByteArray());
        assertThat(t2).contains("Seguidores novos (1)").contains("dani").contains("Seguidores ausentes na exportação mais recente (1)").contains("bia");
    }

    @Test
    void comparativoAvisaQuandoUmaListaEstaAusente() throws IOException {
        abrir();
        String antes = importar(new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana"));
        String depois = importar(new ConstrutorDeExportacao().seguindo("ana"));
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ctx.relatorios().gerarRelatorioComparativo(saida, antes, depois, false);
        assertThat(texto(saida.toByteArray())).contains("ATENÇÃO").contains("ausentes ou incompletas");
    }

    @Test
    void naoEPossivelCompararImportacoesDeOutroPerfil() throws IOException {
        abrir();
        String minha = importar(ConstrutorDeExportacao.exemplo());
        ctx.servicoDePerfis().criar("Outro");
        String deOutro = importar(ConstrutorDeExportacao.exemplo());
        ctx.servicoDePerfis().selecionar(ctx.servicoDePerfis().listar().get(0).id());
        assertThatThrownBy(() -> ctx.relatorios().gerarRelatorioComparativo(new ByteArrayOutputStream(), minha, deOutro, false))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void geracaoDeRelatorioFicaNaAuditoriaSemDadosPessoais() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        relatorio(ServicoDeRelatorios.Opcoes.padrao());
        var eventos = ctx.dados().eventosDeSeguranca(10);
        assertThat(eventos).extracting(e -> e.tipo()).contains("RELATORIO_PDF_GERADO");
        assertThat(eventos).filteredOn(e -> e.tipo().equals("RELATORIO_PDF_GERADO")).allMatch(e -> "importacao".equals(e.detalhe()));
    }

    // ---- exportação feita com intervalo de datas ----

    @Test
    void avisoDeCoberturaParcialChegaAoPainelEAoRelatorio() throws IOException {
        abrir();
        long fim = 1_792_000_000L;
        long dia = 86_400L;
        var c = new ConstrutorDeExportacao();
        c.arquivo("connections/followers_and_following/followers_1.json",
                "[{\"title\":\"\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/a\",\"value\":\"a\",\"timestamp\":" + (fim - 360 * dia)
                        + "}]},{\"title\":\"\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/b\",\"value\":\"b\",\"timestamp\":" + fim + "}]}]");
        c.arquivo("connections/followers_and_following/following.json",
                "{\"relationships_following\":[{\"title\":\"a\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/a\",\"timestamp\":" + (fim - 2500 * dia)
                        + "}]},{\"title\":\"c\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/c\",\"timestamp\":" + fim + "}]}]}");
        importar(c);
        var painel = ctx.relacionamentos().painel().orElseThrow();
        assertThat(painel.avisosDeCobertura()).hasSize(1);
        assertThat(painel.avisosDeCobertura().get(0)).contains("possivelmente incompleta");
        String t = texto(relatorio(ServicoDeRelatorios.Opcoes.padrao()));
        assertThat(t).contains("ATENÇÃO").contains("cobre apenas");
    }
}
