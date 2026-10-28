package br.com.instadino.analise.relatorio;

import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Montagem de relatórios em PDF (A4) com paginação automática, quebra de linhas, tabelas, listas em colunas, gráfico
 * de linhas e rodapé numerado. Usa apenas as fontes padrão do PDF (Helvetica, codificação WinAnsi), que cobrem o
 * português; qualquer caractere fora dessa codificação é substituído por "?" em vez de falhar. O documento gerado não
 * contém JavaScript, ações de abertura, formulários nem links.
 */
public final class DocumentoPdf implements AutoCloseable {

    public static final int MAXIMO_DE_PAGINAS = 300;

    static {
        // O PDFBox consulta o mapeador de fontes ao criar uma fonte padrão e, por omissão, varre TODAS as fontes instaladas
        // no sistema (no Windows isso levou mais de um minuto na primeira geração). As fontes padrão do PDF (Helvetica) são
        // apenas referenciadas pelo nome e o visualizador fornece o desenho; as métricas vêm das tabelas AFM do próprio
        // PDFBox. Um mapeador sem varredura evita a espera e deixa o resultado idêntico em qualquer máquina.
        org.apache.pdfbox.pdmodel.font.FontMappers.set(new MapeadorSemFontesDoSistema());
    }

    private static final float MARGEM = 54f;
    private static final float TAMANHO_CORPO = 10f;
    private static final float ENTRELINHA = 1.35f;

    private final PDDocument documento = new PDDocument();
    private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<Integer, Boolean> codificaveis = new HashMap<>();
    private final String rodape;
    private PDPageContentStream fluxo;
    private float y;

    public DocumentoPdf(String titulo, String rodape) {
        this.rodape = limpar(rodape);
        var info = documento.getDocumentInformation();
        info.setTitle(limpar(titulo));
        info.setAuthor("InstaDino");
        info.setCreator("InstaDino");
        info.setProducer("InstaDino");
        info.setCreationDate(Calendar.getInstance());
    }

    // ---- elementos ----

    public DocumentoPdf titulo(String texto) {
        bloco(texto, negrito, 20f, 0x1F1B33, 6f);
        return this;
    }

    public DocumentoPdf secao(String texto) {
        garantirEspaco(48);
        y -= 8f;
        bloco(texto, negrito, 13f, 0x5B3FD1, 4f);
        return this;
    }

    public DocumentoPdf paragrafo(String texto) {
        bloco(texto, normal, TAMANHO_CORPO, 0x222222, 4f);
        return this;
    }

    public DocumentoPdf nota(String texto) {
        bloco(texto, normal, 8.5f, 0x666666, 4f);
        return this;
    }

    public DocumentoPdf marcador(String texto) {
        bloco("•  " + texto, normal, TAMANHO_CORPO, 0x222222, 2f);
        return this;
    }

    public DocumentoPdf espaco(float pontos) {
        y -= pontos;
        return this;
    }

    /** Tabela simples. {@code larguras} são proporções (a soma não precisa ser 1). */
    public DocumentoPdf tabela(List<String> cabecalho, List<List<String>> linhas, float[] larguras) {
        float util = PDRectangle.A4.getWidth() - 2 * MARGEM;
        float soma = 0;
        for (float l : larguras) {
            soma += l;
        }
        float[] colunas = new float[larguras.length];
        for (int i = 0; i < larguras.length; i++) {
            colunas[i] = util * larguras[i] / soma;
        }
        linhaDeTabela(cabecalho, colunas, negrito, 0x1F1B33, true);
        for (List<String> linha : linhas) {
            linhaDeTabela(linha, colunas, normal, 0x222222, false);
        }
        y -= 6f;
        return this;
    }

    /**
     * Lista de textos em colunas (por exemplo, nomes de usuário). Mostra no máximo {@code maximo} itens e informa
     * quantos ficaram de fora, para que o relatório tenha tamanho limitado.
     */
    public DocumentoPdf listaEmColunas(List<String> itens, int maximo, int colunas) {
        int mostrar = Math.min(itens.size(), Math.max(maximo, 0));
        float util = PDRectangle.A4.getWidth() - 2 * MARGEM;
        float largura = util / colunas;
        float altura = TAMANHO_CORPO * ENTRELINHA;
        int linhas = (mostrar + colunas - 1) / colunas;
        for (int l = 0; l < linhas; l++) {
            garantirEspaco(altura);
            for (int c = 0; c < colunas; c++) {
                int i = l * colunas + c;
                if (i >= mostrar) {
                    break;
                }
                texto(ajustar(limpar(itens.get(i)), normal, TAMANHO_CORPO, largura - 6f), MARGEM + c * largura, y - TAMANHO_CORPO, normal, TAMANHO_CORPO, 0x222222);
            }
            y -= altura;
        }
        if (itens.size() > mostrar) {
            nota("… e mais " + (itens.size() - mostrar) + " não mostradas neste relatório. Use a exportação em CSV para a lista completa.");
        }
        y -= 4f;
        return this;
    }

    /** Gráfico de linhas simples: uma linha por série, eixo X com rótulos (no máximo seis são escritos). */
    public DocumentoPdf graficoDeLinhas(String titulo, List<String> rotulosX, Map<String, List<Integer>> series) {
        float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;
        float altura = 170f;
        garantirEspaco(altura + 60f);
        bloco(titulo, negrito, 10.5f, 0x222222, 6f);
        float esquerda = MARGEM + 38f;
        float direita = MARGEM + largura - 8f;
        float base = y - altura + 22f;
        float topo = y - 6f;
        int maximo = 1;
        for (List<Integer> valores : series.values()) {
            for (int v : valores) {
                maximo = Math.max(maximo, v);
            }
        }
        int teto = arredondarParaCima(maximo);
        try {
            fluxo.setStrokingColor(0.8f, 0.8f, 0.85f);
            fluxo.setLineWidth(0.5f);
            for (int g = 0; g <= 4; g++) {
                float gy = base + (topo - base) * g / 4f;
                fluxo.moveTo(esquerda, gy);
                fluxo.lineTo(direita, gy);
                fluxo.stroke();
                texto(String.valueOf(teto * g / 4), MARGEM, gy - 3f, normal, 8f, 0x666666);
            }
            int pontos = rotulosX.size();
            float passo = pontos <= 1 ? 0 : (direita - esquerda) / (pontos - 1);
            int[] cores = {0x8B5CF6, 0x2A9D8F, 0xE76F51, 0x457B9D};
            int indice = 0;
            float legendaX = esquerda;
            for (Map.Entry<String, List<Integer>> serie : series.entrySet()) {
                int cor = cores[indice % cores.length];
                setCorLinha(cor);
                fluxo.setLineWidth(1.6f);
                List<Integer> valores = serie.getValue();
                for (int i = 0; i < valores.size(); i++) {
                    float px = esquerda + passo * i;
                    float py = base + (topo - base) * valores.get(i) / (float) teto;
                    if (i == 0) {
                        fluxo.moveTo(px, py);
                    } else {
                        fluxo.lineTo(px, py);
                    }
                }
                fluxo.stroke();
                for (int i = 0; i < valores.size(); i++) {
                    float px = esquerda + passo * i;
                    float py = base + (topo - base) * valores.get(i) / (float) teto;
                    fluxo.addRect(px - 1.8f, py - 1.8f, 3.6f, 3.6f);
                    setCorPreenchimento(cor);
                    fluxo.fill();
                }
                fluxo.addRect(legendaX, base - 31f, 7f, 7f);
                setCorPreenchimento(cor);
                fluxo.fill();
                texto(limpar(serie.getKey()), legendaX + 11f, base - 30f, normal, 8.5f, 0x444444);
                legendaX += 110f;
                indice++;
            }
            int rotulos = Math.min(pontos, 6);
            for (int r = 0; r < rotulos; r++) {
                int i = rotulos == 1 ? 0 : Math.round(r * (pontos - 1f) / (rotulos - 1f));
                String t = limpar(rotulosX.get(i));
                float tx = esquerda + passo * i - larguraDoTexto(t, normal, 8f) / 2f;
                texto(t, Math.max(MARGEM, tx), base - 14f, normal, 8f, 0x666666);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao desenhar o gráfico.", e);
        }
        y = base - 42f;
        return this;
    }

    // ---- saída ----

    public void salvar(OutputStream saida) throws IOException {
        fecharFluxo();
        int total = documento.getNumberOfPages();
        if (total == 0) {
            documento.addPage(new PDPage(PDRectangle.A4));
            total = 1;
        }
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream f = new PDPageContentStream(documento, documento.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                String linha = rodape + "  •  página " + (i + 1) + " de " + total;
                f.beginText();
                f.setFont(normal, 8f);
                f.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                f.newLineAtOffset(MARGEM, 30f);
                f.showText(linha);
                f.endText();
            }
        }
        documento.save(saida);
    }

    public int paginas() {
        return documento.getNumberOfPages() + (fluxo != null && documento.getNumberOfPages() == 0 ? 1 : 0);
    }

    @Override
    public void close() throws IOException {
        fecharFluxo();
        documento.close();
    }

    // ---- internos ----

    private void bloco(String texto, PDType1Font fonte, float tamanho, int cor, float depois) {
        float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;
        for (String linha : quebrar(limpar(texto), fonte, tamanho, largura)) {
            garantirEspaco(tamanho * ENTRELINHA);
            texto(linha, MARGEM, y - tamanho, fonte, tamanho, cor);
            y -= tamanho * ENTRELINHA;
        }
        y -= depois;
    }

    private void linhaDeTabela(List<String> celulas, float[] colunas, PDType1Font fonte, int cor, boolean cabecalho) {
        List<List<String>> quebradas = new ArrayList<>();
        int maiorAltura = 1;
        for (int c = 0; c < colunas.length; c++) {
            String t = c < celulas.size() ? celulas.get(c) : "";
            List<String> q = quebrar(limpar(t), fonte, 9.5f, colunas[c] - 8f);
            quebradas.add(q);
            maiorAltura = Math.max(maiorAltura, q.size());
        }
        float altura = maiorAltura * 9.5f * ENTRELINHA + 6f;
        garantirEspaco(altura);
        try {
            if (cabecalho) {
                fluxo.setNonStrokingColor(0.93f, 0.92f, 0.98f);
                fluxo.addRect(MARGEM, y - altura, PDRectangle.A4.getWidth() - 2 * MARGEM, altura);
                fluxo.fill();
            }
            fluxo.setStrokingColor(0.85f, 0.85f, 0.88f);
            fluxo.setLineWidth(0.4f);
            fluxo.moveTo(MARGEM, y - altura);
            fluxo.lineTo(PDRectangle.A4.getWidth() - MARGEM, y - altura);
            fluxo.stroke();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao desenhar a tabela.", e);
        }
        float x = MARGEM;
        for (int c = 0; c < colunas.length; c++) {
            float ty = y - 3f - 9.5f;
            for (String linha : quebradas.get(c)) {
                texto(linha, x + 4f, ty, fonte, 9.5f, cor);
                ty -= 9.5f * ENTRELINHA;
            }
            x += colunas[c];
        }
        y -= altura;
    }

    private void garantirEspaco(float necessario) {
        if (fluxo == null || y - necessario < MARGEM + 14f) {
            novaPagina();
        }
    }

    private void novaPagina() {
        try {
            fecharFluxo();
            if (documento.getNumberOfPages() >= MAXIMO_DE_PAGINAS) {
                throw new IllegalStateException("O relatório excede o limite de " + MAXIMO_DE_PAGINAS + " páginas.");
            }
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            fluxo = new PDPageContentStream(documento, pagina);
            y = PDRectangle.A4.getHeight() - MARGEM;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao criar página.", e);
        }
    }

    private void fecharFluxo() {
        if (fluxo != null) {
            try {
                fluxo.close();
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao concluir a página.", e);
            }
            fluxo = null;
        }
    }

    private void texto(String texto, float x, float baseline, PDType1Font fonte, float tamanho, int cor) {
        try {
            fluxo.beginText();
            fluxo.setFont(fonte, tamanho);
            setCorTexto(cor);
            fluxo.newLineAtOffset(x, baseline);
            fluxo.showText(limpar(texto));
            fluxo.endText();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escrever texto.", e);
        }
    }

    private void setCorTexto(int rgb) throws IOException {
        fluxo.setNonStrokingColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private void setCorLinha(int rgb) throws IOException {
        fluxo.setStrokingColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private void setCorPreenchimento(int rgb) throws IOException {
        setCorTexto(rgb);
    }

    /** Teto "redondo" do eixo Y (múltiplo de 4 divisões com passo 1, 2, 2,5 ou 5 vezes uma potência de 10). */
    static int arredondarParaCima(int maximo) {
        if (maximo <= 4) {
            return 4;
        }
        double passo = maximo / 4.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(passo)));
        double normalizado = passo / magnitude;
        double bonito = normalizado <= 1 ? 1 : normalizado <= 2 ? 2 : normalizado <= 2.5 ? 2.5 : normalizado <= 5 ? 5 : 10;
        return (int) Math.round(bonito * magnitude * 4);
    }

    private float larguraDoTexto(String t, PDType1Font fonte, float tamanho) {
        try {
            return fonte.getStringWidth(t) / 1000f * tamanho;
        } catch (IOException | IllegalArgumentException e) {
            return t.length() * tamanho * 0.5f;
        }
    }

    private List<String> quebrar(String texto, PDType1Font fonte, float tamanho, float largura) {
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : texto.split("\n", -1)) {
            StringBuilder atual = new StringBuilder();
            for (String palavra : paragrafo.split(" ")) {
                String candidata = atual.length() == 0 ? palavra : atual + " " + palavra;
                if (larguraDoTexto(candidata, fonte, tamanho) <= largura) {
                    atual = new StringBuilder(candidata);
                    continue;
                }
                if (atual.length() > 0) {
                    linhas.add(atual.toString());
                    atual = new StringBuilder();
                }
                String resto = palavra;
                while (larguraDoTexto(resto, fonte, tamanho) > largura && resto.length() > 1) {
                    int corte = resto.length() - 1;
                    while (corte > 1 && larguraDoTexto(resto.substring(0, corte), fonte, tamanho) > largura) {
                        corte--;
                    }
                    linhas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                atual = new StringBuilder(resto);
            }
            linhas.add(atual.toString());
        }
        return linhas;
    }

    private String ajustar(String texto, PDType1Font fonte, float tamanho, float largura) {
        if (larguraDoTexto(texto, fonte, tamanho) <= largura) {
            return texto;
        }
        String t = texto;
        while (t.length() > 1 && larguraDoTexto(t + "...", fonte, tamanho) > largura) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    /** Normaliza e remove tudo que a fonte padrão não consegue codificar, em vez de falhar. */
    String limpar(String bruto) {
        if (bruto == null) {
            return "";
        }
        String n = Normalizer.normalize(bruto, Normalizer.Form.NFC);
        StringBuilder s = new StringBuilder(n.length());
        n.codePoints().forEach(cp -> {
            if (cp == '\n') {
                s.append('\n');
            } else if (cp == '\t') {
                s.append("    ");
            } else if (cp < 0x20 || (cp >= 0x7F && cp < 0xA0)) {
                s.append(' ');
            } else if (Character.getType(cp) == Character.FORMAT || cp == 0x2028 || cp == 0x2029) {
                s.append(' ');
            } else if (codificavel(cp)) {
                s.appendCodePoint(cp);
            } else {
                s.append('?');
            }
        });
        return s.toString();
    }

    private boolean codificavel(int cp) {
        return codificaveis.computeIfAbsent(cp, k -> {
            try {
                normal.encode(new String(Character.toChars(k)));
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        });
    }

    /** Mapeador que nunca consulta o sistema; a fonte devolvida só satisfaz o construtor (nada é desenhado pelo PDFBox). */
    private static final class MapeadorSemFontesDoSistema implements org.apache.pdfbox.pdmodel.font.FontMapper {

        @Override
        public org.apache.pdfbox.pdmodel.font.FontMapping<org.apache.fontbox.ttf.TrueTypeFont> getTrueTypeFont(
                String nome, org.apache.pdfbox.pdmodel.font.PDFontDescriptor descritor) {
            return new org.apache.pdfbox.pdmodel.font.FontMapping<>(null, true);
        }

        @Override
        public org.apache.pdfbox.pdmodel.font.FontMapping<org.apache.fontbox.FontBoxFont> getFontBoxFont(
                String nome, org.apache.pdfbox.pdmodel.font.PDFontDescriptor descritor) {
            return new org.apache.pdfbox.pdmodel.font.FontMapping<>(new FontePadrao(nome), false);
        }

        @Override
        public org.apache.pdfbox.pdmodel.font.CIDFontMapping getCIDFont(String nome, org.apache.pdfbox.pdmodel.font.PDFontDescriptor descritor,
                                                                        org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo info) {
            return new org.apache.pdfbox.pdmodel.font.CIDFontMapping(null, null, true);
        }
    }

    private static final class FontePadrao implements org.apache.fontbox.FontBoxFont {
        private final String nome;

        FontePadrao(String nome) {
            this.nome = nome;
        }

        @Override
        public String getName() {
            return nome;
        }

        @Override
        public org.apache.fontbox.util.BoundingBox getFontBBox() {
            return new org.apache.fontbox.util.BoundingBox(-166f, -225f, 1000f, 931f); // caixa da Helvetica (AFM)
        }

        @Override
        public List<Number> getFontMatrix() {
            return List.of(0.001f, 0f, 0f, 0.001f, 0f, 0f);
        }

        @Override
        public java.awt.geom.GeneralPath getPath(String nomeDoGlifo) {
            return new java.awt.geom.GeneralPath();
        }

        @Override
        public float getWidth(String nomeDoGlifo) {
            return 500f;
        }

        @Override
        public boolean hasGlyph(String nomeDoGlifo) {
            return true;
        }
    }
}
