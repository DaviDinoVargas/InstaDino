package br.com.instadino.desktop.util;

import java.nio.file.Path;

/** Define onde ficam os dados locais. Variável de ambiente INSTADINO_DADOS substitui o padrão. */
public final class DiretorioDeDados {

    private DiretorioDeDados() {
    }

    public static Path padrao() {
        String forcado = System.getenv("INSTADINO_DADOS");
        if (forcado != null && !forcado.isBlank()) {
            return Path.of(forcado);
        }
        String so = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (so.startsWith("windows")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                return Path.of(local, "InstaDino");
            }
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("instadino");
    }
}
