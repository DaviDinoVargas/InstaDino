package br.com.instadino.importacao.seguranca;

import br.com.instadino.importacao.ImportacaoRejeitadaException;
import br.com.instadino.importacao.ImportacaoRejeitadaException.Motivo;
import br.com.instadino.importacao.LimitesDeImportacao;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Valida e normaliza nomes de entradas de um ZIP. O importador NUNCA extrai arquivos para o disco usando estes
 * nomes; a validação existe como defesa em profundidade e para impedir que um ZIP forjado seja aceito.
 */
public final class CaminhoSeguro {

    private CaminhoSeguro() {
    }

    /** Resultado da validação: o caminho normalizado e a chave usada para detectar duplicatas. */
    public record Validado(String caminho, String chaveDeDuplicidade, boolean diretorio) {
    }

    public static Validado validar(String nome, LimitesDeImportacao limites) {
        if (nome == null || nome.isEmpty() || nome.length() > limites.comprimentoMaximoDoNome()) {
            throw perigoso();
        }
        for (int i = 0; i < nome.length(); i++) {
            char c = nome.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\\' || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.CONTROL) {
                throw perigoso();
            }
        }
        if (nome.startsWith("/") || (nome.length() >= 2 && nome.charAt(1) == ':' && Character.isLetter(nome.charAt(0)))) {
            throw perigoso();
        }
        boolean diretorio = nome.endsWith("/");
        String semBarraFinal = diretorio ? nome.substring(0, nome.length() - 1) : nome;
        if (semBarraFinal.isEmpty()) {
            throw perigoso();
        }
        List<String> segmentos = new ArrayList<>();
        for (String segmento : semBarraFinal.split("/", -1)) {
            if (segmento.isEmpty() || segmento.equals("..")) {
                throw perigoso();
            }
            if (!segmento.equals(".")) {
                segmentos.add(segmento);
            }
        }
        if (segmentos.isEmpty()) {
            throw perigoso();
        }
        int profundidade = diretorio ? segmentos.size() : segmentos.size() - 1;
        if (profundidade > limites.profundidadeMaxima()) {
            throw perigoso();
        }
        String normalizado = Normalizer.normalize(String.join("/", segmentos), Normalizer.Form.NFC);
        return new Validado(normalizado, normalizado.toLowerCase(Locale.ROOT), diretorio);
    }

    private static ImportacaoRejeitadaException perigoso() {
        return new ImportacaoRejeitadaException(Motivo.CAMINHO_PERIGOSO);
    }
}
