package br.com.instadino.servicos;

import java.util.concurrent.atomic.AtomicLong;

/** Estado de execução local: o perfil atualmente selecionado. Não é persistido nem usado para decisões de acesso. */
public final class SessaoLocal {

    private final AtomicLong perfilAtivo;

    public SessaoLocal(long perfilInicial) {
        this.perfilAtivo = new AtomicLong(perfilInicial);
    }

    public long perfilAtivo() {
        return perfilAtivo.get();
    }

    public void selecionarPerfil(long id) {
        perfilAtivo.set(id);
    }
}
