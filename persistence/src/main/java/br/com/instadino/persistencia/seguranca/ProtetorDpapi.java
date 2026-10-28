package br.com.instadino.persistencia.seguranca;

import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;

/**
 * Protege segredos com a DPAPI do Windows, vinculando-os ao usuário do Windows atual.
 *
 * <p>Limite: um processo executando como o mesmo usuário consegue revelar o segredo; administradores
 * e malware com a sessão do usuário não são cobertos (ver docs/DATABASE_SECURITY.md).
 */
public final class ProtetorDpapi implements ProtetorDeSegredo {

    /** Entropia adicional específica do aplicativo, para que outros programas do mesmo usuário não reutilizem o blob por engano. */
    private static final byte[] ENTROPIA = "br.com.instadino.chave-do-banco.v1".getBytes(StandardCharsets.UTF_8);

    public static boolean disponivel() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    @Override
    public byte[] proteger(byte[] segredo) {
        try {
            return Crypt32Util.cryptProtectData(segredo, ENTROPIA, 0, "InstaDino", null);
        } catch (RuntimeException | LinkageError e) {
            throw new ProtecaoIndisponivelException("Falha ao proteger a chave com a DPAPI.", e);
        }
    }

    @Override
    public byte[] revelar(byte[] protegido) {
        try {
            return Crypt32Util.cryptUnprotectData(protegido, ENTROPIA, 0, null);
        } catch (RuntimeException | LinkageError e) {
            throw new ProtecaoIndisponivelException("Falha ao revelar a chave com a DPAPI.", e);
        }
    }

    @Override
    public boolean seguroParaProducao() {
        return true;
    }

    @Override
    public String nome() {
        return "DPAPI do Windows";
    }
}
