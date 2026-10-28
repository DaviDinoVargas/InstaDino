package br.com.instadino.persistencia;

import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HexFormat;

/** Processo auxiliar do teste de recuperação: grava, abre uma transação e morre abruptamente. */
public final class QuedaSimulada {

    private QuedaSimulada() {
    }

    public static void main(String[] args) throws Exception {
        byte[] chave = HexFormat.of().parseHex(args[1]);
        BancoLocal banco = BancoLocal.abrir(Path.of(args[0]), chave, CarregadorDeMigracoes.doClasspath());
        banco.emTransacao(c -> {
            try (var s = c.createStatement()) {
                s.execute("INSERT INTO preferencia (chave, valor) VALUES ('confirmada', '1')");
            }
            return null;
        });
        Connection c = banco.conexao();
        try (var s = c.createStatement()) {
            s.execute("BEGIN IMMEDIATE");
            s.execute("INSERT INTO preferencia (chave, valor) VALUES ('pendente', '1')");
        }
        Runtime.getRuntime().halt(137);
    }
}
