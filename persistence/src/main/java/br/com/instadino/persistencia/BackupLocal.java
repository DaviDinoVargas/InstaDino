package br.com.instadino.persistencia;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Backup e restauração do banco cifrado. O backup herda a cifragem e a chave do banco de origem. */
public final class BackupLocal {

    private BackupLocal() {
    }

    /** Cria uma cópia consistente e cifrada em {@code destino}, que não pode existir. */
    public static void criar(BancoLocal banco, Path destino) {
        Path alvo = destino.toAbsolutePath();
        if (Files.exists(alvo)) {
            throw new BancoException("O arquivo de backup já existe.");
        }
        try (Connection c = banco.conexao(); PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
            p.setString(1, alvo.toString());
            p.execute();
        } catch (SQLException e) {
            throw new BancoException("Não foi possível criar o backup.", e.getErrorCode());
        }
    }

    /**
     * Restaura {@code backup} em {@code bancoDestino}. O backup é aberto com a chave e verificado antes de qualquer
     * alteração; o banco anterior é preservado com o sufixo {@code .antes}. O banco de destino deve estar fechado.
     */
    public static void restaurar(Path backup, Path bancoDestino, byte[] chave) {
        verificarBackup(backup, chave);
        Path destino = bancoDestino.toAbsolutePath();
        try {
            Files.createDirectories(destino.getParent());
            // a cópia (etapa que pode falhar por falta de espaço) é feita ANTES de tocar no banco atual
            Path temporario = Files.createTempFile(destino.getParent(), "restauracao-", ".tmp");
            try {
                Files.copy(backup, temporario, StandardCopyOption.REPLACE_EXISTING);
                if (Files.exists(destino)) {
                    Files.move(destino, Path.of(destino + ".antes"), StandardCopyOption.REPLACE_EXISTING);
                }
                for (String sufixo : new String[] {"-wal", "-shm"}) {
                    Files.deleteIfExists(Path.of(destino + sufixo));
                }
                try {
                    Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException e) {
            throw new BancoException("Não foi possível restaurar o backup.");
        }
    }

    /** Verifica o backup em uma cópia temporária (cifrada) que é sempre removida. */
    private static void verificarBackup(Path backup, byte[] chave) {
        Path pasta = null;
        try {
            pasta = Files.createTempDirectory("verificacao-backup-");
            Path copia = pasta.resolve("copia.db");
            Files.copy(backup, copia);
            try (BancoLocal teste = BancoLocal.abrirParaVerificacao(copia, chave)) {
                if (!teste.verificarIntegridade().isEmpty()) {
                    throw new BancoException("O backup está corrompido; nada foi alterado.");
                }
            }
        } catch (IOException e) {
            throw new BancoException("Não foi possível verificar o backup.");
        } finally {
            if (pasta != null) {
                BancoLocal.excluirArquivos(pasta.resolve("copia.db"));
                try {
                    Files.deleteIfExists(pasta);
                } catch (IOException ignorada) {
                    // diretório temporário vazio; o sistema o limpa depois
                }
            }
        }
    }
}
