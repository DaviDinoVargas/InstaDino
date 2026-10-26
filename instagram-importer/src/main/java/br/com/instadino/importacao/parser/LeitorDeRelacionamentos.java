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
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    if (p.nextToken() == JsonToken.START_ARRAY) {
                        encontrouLista = true;
                        lerLista(p, resultado, cancelamento);
                    } else {
                        p.skipChildren();
                    }
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

    private static void processarElemento(JsonNode elemento, ResultadoDeLeitura resultado) {
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
