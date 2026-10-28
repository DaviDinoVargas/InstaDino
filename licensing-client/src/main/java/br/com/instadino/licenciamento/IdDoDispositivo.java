package br.com.instadino.licenciamento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Identificador aleatório da instalação, gerado no primeiro uso. Não contém informações do computador. */
public final class IdDoDispositivo {

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private IdDoDispositivo() {
    }

    public static String obterOuCriar(Path arquivo) {
        try {
            if (Files.isRegularFile(arquivo)) {
                String existente = Files.readString(arquivo, StandardCharsets.UTF_8).strip();
                if (VALIDO.matcher(existente).matches()) {
                    return existente;
                }
            }
            Files.createDirectories(arquivo.toAbsolutePath().getParent());
            String novo = UUID.randomUUID().toString();
            Files.writeString(arquivo, novo, StandardCharsets.UTF_8);
            return novo;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível guardar o identificador do dispositivo.");
        }
    }
}
