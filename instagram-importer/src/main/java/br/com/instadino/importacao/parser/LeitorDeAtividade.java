package br.com.instadino.importacao.parser;

import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.RegistroDeAtividade;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lê arquivos de atividade (curtidas, comentários, salvos, pesquisas, visualizações, interações com stories).
 *
 * <p>Os formatos observados misturam {@code string_list_data}, {@code string_map_data} e {@code label_values}, e os
 * rótulos mudam com o idioma. Por isso a extração não depende de nomes de rótulos: o instante vem do carimbo de tempo do
 * elemento ou do primeiro encontrado; a conta, do título, de um valor que seja nome de usuário ou do endereço de perfil;
 * o texto só é extraído nas categorias em que faz sentido (comentários e pesquisas). Dicas de rótulo (dono, autor,
 * comentário) apenas desempatam.
 */
public final class LeitorDeAtividade {

    public record Resultado(List<RegistroDeAtividade> registros, ContagemDeLeitura contagem) {
    }

    private static final Set<String> SEGMENTOS_QUE_NAO_SAO_PERFIL = Set.of("p", "reel", "reels", "tv", "stories", "explore", "accounts",
            "direct", "s", "locations", "tags", "about", "legal", "web", "oauth");

    private record Campo(String rotulo, String valor, String href, Long instante) {
    }

    private LeitorDeAtividade() {
    }

    public static Resultado ler(InputStream entrada, CategoriaDeDados categoria, String subtipo, Cancelamento cancelamento) throws IOException {
        ContagemDeLeitura contagem = new ContagemDeLeitura();
        List<RegistroDeAtividade> registros = new ArrayList<>();
        VarredorDeJson.varrer(entrada, cancelamento, contagem, elemento -> {
            Optional<RegistroDeAtividade> r = extrair(elemento, categoria, subtipo);
            if (r.isPresent()) {
                registros.add(r.get());
                contagem.aceitar(r.get().instanteEmSegundos());
            } else {
                contagem.rejeitar("sem dados reconhecíveis");
            }
        });
        return new Resultado(registros, contagem);
    }

    static Optional<RegistroDeAtividade> extrair(JsonNode el, CategoriaDeDados categoria, String subtipo) {
        List<Campo> campos = new ArrayList<>();
        coletar(el.get("string_list_data"), campos, 0);
        coletar(el.get("string_map_data"), campos, 0);
        coletar(el.get("label_values"), campos, 0);
        Long instante = instante(el.get("timestamp")).or(() -> instante(el.get("timestamp_ms"))).or(() -> instante(el.get("creation_timestamp")))
                .or(() -> campos.stream().map(Campo::instante).filter(java.util.Objects::nonNull).findFirst()).orElse(null);
        String titulo = texto(el.get("title"));

        String conta = null;
        String texto = null;
        if (categoria == CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS) {
            // interesses e anunciantes: o nome do tema ou do anunciante é o texto; só ligamos a uma conta quando há um perfil explícito
            Campo campoDeTexto = escolherTexto(campos, categoria, false); // sem dica de rótulo, não adivinha: o valor pode ser uma conta
            texto = campoDeTexto != null ? CorretorDeTexto.corrigir(campoDeTexto.valor()) : primeiroTextoDoElemento(el, titulo);
            final Campo escolhido = campoDeTexto;
            conta = contaDe(null, campos.stream().filter(c -> c != escolhido).toList(), categoria);
        } else if (categoria == CategoriaDeDados.COMENTARIOS || categoria == CategoriaDeDados.PESQUISAS) {
            Campo campoDeTexto = escolherTexto(campos, categoria, true);
            texto = campoDeTexto == null ? null : CorretorDeTexto.corrigir(campoDeTexto.valor());
            final Campo escolhido = campoDeTexto;
            conta = contaDe(titulo, campos.stream().filter(c -> c != escolhido).toList(), categoria);
        } else {
            conta = contaDe(titulo, campos, categoria);
        }
        String referencia = campos.stream().map(Campo::href).filter(LeitorDeAtividade::ehInstagram).findFirst()
                .or(() -> campos.stream().map(Campo::valor).filter(LeitorDeAtividade::ehInstagram).findFirst()).orElse(null);
        if (conta == null && texto == null && referencia == null && instante == null) {
            return Optional.empty();
        }
        return Optional.of(new RegistroDeAtividade(categoria, subtipo, conta, texto, referencia, instante));
    }

    // ---- escolhas ----

    private static Campo escolherTexto(List<Campo> campos, CategoriaDeDados categoria, boolean adivinhar) {
        String[] dicas = categoria == CategoriaDeDados.COMENTARIOS ? new String[] {"comment", "coment"}
                : categoria == CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS ? new String[] {"name", "nome", "topic", "tema", "interest", "interesse", "advertiser", "anunciante"}
                : new String[] {"search", "busca", "pesquis", "term", "query"};
        for (Campo c : campos) {
            if (c.rotulo() != null && !c.valor().isBlank() && contemAlgum(c.rotulo(), dicas)) {
                return c;
            }
        }
        if (!adivinhar) {
            return null;
        }
        // sem dica de rótulo: o maior texto que não seja endereço
        Campo maior = null;
        for (Campo c : campos) {
            if (!c.valor().isBlank() && !ehEndereco(c.valor()) && (maior == null || c.valor().length() > maior.valor().length())) {
                maior = c;
            }
        }
        return maior;
    }

    /** Formatos planos, como {"advertiser_name": "..."}: usa o primeiro campo textual conhecido do próprio elemento. */
    private static String primeiroTextoDoElemento(JsonNode el, String titulo) {
        for (String chave : new String[] {"advertiser_name", "name", "topic", "title"}) {
            JsonNode v = el.get(chave);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return CorretorDeTexto.corrigir(v.asText());
            }
        }
        return titulo == null || titulo.isBlank() ? null : CorretorDeTexto.corrigir(titulo);
    }

    private static String contaDe(String titulo, List<Campo> campos, CategoriaDeDados categoria) {
        for (Campo c : campos) {
            if (c.rotulo() != null && contemAlgum(c.rotulo(), "owner", "dono", "autor", "author", "usuário", "usuario", "user", "username")) {
                Optional<NomeDeUsuario> n = NomeDeUsuario.tentar(c.valor()).or(() -> contaDeEndereco(c.href())).or(() -> contaDeEndereco(c.valor()));
                if (n.isPresent()) {
                    return n.get().valor();
                }
            }
        }
        Optional<NomeDeUsuario> doTitulo = NomeDeUsuario.tentar(titulo);
        if (doTitulo.isPresent()) {
            return doTitulo.get().valor();
        }
        for (Campo c : campos) {
            String v = c.valor().strip();
            if (!v.isEmpty() && v.indexOf(' ') < 0 && !ehEndereco(v) && categoria != CategoriaDeDados.PESQUISAS) {
                Optional<NomeDeUsuario> n = NomeDeUsuario.tentar(v);
                if (n.isPresent() && v.length() > 1) {
                    return n.get().valor();
                }
            }
        }
        for (Campo c : campos) {
            Optional<NomeDeUsuario> n = contaDeEndereco(c.href()).or(() -> contaDeEndereco(c.valor()));
            if (n.isPresent()) {
                return n.get().valor();
            }
        }
        return null;
    }

    /** Conta a partir de endereço de PERFIL (não de publicação, reel, story etc.). */
    static Optional<NomeDeUsuario> contaDeEndereco(String url) {
        if (url == null || !ehInstagram(url)) {
            return Optional.empty();
        }
        Optional<NomeDeUsuario> n = NomeDeUsuario.deUrlDePerfil(url);
        if (n.isPresent() && SEGMENTOS_QUE_NAO_SAO_PERFIL.contains(n.get().valor())) {
            return Optional.empty();
        }
        return n;
    }

    // ---- coleta ----

    private static void coletar(JsonNode no, List<Campo> destino, int nivel) {
        if (no == null || nivel > 3) {
            return;
        }
        if (no.isArray()) {
            for (JsonNode filho : no) {
                if (filho.isObject() && (filho.has("value") || filho.has("href") || filho.has("timestamp") || filho.has("label"))) {
                    destino.add(new Campo(texto(filho.get("label")), valorTextual(filho.get("value")), texto(filho.get("href")), instante(filho.get("timestamp")).orElse(null)));
                    coletar(filho.get("dict"), destino, nivel + 1);
                    coletar(filho.get("vec"), destino, nivel + 1);
                } else if (filho.isObject()) {
                    coletar(filho.get("dict"), destino, nivel + 1);
                    coletar(filho.get("vec"), destino, nivel + 1);
                }
            }
        } else if (no.isObject()) {
            // string_map_data: rótulo -> {value, href, timestamp}
            no.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (v.isObject()) {
                    destino.add(new Campo(e.getKey(), valorTextual(v.get("value")), texto(v.get("href")), instante(v.get("timestamp")).orElse(null)));
                }
            });
        }
    }

    private static Optional<Long> instante(JsonNode no) {
        if (no == null || !no.canConvertToLong() || no.asLong() <= 0) {
            return Optional.empty();
        }
        long v = no.asLong();
        return Optional.of(v > 100_000_000_000L ? v / 1000 : v); // milissegundos -> segundos
    }

    private static String texto(JsonNode no) {
        return no != null && no.isTextual() ? no.asText() : null;
    }

    private static String valorTextual(JsonNode no) {
        return no != null && no.isTextual() ? no.asText() : "";
    }

    private static boolean contemAlgum(String rotulo, String... dicas) {
        String r = rotulo.toLowerCase(Locale.ROOT);
        for (String d : dicas) {
            if (r.contains(d)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ehInstagram(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.startsWith("https://www.instagram.com/") || u.startsWith("https://instagram.com/") || u.startsWith("http://www.instagram.com/");
    }

    private static boolean ehEndereco(String valor) {
        String v = valor.toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }
}
