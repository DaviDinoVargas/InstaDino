package br.com.instadino.importacao.seguranca;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Limita a quantidade REAL de bytes descompactados lidos de uma entrada, independentemente do tamanho declarado
 * no ZIP (que pode ser falso). Estoura com {@link LimiteExcedidoException} ao ultrapassar o máximo.
 */
public final class EntradaLimitada extends FilterInputStream {

    /** O limite de bytes foi ultrapassado durante a leitura. */
    public static final class LimiteExcedidoException extends IOException {
        private static final long serialVersionUID = 1L;

        public LimiteExcedidoException() {
            super("Limite de bytes excedido.");
        }
    }

    /** Chamada periodicamente para permitir cancelamento e controle de tempo durante leituras longas. */
    @FunctionalInterface
    public interface Vigia {
        void verificar();
    }

    private static final int INTERVALO_DE_VERIFICACAO = 64 * 1024;

    private final long limite;
    private final Vigia vigia;
    private long lidos;
    private long proximaVerificacao = INTERVALO_DE_VERIFICACAO;

    public EntradaLimitada(InputStream origem, long limite, Vigia vigia) {
        super(origem);
        this.limite = limite;
        this.vigia = vigia;
    }

    public long bytesLidos() {
        return lidos;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            contar(1);
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int deslocamento, int tamanho) throws IOException {
        int n = super.read(buffer, deslocamento, tamanho);
        if (n > 0) {
            contar(n);
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long pulados = super.skip(n);
        if (pulados > 0) {
            contar(pulados);
        }
        return pulados;
    }

    private void contar(long n) throws IOException {
        lidos += n;
        if (lidos > limite) {
            throw new LimiteExcedidoException();
        }
        if (lidos >= proximaVerificacao) {
            proximaVerificacao = lidos + INTERVALO_DE_VERIFICACAO;
            vigia.verificar();
        }
    }
}
