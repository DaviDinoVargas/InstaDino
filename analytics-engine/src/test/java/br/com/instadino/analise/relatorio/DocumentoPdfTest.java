package br.com.instadino.analise.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class DocumentoPdfTest {

    private static byte[] gerar(java.util.function.Consumer<DocumentoPdf> montagem) throws IOException {
        try (DocumentoPdf pdf = new DocumentoPdf("Relatório de teste", "InstaDino • teste")) {
            montagem.accept(pdf);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            pdf.salvar(saida);
            return saida.toByteArray();
        }
    }

    private static String textoDe(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    private static int paginasDe(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    @Test
    void preservaAcentuacaoDoPortuguesENumeraPaginasNoRodape() throws IOException {
        byte[] pdf = gerar(d -> d.titulo("Relatório de seguidores — ação e conclusão").secao("Indicadores")
                .paragrafo("Proporção, relações mútuas, não recíprocos, coração, maçã, ÀÉÎÕÜ, ñ.").marcador("Seguidores: 1.234"));
        String texto = textoDe(pdf);
        assertThat(texto).contains("Relatório de seguidores — ação e conclusão").contains("Proporção, relações mútuas, não recíprocos")
                .contains("ÀÉÎÕÜ").contains("Seguidores: 1.234").contains("página 1 de 1").contains("InstaDino • teste");
    }

    @Test
    void caracteresForaDaCodificacaoViramInterrogacaoSemFalhar() throws IOException {
        byte[] pdf = gerar(d -> d.paragrafo("emoji 😀 e japonês 日本語 e controle \u0007\u0000 fim"));
        String texto = textoDe(pdf);
        assertThat(texto).contains("emoji ? e japonês ??? e controle").contains("fim").doesNotContain("\u0007").doesNotContain("\u0000");
    }

    @Test
    void textoHostilNaoProduzConteudoAtivoNemLinks() throws IOException {
        byte[] pdf = gerar(d -> d.paragrafo("/JS (app.alert(1)) /Launch /URI (http://evil.example) ) ( ] > << endobj")
                .paragrafo("https://www.instagram.com/ana/").tabela(List.of("a"), List.of(List.of("<script>alert(1)</script>")), new float[] {1}));
        String bruto = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(bruto).doesNotContain("/JavaScript").doesNotContain("/OpenAction").doesNotContain("/AA ").doesNotContain("/Launch ")
                .doesNotContain("/AcroForm").doesNotContain("/EmbeddedFile");
        // o texto malicioso aparece como texto visível, mas nenhuma anotação de link é criada
        try (PDDocument d = Loader.loadPDF(pdf)) {
            for (var pagina : d.getPages()) {
                assertThat(pagina.getAnnotations()).isEmpty();
            }
            assertThat(d.getDocumentCatalog().getOpenAction()).isNull();
            assertThat(d.getDocumentCatalog().getNames()).isNull();
        }
        assertThat(textoDe(pdf)).contains("/JS (app.alert(1))").contains("<script>alert(1)</script>");
    }

    @Test
    void textosLongosQuebramEmLinhasEPaginasSemEstourarAMargem() throws IOException {
        String longo = "palavra ".repeat(900);
        String semEspacos = "x".repeat(400);
        byte[] pdf = gerar(d -> d.paragrafo(longo).paragrafo(semEspacos));
        assertThat(paginasDe(pdf)).isGreaterThan(1);
        String texto = textoDe(pdf);
        assertThat(texto).contains("palavra palavra");
        assertThat(texto.replace("\n", "").replace(" ", "")).contains("x".repeat(100));
    }

    @Test
    void tabelaComCelulasLongasEMuitasLinhasPagina() throws IOException {
        List<List<String>> linhas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            linhas.add(List.of("conta_" + i, "valor " + i, "observação bastante longa ".repeat(6)));
        }
        byte[] pdf = gerar(d -> d.tabela(List.of("Conta", "Valor", "Obs."), linhas, new float[] {2, 1, 4}));
        assertThat(paginasDe(pdf)).isGreaterThan(3);
        String texto = textoDe(pdf);
        assertThat(texto).contains("conta_0").contains("conta_199").contains("página 1 de");
    }

    @Test
    void listaEmColunasLimitaOTamanhoEInformaOResto() throws IOException {
        List<String> nomes = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            nomes.add("usuario" + i);
        }
        byte[] pdf = gerar(d -> d.listaEmColunas(nomes, 300, 4));
        String texto = textoDe(pdf);
        assertThat(texto).contains("usuario0").contains("usuario299").doesNotContain("usuario300 ").contains("e mais 4700 não mostradas");
        assertThat(paginasDe(pdf)).isLessThan(10);
    }

    @Test
    void graficoDeLinhasDesenhaSeriesERotulosSemFalharComUmUnicoPonto() throws IOException {
        Map<String, List<Integer>> series = new LinkedHashMap<>();
        series.put("Seguidores", List.of(100, 120, 150, 140));
        series.put("Seguindo", List.of(80, 85, 90, 95));
        byte[] pdf = gerar(d -> d.graficoDeLinhas("Evolução", List.of("01/01", "01/02", "01/03", "01/04"), series));
        assertThat(textoDe(pdf)).contains("Evolução").contains("Seguidores").contains("Seguindo").contains("01/01").contains("01/04");
        byte[] umPonto = gerar(d -> d.graficoDeLinhas("Um ponto", List.of("01/01"), Map.of("Seguidores", List.of(10))));
        assertThat(paginasDe(umPonto)).isEqualTo(1);
        byte[] zeros = gerar(d -> d.graficoDeLinhas("Zeros", List.of("a", "b"), Map.of("S", List.of(0, 0))));
        assertThat(paginasDe(zeros)).isEqualTo(1);
    }

    @Test
    void escalaDoGraficoUsaTetosRedondos() {
        assertThat(DocumentoPdf.arredondarParaCima(0)).isEqualTo(4);
        assertThat(DocumentoPdf.arredondarParaCima(4)).isEqualTo(4);
        assertThat(DocumentoPdf.arredondarParaCima(5)).isGreaterThanOrEqualTo(5);
        assertThat(DocumentoPdf.arredondarParaCima(1234)).isEqualTo(2000);
        assertThat(DocumentoPdf.arredondarParaCima(100)).isEqualTo(100);
        assertThat(DocumentoPdf.arredondarParaCima(101)).isEqualTo(200);
        for (int m : new int[] {1, 7, 99, 1000, 12345, 999_999, 5_000_000}) {
            int teto = DocumentoPdf.arredondarParaCima(m);
            assertThat(teto).as("teto de " + m).isGreaterThanOrEqualTo(m);
            assertThat(teto % 4).isZero();
        }
    }

    @Test
    void documentoVazioAindaGeraUmaPaginaValida() throws IOException {
        byte[] pdf = gerar(d -> { });
        assertThat(paginasDe(pdf)).isEqualTo(1);
    }

    @Test
    void metadadosIdentificamOProdutoSemDadosPessoais() throws IOException {
        byte[] pdf = gerar(d -> d.paragrafo("x"));
        try (PDDocument d = Loader.loadPDF(pdf)) {
            var info = d.getDocumentInformation();
            assertThat(info.getAuthor()).isEqualTo("InstaDino");
            assertThat(info.getCreator()).isEqualTo("InstaDino");
            assertThat(info.getTitle()).isEqualTo("Relatório de teste");
            assertThat(info.getKeywords()).isNull();
            assertThat(info.getSubject()).isNull();
        }
    }

    @Test
    void limiteDePaginasImpedeRelatoriosDesproporcionais() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gerar(d -> {
            String bloco = "linha\n".repeat(60);
            for (int i = 0; i < 1000; i++) {
                d.paragrafo(bloco);
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("limite");
    }

    @Test
    void volumeGrandeDeTextoEGeradoEmTempoRazoavel() throws IOException {
        long inicio = System.nanoTime();
        byte[] pdf = gerar(d -> {
            for (int i = 0; i < 2000; i++) {
                d.marcador("Linha " + i + " com acentuação e conteúdo ordinário");
            }
        });
        assertThat(paginasDe(pdf)).isGreaterThan(20);
        assertThat((System.nanoTime() - inicio) / 1_000_000_000L).isLessThan(20L);
    }
}
