package br.com.instadino.servidor.email;

import br.com.instadino.servidor.config.PropriedadesDoServidor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * SOMENTE DESENVOLVIMENTO: grava as mensagens em arquivos locais para que o desenvolvedor leia os tokens.
 * Não existe em produção, onde o envio real de e-mail ainda precisa ser implementado.
 */
@Component
@Profile("desenvolvimento")
class EnviadorDeEmailEmArquivo implements EnviadorDeEmail {

    private final Path pasta;

    EnviadorDeEmailEmArquivo(PropriedadesDoServidor propriedades) {
        this.pasta = propriedades.dados().resolve("emails-simulados");
    }

    @Override
    public void enviar(MensagemDeEmail mensagem) {
        try {
            Files.createDirectories(pasta);
            String nome = Instant.now().toEpochMilli() + "-" + mensagem.tipo().name().toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID() + ".txt";
            String texto = "Para: " + mensagem.para() + "\nAssunto: " + mensagem.assunto() + "\n\n" + mensagem.corpo()
                    + (mensagem.token() == null ? "" : "\n\nTOKEN: " + mensagem.token()) + "\n";
            Files.writeString(pasta.resolve(nome), texto, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar o e-mail simulado.");
        }
    }
}
