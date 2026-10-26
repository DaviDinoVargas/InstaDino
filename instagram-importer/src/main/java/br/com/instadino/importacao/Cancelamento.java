package br.com.instadino.importacao;

import java.util.concurrent.atomic.AtomicBoolean;

/** Sinal cooperativo de cancelamento, verificado entre entradas e a cada bloco de registros. */
public final class Cancelamento {

    public static final Cancelamento NUNCA = new Cancelamento();

    private final AtomicBoolean cancelado = new AtomicBoolean();

    public void cancelar() {
        cancelado.set(true);
    }

    public boolean cancelado() {
        return cancelado.get();
    }

    void verificar() {
        if (cancelado.get()) {
            throw new ImportacaoRejeitadaException(ImportacaoRejeitadaException.Motivo.CANCELADA);
        }
    }
}
