package br.com.instadino.importacao.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Os JSON de exportação do Instagram/Facebook gravam texto UTF-8 como se cada byte fosse um caractere Latin-1
 * ("ção" vira "Ã§Ã£o"). Esta correção desfaz isso quando, e somente quando, o texto inteiro cabe em Latin-1 e os bytes
 * formam UTF-8 válido; caso contrário o texto original é mantido.
 */
public final class CorretorDeTexto {

    private CorretorDeTexto() {
    }

    public static String corrigir(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        boolean suspeito = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c > 0xFF) {
                return texto; // contém caracteres que não existem em Latin-1: o texto já está correto
            }
            if (c >= 0xC2 && c <= 0xF4) {
                suspeito = true;
            }
        }
        if (!suspeito) {
            return texto;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(texto.getBytes(StandardCharsets.ISO_8859_1))).toString();
        } catch (CharacterCodingException e) {
            return texto;
        }
    }
}
