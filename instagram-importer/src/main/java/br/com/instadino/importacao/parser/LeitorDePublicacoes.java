package br.com.instadino.importacao.parser;

import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.Publicacao;
import br.com.instadino.importacao.Publicacao.MidiaDePublicacao;
import br.com.instadino.importacao.Publicacao.TipoDeMidia;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lê publicações, reels e stories. Formatos observados: lista de objetos com {@code media: [{uri, creation_timestamp,
 * title}]} (posts e reels) e lista de objetos com {@code uri} diretamente (stories). A legenda vem do título do elemento
 * ou da primeira mídia; o instante, do elemento ou da primeira mídia. Os arquivos de mídia não são lidos nem extraídos:
 * apenas o caminho declarado é resolvido contra o inventário do ZIP.
 */
public final class LeitorDePublicacoes {

    public record Resultado(List<Publicacao> publicacoes, ContagemDeLeitura contagem) {
    }

    private LeitorDePublicacoes() {
    }

    /**
     * @param resolvedor devolve a mídia (caminho sem raiz e tamanho) correspondente ao {@code uri} declarado, ou nulo se o
     *                   arquivo não existe no ZIP
     */
    public static Resultado ler(InputStream entrada, CategoriaDeDados categoria, Cancelamento cancelamento,
                                Function<String, MidiaDePublicacao> resolvedor) throws IOException {
        ContagemDeLeitura contagem = new ContagemDeLeitura();
        List<Publicacao> publicacoes = new ArrayList<>();
        VarredorDeJson.varrer(entrada, cancelamento, contagem, elemento -> {
            List<MidiaDePublicacao> midias = new ArrayList<>();
            Long instante = instante(elemento.get("creation_timestamp"), elemento.get("timestamp"));
            String legenda = texto(elemento.get("title"));
            JsonNode lista = elemento.get("media");
            if (lista != null && lista.isArray()) {
                for (JsonNode m : lista) {
                    adicionarMidia(m, midias, resolvedor);
                    if (instante == null) {
                        instante = instante(m.get("creation_timestamp"), m.get("timestamp"));
                    }
                    if (legenda == null || legenda.isBlank()) {
                        legenda = texto(m.get("title"));
                    }
                }
            } else if (elemento.has("uri")) {
                adicionarMidia(elemento, midias, resolvedor);
            }
            if (midias.isEmpty() && (legenda == null || legenda.isBlank()) && instante == null) {
                contagem.rejeitar("sem dados reconhecíveis");
                return;
            }
            publicacoes.add(new Publicacao(categoria, CorretorDeTexto.corrigir(legenda == null || legenda.isBlank() ? null : legenda.strip()), instante, midias));
            contagem.aceitar(instante);
        });
        return new Resultado(publicacoes, contagem);
    }

    private static void adicionarMidia(JsonNode no, List<MidiaDePublicacao> midias, Function<String, MidiaDePublicacao> resolvedor) {
        String uri = texto(no.get("uri"));
        if (uri == null || uri.isBlank()) {
            return;
        }
        MidiaDePublicacao encontrada = resolvedor.apply(uri);
        midias.add(encontrada != null ? encontrada : new MidiaDePublicacao(caminhoLimpo(uri), TipoDeMidia.deCaminho(uri), null));
    }

    private static String caminhoLimpo(String uri) {
        String c = uri.replace('\\', '/');
        return c.length() > 300 ? c.substring(c.length() - 300) : c;
    }

    private static Long instante(JsonNode... nos) {
        for (JsonNode no : nos) {
            if (no != null && no.canConvertToLong() && no.asLong() > 0) {
                long v = no.asLong();
                return v > 100_000_000_000L ? v / 1000 : v;
            }
        }
        return null;
    }

    private static String texto(JsonNode no) {
        return no != null && no.isTextual() ? no.asText() : null;
    }
}
