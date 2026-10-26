package br.com.instadino.persistencia.seguranca;

/** Protege um segredo (a chave do banco) usando um mecanismo do sistema operacional. */
public interface ProtetorDeSegredo {

    byte[] proteger(byte[] segredo);

    byte[] revelar(byte[] protegido);

    /** Falso para mecanismos que não oferecem proteção real em repouso (somente desenvolvimento). */
    boolean seguroParaProducao();

    String nome();
}
