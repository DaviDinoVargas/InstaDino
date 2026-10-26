package br.com.instadino.servicos;

import br.com.instadino.persistencia.repositorio.RepositorioDeEventosDeSeguranca;

/** Eventos locais relevantes, sem dados pessoais. */
final class RegistroDeAuditoriaLocal {

    private final RepositorioDeEventosDeSeguranca eventos;

    RegistroDeAuditoriaLocal(RepositorioDeEventosDeSeguranca eventos) {
        this.eventos = eventos;
    }

    void registrar(String tipo, String detalhe) {
        try {
            eventos.registrar(tipo, detalhe);
        } catch (RuntimeException e) {
            System.getLogger(RegistroDeAuditoriaLocal.class.getName())
                    .log(System.Logger.Level.WARNING, "Não foi possível registrar o evento {0}.", tipo);
        }
    }
}
