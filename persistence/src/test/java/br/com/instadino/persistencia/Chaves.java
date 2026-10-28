package br.com.instadino.persistencia;

import java.util.Arrays;

/** Chaves fixas e públicas, usadas somente em testes automatizados. */
public final class Chaves {

    public static final byte[] A = chave((byte) 1);
    public static final byte[] B = chave((byte) 2);

    private Chaves() {
    }

    public static byte[] chave(byte valor) {
        byte[] k = new byte[32];
        Arrays.fill(k, valor);
        return k;
    }
}
