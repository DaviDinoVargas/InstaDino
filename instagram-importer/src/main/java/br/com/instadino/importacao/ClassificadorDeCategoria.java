package br.com.instadino.importacao;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Descobre a categoria de um arquivo a partir do caminho dentro da exportação. A exportação não é estável entre
 * versões; por isso a classificação é por trechos do caminho e nomes de arquivo, e o que não for reconhecido
 * vira {@link CategoriaDeDados#DESCONHECIDA} (inventariado, nunca interpretado).
 */
public final class ClassificadorDeCategoria {

    private static final Pattern SEGUIDORES = Pattern.compile("(^|/)followers(_\\d+)?\\.(json|html)$");
    private static final Pattern SEGUINDO = Pattern.compile("(^|/)following(_\\d+)?\\.(json|html)$");
    private static final Pattern PENDENTES = Pattern.compile("(^|/)pending_follow_requests(_\\d+)?\\.(json|html)$");
    private static final Pattern BLOQUEADOS = Pattern.compile("(^|/)blocked_profiles(_\\d+)?\\.(json|html)$");
    private static final Pattern RESTRITOS = Pattern.compile("(^|/)restricted_profiles(_\\d+)?\\.(json|html)$");
    private static final Pattern FAVORITOS = Pattern.compile("(^|/)close_friends(_\\d+)?\\.(json|html)$");
    private static final Pattern OUTRAS_LISTAS = Pattern.compile(
            "(^|/)(recently_unfollowed_profiles|recent_follow_requests|removed_suggestions|following_hashtags)(_\\d+)?\\.(json|html)$");

    private ClassificadorDeCategoria() {
    }

    public static CategoriaDeDados classificar(String caminhoNormalizado) {
        String c = caminhoNormalizado.toLowerCase(Locale.ROOT);
        if (SEGUIDORES.matcher(c).find()) {
            return CategoriaDeDados.SEGUIDORES;
        }
        if (SEGUINDO.matcher(c).find()) {
            return CategoriaDeDados.SEGUINDO;
        }
        if (PENDENTES.matcher(c).find()) {
            return CategoriaDeDados.SOLICITACOES_PENDENTES;
        }
        if (BLOQUEADOS.matcher(c).find()) {
            return CategoriaDeDados.BLOQUEADOS;
        }
        if (RESTRITOS.matcher(c).find()) {
            return CategoriaDeDados.RESTRITOS;
        }
        if (FAVORITOS.matcher(c).find()) {
            return CategoriaDeDados.FAVORITOS;
        }
        if (OUTRAS_LISTAS.matcher(c).find() || c.contains("followers_and_following/")) {
            return CategoriaDeDados.RELACIONAMENTOS_OUTROS;
        }
        if (FormatoDeArquivo.deCaminho(c) == FormatoDeArquivo.MIDIA) {
            return CategoriaDeDados.MIDIA;
        }
        if (c.contains("/messages/") || c.startsWith("messages/")) {
            return CategoriaDeDados.MENSAGENS;
        }
        if (c.contains("security_and_login_information") || c.contains("login_activity") || c.contains("login_and_account_creation")) {
            return CategoriaDeDados.SEGURANCA;
        }
        if (c.contains("personal_information") || c.contains("account_information")) {
            return CategoriaDeDados.PERFIL;
        }
        if (c.contains("/likes/") || c.startsWith("likes/")) {
            return CategoriaDeDados.CURTIDAS;
        }
        if (c.contains("/comments/") || c.startsWith("comments/")) {
            return CategoriaDeDados.COMENTARIOS;
        }
        if (c.contains("/saved/") || c.startsWith("saved/")) {
            return CategoriaDeDados.SALVOS;
        }
        if (c.contains("recent_searches") || c.contains("account_searches") || c.contains("word_or_phrase_searches")) {
            return CategoriaDeDados.PESQUISAS;
        }
        if (c.contains("/reels") || c.contains("reels.json")) {
            return CategoriaDeDados.REELS;
        }
        if (c.contains("stories") || c.contains("archived_posts")) {
            return CategoriaDeDados.STORIES;
        }
        if (c.contains("/media/") && (c.contains("posts_") || c.contains("/posts"))) {
            return CategoriaDeDados.PUBLICACOES;
        }
        if (c.contains("ads_information") || c.contains("preferences") || c.contains("your_topics") || c.contains("inferred_topics")) {
            return CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS;
        }
        return CategoriaDeDados.DESCONHECIDA;
    }
}
