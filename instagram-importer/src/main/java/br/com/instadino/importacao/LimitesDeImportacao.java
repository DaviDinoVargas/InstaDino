package br.com.instadino.importacao;

import java.time.Duration;

/**
 * Limites de segurança do importador. Todos são configuráveis; os padrões são conservadores e documentados
 * em docs/IMPORT_SECURITY.md.
 *
 * @param tamanhoMaximoDoZip bytes do arquivo ZIP em disco
 * @param entradasMaximas número de entradas no ZIP
 * @param tamanhoDeclaradoTotalMaximo soma dos tamanhos descompactados declarados no diretório central
 * @param tamanhoMaximoPorEntradaLida bytes que podem ser descompactados de uma entrada JSON/HTML (contagem real)
 * @param bytesLidosTotalMaximo bytes descompactados no total, somando todas as entradas lidas
 * @param profundidadeMaxima níveis de diretório de uma entrada
 * @param comprimentoMaximoDoNome caracteres do caminho de uma entrada
 * @param razaoMaximaDeCompressao tamanho descompactado / comprimido tolerado em entradas grandes
 * @param limiarDaRazao tamanho mínimo da entrada para a razão ser avaliada
 * @param tempoMaximo duração máxima da importação
 */
public record LimitesDeImportacao(
        long tamanhoMaximoDoZip,
        int entradasMaximas,
        long tamanhoDeclaradoTotalMaximo,
        long tamanhoMaximoPorEntradaLida,
        long bytesLidosTotalMaximo,
        int profundidadeMaxima,
        int comprimentoMaximoDoNome,
        long razaoMaximaDeCompressao,
        long limiarDaRazao,
        Duration tempoMaximo) {

    private static final long GIB = 1024L * 1024 * 1024;
    private static final long MIB = 1024L * 1024;

    public LimitesDeImportacao {
        if (tamanhoMaximoDoZip <= 0 || entradasMaximas <= 0 || tamanhoDeclaradoTotalMaximo <= 0
                || tamanhoMaximoPorEntradaLida <= 0 || bytesLidosTotalMaximo <= 0 || profundidadeMaxima <= 0
                || comprimentoMaximoDoNome <= 0 || razaoMaximaDeCompressao <= 0 || limiarDaRazao < 0
                || tempoMaximo == null || tempoMaximo.isNegative() || tempoMaximo.isZero()) {
            throw new IllegalArgumentException("Limites de importação inválidos.");
        }
    }

    public static LimitesDeImportacao padrao() {
        return new LimitesDeImportacao(8 * GIB, 1_000_000, 64 * GIB, 256 * MIB, GIB, 24, 512, 200, MIB, Duration.ofMinutes(10));
    }

    public LimitesDeImportacao comTamanhoMaximoDoZip(long valor) {
        return new LimitesDeImportacao(valor, entradasMaximas, tamanhoDeclaradoTotalMaximo, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, tempoMaximo);
    }

    public LimitesDeImportacao comEntradasMaximas(int valor) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, valor, tamanhoDeclaradoTotalMaximo, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, tempoMaximo);
    }

    public LimitesDeImportacao comTamanhoMaximoPorEntradaLida(long valor) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, entradasMaximas, tamanhoDeclaradoTotalMaximo, valor,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, tempoMaximo);
    }

    public LimitesDeImportacao comRazaoDeCompressao(long razao, long limiar) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, entradasMaximas, tamanhoDeclaradoTotalMaximo, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razao, limiar, tempoMaximo);
    }

    public LimitesDeImportacao comTamanhoDeclaradoTotalMaximo(long valor) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, entradasMaximas, valor, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, tempoMaximo);
    }

    public LimitesDeImportacao comProfundidadeMaxima(int valor) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, entradasMaximas, tamanhoDeclaradoTotalMaximo, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, valor, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, tempoMaximo);
    }

    public LimitesDeImportacao comTempoMaximo(Duration valor) {
        return new LimitesDeImportacao(tamanhoMaximoDoZip, entradasMaximas, tamanhoDeclaradoTotalMaximo, tamanhoMaximoPorEntradaLida,
                bytesLidosTotalMaximo, profundidadeMaxima, comprimentoMaximoDoNome, razaoMaximaDeCompressao, limiarDaRazao, valor);
    }
}
