package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PRI-01: nenhum dado da exportação do Instagram pode chegar ao backend. Garantia estrutural verificada nas classes
 * compiladas: este módulo não referencia os pacotes que importam, analisam ou guardam os dados do usuário.
 */
class ArquiteturaDePrivacidadeServidorTest {

    private static final List<String> PROIBIDOS = List.of("br/com/instadino/importacao/", "br/com/instadino/analise/", "br/com/instadino/servicos/", "br/com/instadino/persistencia/", "br/com/instadino/licenciamento/", "br/com/instadino/desktop/");

    @Test
    void oModuloNaoReferenciaOsPacotesQueTratamDadosDaExportacao() throws IOException {
        Path classes = Path.of("target", "classes");
        assertThat(classes).isDirectory();
        try (Stream<Path> arquivos = Files.walk(classes)) {
            List<String> violacoes = arquivos.filter(p -> p.toString().endsWith(".class"))
                    .flatMap(p -> referenciasProibidas(p, classes).stream()).toList();
            assertThat(violacoes).as("referências proibidas encontradas").isEmpty();
        }
    }

    @Test
    void oDetectorReconheceReferenciaProibida() {
        byte[] falsa = ("Lbr/com/instadino/" + "importacao/ImportadorDeExportacao;").getBytes(StandardCharsets.UTF_8);
        assertThat(PROIBIDOS.stream().anyMatch(p -> contem(falsa, p))).isTrue();
        assertThat(PROIBIDOS.stream().anyMatch(p -> contem("Ljava/lang/String;".getBytes(StandardCharsets.UTF_8), p))).isFalse();
    }

    private static List<String> referenciasProibidas(Path classe, Path raiz) {
        try {
            byte[] bytes = Files.readAllBytes(classe);
            return PROIBIDOS.stream().filter(p -> contem(bytes, p)).map(p -> raiz.relativize(classe) + " -> " + p).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + classe, e);
        }
    }

    private static boolean contem(byte[] bytes, String trecho) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(trecho);
    }
}
