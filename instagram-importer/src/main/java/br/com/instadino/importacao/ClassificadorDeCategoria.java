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
    private static final Pattern DEIXEI_DE_SEGUIR = Pattern.compile("(^|/)recently_unfollowed_(profiles|accounts)(_\\d+)?\\.(json|html)$");
    private static final Pattern SOLICITACOES_RECENTES = Pattern.compile("(^|/)recent_follow_requests(_\\d+)?\\.(json|html)$");
    // o nome real contém um apóstrofo (reto ou tipográfico): follow_requests_you've_received.json
    private static final Pattern SOLICITACOES_RECEBIDAS = Pattern.compile("(^|/)follow_requests_you.ve_received(_\\d+)?\\.(json|html)$");
    private static final Pattern VISUALIZACOES = Pattern.compile(
            "(^|/)(posts_viewed|videos_watched|stories_viewed|suggested_accounts_viewed|profiles_you.ve_viewed)(_\\d+)?\\.(json|html)$");
    private static final Pattern OUTRAS_LISTAS = Pattern.compile(
            "(^|/)(removed_suggestions|following_hashtags)(_\\d+)?\\.(json|html)$");

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
        if (DEIXEI_DE_SEGUIR.matcher(c).find()) {
            return CategoriaDeDados.DEIXEI_DE_SEGUIR;
        }
        if (SOLICITACOES_RECENTES.matcher(c).find()) {
            return CategoriaDeDados.SOLICITACOES_RECENTES;
        }
        if (SOLICITACOES_RECEBIDAS.matcher(c).find()) {
            return CategoriaDeDados.SOLICITACOES_RECEBIDAS;
        }
        if (OUTRAS_LISTAS.matcher(c).find() || c.contains("followers_and_following/")) {
            return CategoriaDeDados.RELACIONAMENTOS_OUTROS;
        }
        if (c.contains("story_interactions")) {
            return CategoriaDeDados.INTERACOES_STORIES;
        }
        if (VISUALIZACOES.matcher(c).find()) {
            return CategoriaDeDados.VISUALIZACOES;
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
        if (c.contains("recent_searches") || c.contains("account_searches") || c.contains("word_or_phrase_searches") || c.contains("profile_searches")) {
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
