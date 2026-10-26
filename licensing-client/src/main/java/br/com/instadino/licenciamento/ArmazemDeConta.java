package br.com.instadino.licenciamento;

import br.com.instadino.persistencia.seguranca.ProtetorDeSegredo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Guarda a sessão comercial e a licença no disco, protegidas pelo mesmo mecanismo da chave do banco (DPAPI).
 * Se o arquivo estiver adulterado, ilegível ou protegido por outro usuário, é tratado como ausente.
 */
public final class ArmazemDeConta {

    private final Path arquivo;
    private final ProtetorDeSegredo protetor;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ArmazemDeConta(Path arquivo, ProtetorDeSegredo protetor) {
        this.arquivo = arquivo;
        this.protetor = protetor;
    }

    public Optional<DadosDaConta> ler() {
        try {
            if (!Files.isRegularFile(arquivo)) {
                return Optional.empty();
            }
            byte[] claro = protetor.revelar(Files.readAllBytes(arquivo));
            return Optional.ofNullable(json.readValue(claro, DadosDaConta.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public void gravar(DadosDaConta dados) {
        try {
            Path pasta = arquivo.toAbsolutePath().getParent();
            Files.createDirectories(pasta);
            Path temporario = Files.createTempFile(pasta, "conta-", ".tmp");
            try {
                restringir(temporario);
                Files.write(temporario, protetor.proteger(json.writeValueAsBytes(dados)));
                try {
                    Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível guardar os dados da conta.");
        }
    }

    public void apagar() {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível remover os dados da conta.");
        }
    }

    private static void restringir(Path p) {
        try {
            Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows: a proteção vem da DPAPI e do perfil do usuário
        }
    }
}
