package br.com.instadino.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verificação estática (DB-04): nenhum comando SQL do módulo pode ser montado concatenando variáveis.
 * Literais de texto unidos entre si são permitidos; qualquer {@code + identificador} em linha de SQL não é.
 */
class ConsultasParametrizadasTest {

    private static final Pattern CHAMADA_SQL = Pattern.compile("\\.(execute|executeQuery|executeUpdate|prepareStatement)\\(");
    private static final Pattern CONCATENA_VARIAVEL = Pattern.compile("\\+\\s*[A-Za-z_(]");

    @Test
    void nenhumSqlDeProducaoEMontadoPorConcatenacaoDeVariaveis() throws IOException {
        Path fontes = Path.of("src/main/java");
        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(fontes)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String codigo = Files.readString(arquivo);
                var chamadas = CHAMADA_SQL.matcher(codigo);
                while (chamadas.find()) {
                    String argumento = argumentoDaChamada(codigo, chamadas.end());
                    if (argumento.strip().equals("comando")) {
                        continue; // comandos fixos de migração, carregados de arquivos versionados e com checksum
                    }
                    if (CONCATENA_VARIAVEL.matcher(argumento).find()) {
                        int linha = codigo.substring(0, chamadas.start()).split("\n", -1).length;
                        violacoes.add(arquivo + ":" + linha + " -> " + argumento.strip());
                    }
                }
            }
        }
        assertThat(violacoes).as("SQL montado por concatenação").isEmpty();
    }

    @Test
    void oDetectorReconheceConcatenacaoDeVariaveis() {
        String ruim = "s.executeQuery(\"SELECT * FROM t WHERE a = '\" + entrada + \"'\");";
        var m = CHAMADA_SQL.matcher(ruim);
        assertThat(m.find()).isTrue();
        assertThat(CONCATENA_VARIAVEL.matcher(argumentoDaChamada(ruim, m.end())).find()).isTrue();

        String bom = "c.prepareStatement(\"SELECT * FROM t \" + \"WHERE a = ?\")";
        var b = CHAMADA_SQL.matcher(bom);
        assertThat(b.find()).isTrue();
        assertThat(CONCATENA_VARIAVEL.matcher(argumentoDaChamada(bom, b.end())).find()).isFalse();
    }

    /** Devolve o texto entre o parêntese de abertura (já consumido) e o parêntese que o fecha, respeitando literais. */
    private static String argumentoDaChamada(String codigo, int inicio) {
        int nivel = 1;
        boolean emTexto = false;
        for (int i = inicio; i < codigo.length(); i++) {
            char c = codigo.charAt(i);
            if (emTexto) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    emTexto = false;
                }
            } else if (c == '"') {
                emTexto = true;
            } else if (c == '(') {
                nivel++;
            } else if (c == ')' && --nivel == 0) {
                return codigo.substring(inicio, i);
            }
        }
        return codigo.substring(inicio);
    }
}
