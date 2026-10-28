package br.com.instadino.persistencia.migracao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Uma migração versionada: lista de comandos SQL e o checksum do texto original. */
public record Migracao(int versao, String descricao, List<String> comandos, String checksum) {

    public Migracao {
        if (versao < 1) {
            throw new IllegalArgumentException("Versão de migração inválida.");
        }
        comandos = List.copyOf(comandos);
    }

    /** Cria a migração a partir do texto SQL. Os comandos são separados por ponto e vírgula no fim da linha. */
    public static Migracao deTexto(int versao, String descricao, String sql) {
        String normalizado = sql.replace("\r\n", "\n");
        return new Migracao(versao, descricao, dividir(normalizado), sha256(normalizado));
    }

    static List<String> dividir(String sql) {
        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String linha : sql.split("\n", -1)) {
            String aparada = linha.strip();
            if (aparada.startsWith("--")) {
                continue;
            }
            atual.append(linha).append('\n');
            if (aparada.endsWith(";")) {
                String comando = atual.toString().strip();
                comandos.add(comando.substring(0, comando.length() - 1));
                atual.setLength(0);
            }
        }
        if (!atual.toString().isBlank()) {
            throw new IllegalArgumentException("Migração termina com comando sem ponto e vírgula.");
        }
        return comandos;
    }

    static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
