package br.com.instadino.fixtures;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Monta exportações SINTÉTICAS no formato JSON/HTML observado nas exportações do Instagram. Os nomes de usuário
 * e conteúdos são inventados. Somente para testes e demonstrações identificadas como tais.
 */
public final class ConstrutorDeExportacao {

    private static final long BASE = Instant.parse("2024-01-01T00:00:00Z").getEpochSecond();

    private final Map<String, byte[]> arquivos = new LinkedHashMap<>();

    public ConstrutorDeExportacao arquivo(String caminho, String conteudo) {
        arquivos.put(caminho, conteudo.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public ConstrutorDeExportacao arquivo(String caminho, byte[] conteudo) {
        arquivos.put(caminho, conteudo);
        return this;
    }

    // ---- Relacionamentos (JSON) ----

    /** followers_1.json: lista na raiz, nome em {@code value}. */
    public ConstrutorDeExportacao seguidores(String... usuarios) {
        return arquivo("connections/followers_and_following/followers_1.json", listaComValor(usuarios));
    }

    public ConstrutorDeExportacao seguidoresParte(int parte, String... usuarios) {
        return arquivo("connections/followers_and_following/followers_" + parte + ".json", listaComValor(usuarios));
    }

    /** following.json: objeto com chave de lista, nome em {@code title}. */
    public ConstrutorDeExportacao seguindo(String... usuarios) {
        return arquivo("connections/followers_and_following/following.json", objetoComTitulo("relationships_following", usuarios));
    }

    public ConstrutorDeExportacao pendentes(String... usuarios) {
        return arquivo("connections/followers_and_following/pending_follow_requests.json",
                objetoComTitulo("relationships_follow_requests_sent", usuarios));
    }

    public ConstrutorDeExportacao bloqueados(String... usuarios) {
        return arquivo("connections/followers_and_following/blocked_profiles.json",
                objetoComTitulo("relationships_blocked_users", usuarios));
    }

    public ConstrutorDeExportacao restritos(String... usuarios) {
        return arquivo("connections/followers_and_following/restricted_profiles.json",
                objetoComTitulo("relationships_restricted_users", usuarios));
    }

    public ConstrutorDeExportacao amigosProximos(String... usuarios) {
        return arquivo("connections/followers_and_following/close_friends.json",
                objetoComTitulo("relationships_close_friends", usuarios));
    }

    // ---- Relacionamentos (HTML) ----

    public ConstrutorDeExportacao seguidoresHtml(String... usuarios) {
        StringBuilder sb = new StringBuilder("<html><head><meta charset=\"utf-8\"></head><body>");
        for (String u : usuarios) {
            sb.append("<div><div><a target=\"_blank\" href=\"https://www.instagram.com/").append(u).append("\">")
                    .append(u).append("</a><div>Mar 05, 2023, 3:14 PM</div></div></div>");
        }
        return arquivo("connections/followers_and_following/followers_1.html", sb.append("</body></html>").toString());
    }

    // ---- Demais categorias (conteúdo mínimo e sintético) ----

    public ConstrutorDeExportacao comCategoriasDiversas() {
        arquivo("your_instagram_activity/media/posts_1.json",
                "[{\"media\":[{\"uri\":\"media/posts/202401/foto1.jpg\",\"title\":\"Legenda sintética\",\"creation_timestamp\":" + BASE + "}]}]");
        arquivo("media/posts/202401/foto1.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        arquivo("your_instagram_activity/likes/liked_posts.json",
                "{\"likes_media_likes\":[{\"title\":\"conta_exemplo\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/p/AAA/\",\"value\":\"x\",\"timestamp\":" + BASE + "}]}]}");
        arquivo("your_instagram_activity/comments/post_comments_1.json",
                "[{\"string_map_data\":{\"Comment\":{\"value\":\"comentário sintético\"},\"Time\":{\"timestamp\":" + BASE + "}}}]");
        arquivo("your_instagram_activity/saved/saved_posts.json", "{\"saved_saved_media\":[]}");
        arquivo("logged_information/recent_searches/account_searches.json", "{\"searches_user\":[]}");
        arquivo("your_instagram_activity/messages/inbox/amigo_1/message_1.json",
                "{\"participants\":[{\"name\":\"Pessoa Fictícia\"}],\"messages\":[{\"sender_name\":\"Pessoa Fictícia\",\"timestamp_ms\":" + BASE * 1000 + ",\"content\":\"mensagem sintética\"}]}");
        arquivo("security_and_login_information/login_and_account_creation/login_activity.json",
                "{\"account_history_login_history\":[]}");
        arquivo("personal_information/personal_information/personal_information.json",
                "{\"profile_user\":[{\"string_map_data\":{\"Username\":{\"value\":\"conta_sintetica\"}}}]}");
        arquivo("ads_information/ads_and_topics/ads_viewed.json", "{\"impressions_history_ads_seen\":[]}");
        arquivo("arquivo_desconhecido/qualquer_coisa.bin", new byte[] {1, 2, 3});
        return this;
    }

    /** Exportação mínima válida com listas de exemplo. */
    public static ConstrutorDeExportacao exemplo() {
        return new ConstrutorDeExportacao()
                .seguidores("ana", "bruno", "carla", "diego")
                .seguindo("bruno", "carla", "eduardo", "fernanda");
    }

    public byte[] bytes() {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (Map.Entry<String, byte[]> e : arquivos.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path escreverEm(Path destino) {
        try {
            Files.write(destino, bytes());
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- Formatação ----

    private static String listaComValor(String[] usuarios) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < usuarios.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"title\":\"\",\"media_list_data\":[],\"string_list_data\":[{\"href\":\"https://www.instagram.com/")
                    .append(usuarios[i]).append("\",\"value\":\"").append(usuarios[i]).append("\",\"timestamp\":")
                    .append(BASE + i * 60L).append("}]}");
        }
        return sb.append(']').toString();
    }

    private static String objetoComTitulo(String chave, String[] usuarios) {
        StringBuilder sb = new StringBuilder("{\"").append(chave).append("\":[");
        for (int i = 0; i < usuarios.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"title\":\"").append(usuarios[i])
                    .append("\",\"media_list_data\":[],\"string_list_data\":[{\"href\":\"https://www.instagram.com/_u/")
                    .append(usuarios[i]).append("\",\"timestamp\":").append(BASE + i * 60L).append("}]}");
        }
        return sb.append("]}").toString();
    }

    public static List<String> nomes(int quantidade, String prefixo) {
        return java.util.stream.IntStream.range(0, quantidade).mapToObj(i -> prefixo + i).toList();
    }
}
