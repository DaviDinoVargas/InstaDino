package br.com.instadino.nucleo.dominio;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nome de usuário do Instagram normalizado: minúsculas, sem espaços nem "@", restrito a letras ASCII, números,
 * ponto e sublinhado, com 1 a 30 caracteres. Nomes de usuário são tratados sem distinção de maiúsculas.
 */
public record NomeDeUsuario(String valor) implements Comparable<NomeDeUsuario> {

    private static final Pattern VALIDO = Pattern.compile("[a-z0-9._]{1,30}");

    public NomeDeUsuario {
        if (valor == null || !VALIDO.matcher(valor).matches()) {
            throw new IllegalArgumentException("Nome de usuário inválido.");
        }
    }

    /** Normaliza e valida; devolve vazio se o texto não for um nome de usuário aceitável. */
    public static Optional<NomeDeUsuario> tentar(String bruto) {
        if (bruto == null) {
            return Optional.empty();
        }
        String texto = bruto.strip();
        if (texto.startsWith("@")) {
            texto = texto.substring(1);
        }
        texto = texto.toLowerCase(Locale.ROOT);
        return VALIDO.matcher(texto).matches() ? Optional.of(new NomeDeUsuario(texto)) : Optional.empty();
    }

    /** Extrai o nome de usuário de uma URL de perfil (instagram.com/usuario ou instagram.com/_u/usuario). */
    public static Optional<NomeDeUsuario> deUrlDePerfil(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String u = url.strip();
        int esquema = u.indexOf("://");
        if (esquema >= 0) {
            u = u.substring(esquema + 3);
        }
        int barra = u.indexOf('/');
        if (barra < 0) {
            return Optional.empty();
        }
        String host = u.substring(0, barra).toLowerCase(Locale.ROOT);
        if (!host.equals("instagram.com") && !host.endsWith(".instagram.com")) {
            return Optional.empty();
        }
        String[] partes = u.substring(barra + 1).split("[/?#]");
        int i = 0;
        if (partes.length > 0 && partes[0].equals("_u")) {
            i = 1;
        }
        return i < partes.length ? tentar(partes[i]) : Optional.empty();
    }

    /** Endereço do perfil, usado para abrir no navegador. Sempre reconstruído a partir do nome validado. */
    public String urlDoPerfil() {
        return "https://www.instagram.com/" + valor + "/";
    }

    @Override
    public int compareTo(NomeDeUsuario outro) {
        return valor.compareTo(outro.valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
