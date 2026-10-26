package br.com.instadino.persistencia.seguranca;

/**
 * Escolhe o protetor de chave. Falha fechada: fora do Windows só é possível usar o protetor de desenvolvimento
 * com opt-in explícito (propriedade de sistema {@value #PROPRIEDADE_MODO_INSEGURO}=true).
 */
public final class ProtetoresPadrao {

    public static final String PROPRIEDADE_MODO_INSEGURO = "instadino.modoInseguroDesenvolvimento";

    private ProtetoresPadrao() {
    }

    public static ProtetorDeSegredo escolher() {
        if (ProtetorDpapi.disponivel()) {
            return new ProtetorDpapi();
        }
        if (Boolean.getBoolean(PROPRIEDADE_MODO_INSEGURO)) {
            return new ProtetorSomenteDesenvolvimento();
        }
        throw new ProtecaoIndisponivelException(
                "Não há mecanismo seguro de proteção de chave neste sistema. "
                        + "O banco local não será criado. (Somente desenvolvimento: -D"
                        + PROPRIEDADE_MODO_INSEGURO + "=true)");
    }
}
