package br.com.instadino.persistencia.migracao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carrega migrações do classpath a partir de {@code db/migracoes/indice.txt}, que lista um arquivo por linha
 * no formato {@code V001__descricao.sql}. O índice evita listar diretórios dentro de JARs.
 */
public final class CarregadorDeMigracoes {

    private static final Pattern NOME = Pattern.compile("V(\\d{3})__([a-z0-9_]+)\\.sql");

    private CarregadorDeMigracoes() {
    }

    public static List<Migracao> doClasspath() {
        return doClasspath("db/migracoes");
    }

    public static List<Migracao> doClasspath(String pasta) {
        String indice = ler(pasta + "/indice.txt");
        List<Migracao> migracoes = new ArrayList<>();
        for (String linha : indice.split("\n")) {
            String nome = linha.strip();
            if (nome.isEmpty() || nome.startsWith("#")) {
                continue;
            }
            Matcher m = NOME.matcher(nome);
            if (!m.matches()) {
                throw new MigracaoException("Nome de migração inválido no índice.");
            }
            migracoes.add(Migracao.deTexto(Integer.parseInt(m.group(1)), m.group(2), ler(pasta + "/" + nome)));
        }
        return migracoes;
    }

    private static String ler(String recurso) {
        try (InputStream in = CarregadorDeMigracoes.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new MigracaoException("Recurso de migração ausente.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MigracaoException("Falha ao ler migração.");
        }
    }
}
