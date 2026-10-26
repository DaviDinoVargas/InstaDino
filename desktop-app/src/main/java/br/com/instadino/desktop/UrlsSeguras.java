package br.com.instadino.desktop;

import java.util.regex.Pattern;

/** Só permite abrir no navegador endereços de perfil do Instagram com o formato exato que o aplicativo gera. */
public final class UrlsSeguras {

    private static final Pattern PERFIL = Pattern.compile("https://www\\.instagram\\.com/[a-z0-9._]{1,30}/");

    private UrlsSeguras() {
    }

    /** Página de pagamento hospedada pelo provedor: HTTPS, com nome de domínio, sem credenciais embutidas e fora de domínios de teste. */
    public static boolean paginaDePagamento(String url) {
        if (url == null || url.length() > 2048) {
            return false;
        }
        try {
            java.net.URI u = java.net.URI.create(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equals(u.getScheme()) && u.getUserInfo() == null && host.contains(".") && !host.endsWith(".invalid")
                    && !host.endsWith(".test") && !host.endsWith(".localhost") && !host.matches("[0-9.]+");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean perfilDoInstagram(String url) {
        return url != null && PERFIL.matcher(url).matches();
    }
}
