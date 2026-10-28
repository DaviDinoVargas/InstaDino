package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Rotação das senhas de demonstração: a senha antiga deixa de valer e a nova só existe no arquivo local. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles({"teste", "desenvolvimento"})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:semeadura-rotacao;DB_CLOSE_DELAY=-1",
    "instadino.dados=${java.io.tmpdir}/instadino-semeadura-rotacao",
    "instadino.semeadura.habilitada=true",
    "INSTADINO_SEMEADURA_REDEFINIR=true",
    "PAYMENTS_ENABLED=true", "PAYMENTS_MODE=sandbox"})
class SemeaduraRedefinicaoTest extends TesteBase {

    private static final Path ARQUIVO = Path.of(System.getProperty("java.io.tmpdir"), "instadino-semeadura-rotacao", "contas-semeadas.txt");

    @Autowired
    List<ApplicationRunner> executores;

    private static String ultimaSenha(String chave) throws Exception {
        String encontrada = null;
        for (String linha : Files.readAllLines(ARQUIVO)) {
            if (linha.startsWith(chave)) {
                encontrada = linha.split("\\|")[2].strip();
            }
        }
        return encontrada;
    }

    private MvcResult login(String email, String senha) throws Exception {
        return executar(postJson("/v1/sessoes", Map.of("email", email, "senha", senha, "idDoDispositivo", dispositivo())));
    }

    @Test
    void redefinirTrocaAsSenhasInvalidaAsAntigasENaoExpoeAsNovasNoLog(CapturedOutput saida) throws Exception {
        String antigaMaster = ultimaSenha("master");
        String antigaFree = ultimaSenha("free");
        assertThat(login("master@instadino.test", antigaMaster).getResponse().getStatus()).isEqualTo(200);

        executores.stream().filter(e -> e.getClass().getSimpleName().startsWith("SemeadorDeContas")).findFirst().orElseThrow().run(null);

        String novaMaster = ultimaSenha("master");
        String novaFree = ultimaSenha("free");
        assertThat(novaMaster).isNotEqualTo(antigaMaster).hasSize(20);
        assertThat(novaFree).isNotEqualTo(antigaFree).isNotEqualTo(novaMaster);
        assertThat(login("master@instadino.test", antigaMaster).getResponse().getStatus()).as("senha antiga invalidada").isEqualTo(401);
        assertThat(login("free@instadino.test", antigaFree).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("master@instadino.test", novaMaster).getResponse().getStatus()).isEqualTo(200);
        assertThat(saida.getAll()).doesNotContain(antigaMaster, novaMaster, novaFree);
    }
}
