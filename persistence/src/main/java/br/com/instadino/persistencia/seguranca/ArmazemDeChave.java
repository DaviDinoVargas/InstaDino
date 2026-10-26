package br.com.instadino.persistencia.seguranca;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Gera e guarda a chave de 256 bits do banco local. O arquivo da chave fica FORA do banco e contém apenas
 * o segredo protegido pelo {@link ProtetorDeSegredo}. Não há chave embutida no código.
 */
public final class ArmazemDeChave {

    public static final int TAMANHO_CHAVE_BYTES = 32;

    private final Path arquivo;
    private final ProtetorDeSegredo protetor;
    private final SecureRandom aleatorio = new SecureRandom();

    public ArmazemDeChave(Path arquivo, ProtetorDeSegredo protetor) {
        this.arquivo = Objects.requireNonNull(arquivo, "arquivo");
        this.protetor = Objects.requireNonNull(protetor, "protetor");
    }

    public boolean existe() {
        return Files.isRegularFile(arquivo);
    }

    public ProtetorDeSegredo protetor() {
        return protetor;
    }

    /** Devolve a chave existente ou cria uma nova. O chamador deve zerar o array depois do uso. */
    public synchronized byte[] obterOuCriar() {
        try {
            if (existe()) {
                byte[] protegido = Files.readAllBytes(arquivo);
                byte[] chave = protetor.revelar(protegido);
                if (chave.length != TAMANHO_CHAVE_BYTES) {
                    Arrays.fill(chave, (byte) 0);
                    throw new ProtecaoIndisponivelException("O arquivo de chave está corrompido.");
                }
                return chave;
            }
            byte[] chave = new byte[TAMANHO_CHAVE_BYTES];
            aleatorio.nextBytes(chave);
            gravarAtomico(protetor.proteger(chave));
            return chave;
        } catch (IOException e) {
            throw new ProtecaoIndisponivelException("Não foi possível acessar o arquivo de chave.", e);
        }
    }

    private void gravarAtomico(byte[] protegido) throws IOException {
        Path pasta = arquivo.toAbsolutePath().getParent();
        Files.createDirectories(pasta);
        Path temporario = Files.createTempFile(pasta, "chave-", ".tmp");
        try {
            restringirPermissoes(temporario);
            Files.write(temporario, protegido);
            try {
                Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    static void restringirPermissoes(Path caminho) {
        try {
            Files.setPosixFilePermissions(caminho, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Sistemas sem POSIX (Windows): a proteção vem da DPAPI e das ACLs do perfil do usuário.
        }
    }
}
