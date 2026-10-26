package br.com.instadino.servidor.seguranca;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Política de senha: comprimento é o requisito principal; rejeita senhas triviais e derivadas do e-mail. */
public final class PoliticaDeSenha {

    public static final int MINIMO = 12;
    public static final int MAXIMO = 128;

    private static final Set<String> COMUNS = Set.of("123456789012", "password1234", "senha1234567", "qwertyuiop12", "1234567890123",
            "passwordpassword", "senhasenhasenha", "instadino1234", "instadino12345", "abcdefghijkl", "iloveyou1234", "letmein123456",
            "administrador", "administrator1", "mudar123456", "trocar123456");

    private PoliticaDeSenha() {
    }

    /** Devolve a mensagem de erro (segura para exibir) ou vazio se a senha é aceitável. */
    public static Optional<String> validar(String senha, String email) {
        if (senha == null || senha.length() < MINIMO) {
            return Optional.of("A senha deve ter pelo menos " + MINIMO + " caracteres.");
        }
        if (senha.length() > MAXIMO) {
            return Optional.of("A senha deve ter no máximo " + MAXIMO + " caracteres.");
        }
        String minuscula = senha.toLowerCase(Locale.ROOT);
        if (COMUNS.contains(minuscula) || senha.chars().distinct().count() < 4) {
            return Optional.of("Esta senha é muito previsível. Escolha outra.");
        }
        if (email != null) {
            String e = email.toLowerCase(Locale.ROOT);
            String local = e.contains("@") ? e.substring(0, e.indexOf('@')) : e;
            if (minuscula.equals(e) || (local.length() >= 6 && minuscula.contains(local))) {
                return Optional.of("A senha não pode conter o seu e-mail.");
            }
        }
        return Optional.empty();
    }
}
