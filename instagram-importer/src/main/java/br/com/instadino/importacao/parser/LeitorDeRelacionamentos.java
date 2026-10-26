package br.com.instadino.importacao.parser;

import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Lê listas de relacionamentos (seguidores, seguindo etc.) em JSON ou HTML.
 *
 * <p>O JSON é lido em streaming, um elemento por vez, com limites de profundidade e de tamanho de texto. O
 * formato observado é uma lista (na raiz ou sob uma chave) de objetos com {@code string_list_data}; o nome do
 * usuário vem de {@code value}, de {@code title} ou da URL em {@code href}, nessa ordem. O HTML é tratado como
 * texto: apenas links de perfil do Instagram são considerados e nenhum script é executado.
 */
public final class LeitorDeRelacionamentos {

    private static final JsonFactory FABRICA = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(64)
                    .maxStringLength(100_000)
                    .maxNameLength(500)
                    .maxNumberLength(30)
                    .build())
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper(FABRICA);

    public static final String MOTIVO_SEM_USUARIO = "sem nome de usuário válido";
    public static final String MOTIVO_ESTRUTURA = "estrutura inesperada";

    private LeitorDeRelacionamentos() {
    }

    public static ResultadoDeLeitura lerJson(InputStream entrada, Cancelamento cancelamento) throws IOException {
        ResultadoDeLeitura resultado = new ResultadoDeLeitura();
        try (JsonParser p = MAPPER.getFactory().createParser(entrada)) {
            JsonToken inicio = p.nextToken();
            if (inicio == JsonToken.START_ARRAY) {
                lerLista(p, resultado, cancelamento);
            } else if (inicio == JsonToken.START_OBJECT) {
                boolean encontrouLista = false;
                // Alguns arquivos (por exemplo, uma única solicitação pendente) são UM registro na raiz, com
                // label_values ou string_list_data; nesse caso a própria raiz é o elemento.
                com.fasterxml.jackson.databind.node.ObjectNode registroDaRaiz = MAPPER.createObjectNode();
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String campo = p.currentName();
                    JsonToken valor = p.nextToken();
                    if (valor == JsonToken.START_ARRAY && ("label_values".equals(campo) || "string_list_data".equals(campo))) {
                        registroDaRaiz.set(campo, MAPPER.readTree(p));
                    } else if (valor == JsonToken.START_ARRAY) {
                        encontrouLista = true;
                        lerLista(p, resultado, cancelamento);
                    } else if (valor != null && valor.isScalarValue() && ("timestamp".equals(campo) || "title".equals(campo))) {
                        registroDaRaiz.set(campo, MAPPER.readTree(p));
                    } else {
                        p.skipChildren();
                    }
                }
                if (registroDaRaiz.has("label_values") || registroDaRaiz.has("string_list_data")) {
                    encontrouLista = true;
                    processarElemento(registroDaRaiz, resultado);
                }
                if (!encontrouLista) {
                    throw new IOException("Estrutura sem lista de registros.");
                }
            } else {
                throw new IOException("Estrutura JSON inesperada.");
            }
        }
        return resultado;
    }

    private static void lerLista(JsonParser p, ResultadoDeLeitura resultado, Cancelamento cancelamento) throws IOException {
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
                resultado.rejeitar(MOTIVO_ESTRUTURA);
                continue;
            }
            JsonNode elemento = MAPPER.readTree(p);
            processarElemento(elemento, resultado);
        }
    }

    /**
     * Descreve a forma do elemento: nomes de chaves e o TIPO de cada valor (texto, vazio, num...), nunca o conteúdo.
     * Ex.: {@code {string_list_data:[1]{href:texto,timestamp:num,value:texto},title:vazio}}.
     */
    static String forma(JsonNode no) {
        return forma(no, 0);
    }

    private static String forma(JsonNode no, int nivel) {
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
            java.util.TreeSet<String> chaves = new java.util.TreeSet<>();
            no.fieldNames().forEachRemaining(chaves::add);
            StringBuilder s = new StringBuilder("{");
            for (String c : chaves) {
                if (s.length() > 1) {
                    s.append(',');
                }
                s.append(c.length() > 40 ? c.substring(0, 40) + "…" : c).append(':').append(forma(no.get(c), nivel + 1));
            }
            return s.append('}').toString();
        }
        if (no.isTextual()) {
            return no.asText().isEmpty() ? "vazio" : "texto";
        }
        return no.isNumber() ? "num" : no.isBoolean() ? "bool" : "valor";
    }

    private static void processarElemento(JsonNode elemento, ResultadoDeLeitura resultado) {
        resultado.registrarForma(forma(elemento));
        JsonNode rotulos = elemento.get("label_values");
        if (rotulos != null && rotulos.isArray() && !rotulos.isEmpty()) {
            processarRotulos(elemento, rotulos, resultado);
            return;
        }
        String titulo = texto(elemento.get("title"));
        JsonNode itens = elemento.get("string_list_data");
        if (itens == null || !itens.isArray() || itens.isEmpty()) {
            Optional<NomeDeUsuario> doTitulo = NomeDeUsuario.tentar(titulo);
            if (doTitulo.isPresent()) {
                resultado.aceitar(doTitulo.get(), null);
            } else {
                resultado.rejeitar(MOTIVO_SEM_USUARIO);
            }
            return;
        }
        for (JsonNode item : itens) {
            Optional<NomeDeUsuario> usuario = NomeDeUsuario.tentar(texto(item.get("value")))
                    .or(() -> NomeDeUsuario.tentar(titulo))
                    .or(() -> NomeDeUsuario.deUrlDePerfil(texto(item.get("href"))));
            if (usuario.isPresent()) {
                JsonNode ts = item.get("timestamp");
                Long instante = ts != null && ts.canConvertToLong() && ts.asLong() > 0 ? ts.asLong() : null;
                resultado.aceitar(usuario.get(), instante);
            } else {
                resultado.rejeitar(MOTIVO_SEM_USUARIO);
            }
        }
    }

    /**
     * Formato mais recente de algumas listas (por exemplo, solicitações pendentes): uma lista {@code label_values} com
     * pares rótulo/valor. Os rótulos mudam com o idioma, então o nome de usuário é procurado pelo endereço do perfil
     * (em qualquer campo de texto) e, na falta dele, em um rótulo de nome de usuário.
     */
    private static void processarRotulos(JsonNode elemento, JsonNode rotulos, ResultadoDeLeitura resultado) {
        Optional<NomeDeUsuario> usuario = Optional.empty();
        for (JsonNode item : rotulos) {
            usuario = NomeDeUsuario.deUrlDePerfil(texto(item.get("href"))).or(() -> NomeDeUsuario.deUrlDePerfil(texto(item.get("value"))));
            if (usuario.isPresent()) {
                break;
            }
        }
        if (usuario.isEmpty()) {
            for (JsonNode item : rotulos) {
                String rotulo = texto(item.get("label"));
                if (rotulo != null && (rotulo.toLowerCase(java.util.Locale.ROOT).contains("user") || rotulo.toLowerCase(java.util.Locale.ROOT).contains("usu"))) {
                    usuario = NomeDeUsuario.tentar(texto(item.get("value")));
                    if (usuario.isPresent()) {
                        break;
                    }
                }
            }
        }
        if (usuario.isEmpty()) {
            resultado.rejeitar(MOTIVO_SEM_USUARIO);
            return;
        }
        JsonNode ts = elemento.get("timestamp");
        if (ts == null || !ts.canConvertToLong()) {
            for (JsonNode item : rotulos) {
                if (item.get("timestamp") != null && item.get("timestamp").canConvertToLong()) {
                    ts = item.get("timestamp");
                    break;
                }
            }
        }
        resultado.aceitar(usuario.get(), ts != null && ts.canConvertToLong() && ts.asLong() > 0 ? ts.asLong() : null);
    }

    public static ResultadoDeLeitura lerHtml(InputStream entrada) throws IOException {
        ResultadoDeLeitura resultado = new ResultadoDeLeitura();
        Document documento = Jsoup.parse(entrada, StandardCharsets.UTF_8.name(), "");
        for (Element link : documento.select("a[href]")) {
            String href = link.attr("href");
            if (!href.toLowerCase(java.util.Locale.ROOT).contains("instagram.com")) {
                continue;
            }
            Optional<NomeDeUsuario> usuario = NomeDeUsuario.deUrlDePerfil(href).or(() -> NomeDeUsuario.tentar(link.text()));
            if (usuario.isPresent()) {
                resultado.aceitar(usuario.get(), null);
            } else {
                resultado.rejeitar(MOTIVO_SEM_USUARIO);
            }
        }
        return resultado;
    }

    private static String texto(JsonNode no) {
        return no != null && no.isTextual() ? no.asText() : null;
    }
}
