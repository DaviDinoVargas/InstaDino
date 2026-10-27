package br.com.instadino.importacao.parser;

import br.com.instadino.importacao.Cancelamento;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

/**
 * Percorre um JSON de exportação em streaming, entregando um elemento por vez. Aceita lista na raiz, objeto com uma ou
 * mais listas sob chaves quaisquer, e registro único na raiz (com campos como label_values). Os limites de profundidade
 * e de tamanho de texto são os mesmos da leitura de relacionamentos.
 */
final class VarredorDeJson {

    static final JsonFactory FABRICA = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(100_000).maxNameLength(500).maxNumberLength(30).build())
            .build();
    static final ObjectMapper MAPPER = new ObjectMapper(FABRICA);

    private static final Set<String> CAMPOS_DE_REGISTRO = Set.of("label_values", "string_list_data", "string_map_data", "media", "uri");

    @FunctionalInterface
    interface Tratador {
        void tratar(JsonNode elemento) throws IOException;
    }

    private VarredorDeJson() {
    }

    static void varrer(InputStream entrada, Cancelamento cancelamento, ContagemDeLeitura contagem, Tratador tratador) throws IOException {
        try (JsonParser p = MAPPER.getFactory().createParser(entrada)) {
            JsonToken inicio = p.nextToken();
            if (inicio == JsonToken.START_ARRAY) {
                lista(p, cancelamento, contagem, tratador);
            } else if (inicio == JsonToken.START_OBJECT) {
                ObjectNode raiz = MAPPER.createObjectNode();
                boolean encontrouLista = false;
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String campo = p.currentName();
                    JsonToken valor = p.nextToken();
                    if (valor == JsonToken.START_ARRAY && !CAMPOS_DE_REGISTRO.contains(campo)) {
                        encontrouLista = true;
                        lista(p, cancelamento, contagem, tratador);
                    } else if (valor == JsonToken.START_ARRAY || valor == JsonToken.START_OBJECT) {
                        raiz.set(campo, MAPPER.readTree(p));
                    } else if (valor != null && valor.isScalarValue()) {
                        raiz.set(campo, MAPPER.readTree(p));
                    } else {
                        p.skipChildren();
                    }
                }
                if (CAMPOS_DE_REGISTRO.stream().anyMatch(raiz::has)) {
                    encontrouLista = true;
                    elemento(raiz, contagem, tratador);
                }
                if (!encontrouLista) {
                    throw new IOException("Estrutura sem lista de registros.");
                }
            } else {
                throw new IOException("Estrutura JSON inesperada.");
            }
        }
    }

    private static void lista(JsonParser p, Cancelamento cancelamento, ContagemDeLeitura contagem, Tratador tratador) throws IOException {
        int contador = 0;
        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == null) {
                throw new IOException("Arquivo JSON truncado.");
            }
            if ((++contador & 1023) == 0 && cancelamento.cancelado()) {
                throw new IOException("Cancelado.");
            }
            if (p.currentToken() != JsonToken.START_OBJECT) {
                p.skipChildren();
                contagem.elemento("(não é objeto)");
                contagem.rejeitar("estrutura inesperada");
                continue;
            }
            elemento(MAPPER.readTree(p), contagem, tratador);
        }
    }

    private static void elemento(JsonNode no, ContagemDeLeitura contagem, Tratador tratador) throws IOException {
        contagem.elemento(forma(no, 0));
        tratador.tratar(no);
    }

    /** Forma do elemento: nomes de chaves e tipos de valor, nunca conteúdo. */
    static String forma(JsonNode no, int nivel) {
        if (no == null || no.isNull()) {
            return "nulo";
        }
        if (no.isArray()) {
            return "[" + no.size() + "]" + (no.isEmpty() ? "" : nivel >= 3 ? "…" : forma(no.get(0), nivel + 1));
        }
        if (no.isObject()) {
            if (nivel >= 3) {
                return "{…}";
            }
            TreeSet<String> chaves = new TreeSet<>();
            no.fieldNames().forEachRemaining(chaves::add);
            StringBuilder s = new StringBuilder("{");
            for (String c : chaves) {
                if (s.length() > 1) {
                    s.append(',');
                }
                // chaves de string_map_data são rótulos (por exemplo "Comment"), não dados pessoais; ainda assim limitadas
                s.append(c.length() > 40 ? c.substring(0, 40) + "…" : c).append(':').append(forma(no.get(c), nivel + 1));
            }
            return s.append('}').toString();
        }
        if (no.isTextual()) {
            return no.asText().isEmpty() ? "vazio" : "texto";
        }
        return no.isNumber() ? "num" : no.isBoolean() ? "bool" : "valor";
    }
}
