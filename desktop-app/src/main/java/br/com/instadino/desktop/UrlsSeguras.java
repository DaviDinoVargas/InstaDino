package br.com.instadino.desktop;

import java.util.regex.Pattern;

/** Só permite abrir no navegador endereços de perfil do Instagram com o formato exato que o aplicativo gera. */
public final class UrlsSeguras {

    private static final Pattern PERFIL = Pattern.compile("https://www\\.instagram\\.com/[a-z0-9._]{1,30}/");

    private UrlsSeguras() {
    }

    public static boolean perfilDoInstagram(String url) {
        return url != null && PERFIL.matcher(url).matches();
    }
}
