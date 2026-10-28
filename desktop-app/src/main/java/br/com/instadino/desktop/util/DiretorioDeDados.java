package br.com.instadino.desktop.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Define onde ficam os dados locais. Variável de ambiente INSTADINO_DADOS substitui o padrão.
 *
 * <p>No Windows os dados ficam em {@code %LOCALAPPDATA%\InstaDinoDados}, <strong>fora</strong> da pasta de instalação
 * do programa ({@code %LOCALAPPDATA%\InstaDino}): atualizar ou desinstalar o aplicativo não deve tocar nos dados do
 * usuário. {@code LOCALAPPDATA} (e não {@code APPDATA}) porque a chave do banco protegida pela DPAPI não é
 * utilizável em outro computador.
 */
public final class DiretorioDeDados {

    static final String PASTA_NO_WINDOWS = "InstaDinoDados";

    private DiretorioDeDados() {
    }

    public static Path padrao() {
        return calcular(System::getenv, System.getProperty("os.name", ""), System.getProperty("user.home"));
    }

    static Path calcular(UnaryOperator<String> ambiente, String sistema, String casa) {
        String forcado = ambiente.apply("INSTADINO_DADOS");
        if (forcado != null && !forcado.isBlank()) {
            return Path.of(forcado);
        }
        if (sistema.toLowerCase(Locale.ROOT).startsWith("windows")) {
            String local = ambiente.apply("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                return Path.of(local, PASTA_NO_WINDOWS);
            }
        }
        String xdg = ambiente.apply("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(casa, ".local", "share");
        return base.resolve("instadino");
    }
}
