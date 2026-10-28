package br.com.instadino.importacao.parser;

import br.com.instadino.importacao.Cancelamento;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lê um arquivo {@code message_N.json} de uma conversa e devolve apenas <strong>agregados</strong>: quantas mensagens cada
 * remetente enviou, em que meses, e quantas continham mídia. O texto das mensagens, os nomes de arquivo de mídia, as
 * reações e os links são lidos pelo analisador JSON (que precisa percorrê-los) mas descartados na hora: nada disso é
 * guardado, devolvido ou escrito em diagnósticos.
 */
public final class LeitorDeMensagens {

    /** Máximo de remetentes distintos por conversa; excedentes são somados em um único grupo. */
    static final int MAXIMO_DE_REMETENTES = 500;
    public static final String OUTROS = "(outros)";

    /** Agregados de um arquivo. {@code porRemetente} mapeia remetente → mês (yyyy-MM, UTC) → quantidade. */
    public record Parte(String titulo, List<String> participantes, Map<String, Map<String, Integer>> porRemetente, int midias,
                        Long primeiroInstante, Long ultimoInstante, ContagemDeLeitura contagem) {
    }

    private static final String[] CAMPOS_DE_MIDIA = {"photos", "videos", "audio_files", "gifs", "sticker"};

    private LeitorDeMensagens() {
    }

    public static Parte ler(InputStream entrada, Cancelamento cancelamento) throws IOException {
        ContagemDeLeitura contagem = new ContagemDeLeitura();
        String titulo = null;
        List<String> participantes = new ArrayList<>();
        Map<String, Map<String, Integer>> porRemetente = new LinkedHashMap<>();
        int midias = 0;
        Long primeiro = null;
        Long ultimo = null;
        boolean viuMensagens = false;
        try (JsonParser p = VarredorDeJson.MAPPER.getFactory().createParser(entrada)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Estrutura JSON inesperada.");
            }
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String campo = p.currentName();
                JsonToken valor = p.nextToken();
                if ("title".equals(campo) && valor == JsonToken.VALUE_STRING) {
                    titulo = CorretorDeTexto.corrigir(p.getText());
                } else if ("participants".equals(campo) && valor == JsonToken.START_ARRAY) {
                    while (p.nextToken() == JsonToken.START_OBJECT) {
                        JsonNode no = VarredorDeJson.MAPPER.readTree(p);
                        if (no.path("name").isTextual() && participantes.size() < 1000) {
                            participantes.add(CorretorDeTexto.corrigir(no.get("name").asText()));
                        }
                    }
                } else if ("messages".equals(campo) && valor == JsonToken.START_ARRAY) {
                    viuMensagens = true;
                    int n = 0;
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        if (p.currentToken() == null) {
                            throw new IOException("Arquivo JSON truncado.");
                        }
                        if ((++n & 1023) == 0 && cancelamento.cancelado()) {
                            throw new IOException("Cancelado.");
                        }
                        if (p.currentToken() != JsonToken.START_OBJECT) {
                            p.skipChildren();
                            contagem.elemento("(não é objeto)");
                            contagem.rejeitar("estrutura inesperada");
                            continue;
                        }
                        JsonNode m = VarredorDeJson.MAPPER.readTree(p);
                        contagem.elemento("mensagem");
                        JsonNode remetente = m.get("sender_name");
                        Long instante = instante(m);
                        if (remetente == null || !remetente.isTextual() || instante == null) {
                            contagem.rejeitar("sem remetente ou data");
                            continue;
                        }
                        String nome = CorretorDeTexto.corrigir(remetente.asText());
                        if (!porRemetente.containsKey(nome) && porRemetente.size() >= MAXIMO_DE_REMETENTES) {
                            nome = OUTROS;
                        }
                        porRemetente.computeIfAbsent(nome, k -> new TreeMap<>()).merge(mes(instante), 1, Integer::sum);
                        if (temMidia(m)) {
                            midias++;
                        }
                        contagem.aceitar(instante);
                        primeiro = primeiro == null ? instante : Math.min(primeiro, instante);
                        ultimo = ultimo == null ? instante : Math.max(ultimo, instante);
                    }
                } else if (valor == JsonToken.START_ARRAY || valor == JsonToken.START_OBJECT) {
                    p.skipChildren();
                }
            }
        }
        if (!viuMensagens) {
            throw new IOException("Estrutura sem lista de mensagens.");
        }
        return new Parte(titulo, participantes, porRemetente, midias, primeiro, ultimo, contagem);
    }

    private static Long instante(JsonNode m) {
        JsonNode ms = m.get("timestamp_ms");
        if (ms != null && ms.isNumber() && ms.asLong() > 0) {
            return ms.asLong() / 1000;
        }
        JsonNode s = m.get("timestamp");
        return s != null && s.isNumber() && s.asLong() > 0 ? s.asLong() : null;
    }

    private static boolean temMidia(JsonNode m) {
        for (String campo : CAMPOS_DE_MIDIA) {
            JsonNode v = m.get(campo);
            if (v != null && !v.isNull() && (!v.isContainerNode() || !v.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    static String mes(long segundos) {
        var d = Instant.ofEpochSecond(segundos).atOffset(ZoneOffset.UTC);
        return "%04d-%02d".formatted(d.getYear(), d.getMonthValue());
    }
}
