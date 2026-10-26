package br.com.instadino.persistencia.seguranca;

import java.util.Arrays;

/**
 * NÃO oferece proteção real: apenas devolve o segredo como está. Existe para desenvolvimento e testes em
 * sistemas sem DPAPI. É recusado em produção por {@link ProtetoresPadrao}.
 */
public final class ProtetorSomenteDesenvolvimento implements ProtetorDeSegredo {

    @Override
    public byte[] proteger(byte[] segredo) {
        return Arrays.copyOf(segredo, segredo.length);
    }

    @Override
    public byte[] revelar(byte[] protegido) {
        return Arrays.copyOf(protegido, protegido.length);
    }

    @Override
    public boolean seguroParaProducao() {
        return false;
    }

    @Override
    public String nome() {
        return "SOMENTE DESENVOLVIMENTO (sem proteção real)";
    }
}
