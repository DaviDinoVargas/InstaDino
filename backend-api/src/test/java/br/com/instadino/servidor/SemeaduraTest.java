package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.servico.AssinadorDeLicencas;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles({"teste", "desenvolvimento"})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:semeadura;DB_CLOSE_DELAY=-1",
    "instadino.dados=${java.io.tmpdir}/instadino-semeadura-teste",
    "instadino.semeadura.habilitada=true",
    "INSTADINO_SENHA_PREMIUM=Chave-Longa-De-Teste-7!",
    "PAYMENTS_ENABLED=true", "PAYMENTS_MODE=sandbox"})
class SemeaduraTest extends TesteBase {

    @Autowired
    AssinadorDeLicencas assinador;

    private static final Path ARQUIVO = Path.of(System.getProperty("java.io.tmpdir"), "instadino-semeadura-teste", "contas-semeadas.txt");

    private String senhaDoArquivo(String chave) throws Exception {
        // o arquivo é acrescido a cada semeadura; vale a ÚLTIMA ocorrência (a das contas criadas nesta execução)
        String encontrada = null;
        for (String linha : Files.readAllLines(ARQUIVO)) {
            if (linha.startsWith(chave)) {
                encontrada = linha.split("\\|")[2].strip();
            }
        }
        if (encontrada == null) {
            throw new AssertionError("Conta não encontrada no arquivo: " + chave);
        }
        return encontrada;
    }

    private MvcResult login(String email, String senha) throws Exception {
        return executar(postJson("/v1/sessoes", Map.of("email", email, "senha", senha, "idDoDispositivo", dispositivo())));
    }

    @Test
    void asTresContasExistemEstaoVerificadasETemOsPapeisCorretos() throws Exception {
        assertThat(contas.findByEmailNormalizado("master@instadino.test").orElseThrow().getPapel()).isEqualTo(Papel.MASTER);
        assertThat(contas.findByEmailNormalizado("premium@instadino.test").orElseThrow().getPapel()).isEqualTo(Papel.CLIENTE);
        assertThat(contas.findByEmailNormalizado("free@instadino.test").orElseThrow().getPapel()).isEqualTo(Papel.CLIENTE);
        for (String e : new String[] {"master@instadino.test", "premium@instadino.test", "free@instadino.test"}) {
            assertThat(contas.findByEmailNormalizado(e).orElseThrow().isEmailVerificado()).isTrue();
        }
    }

    @Test
    void senhaDefinidaPeloAmbienteFuncionaEAsGeradasSaoFortesEDiferentes() throws Exception {
        assertThat(login("premium@instadino.test", "Chave-Longa-De-Teste-7!").getResponse().getStatus()).isEqualTo(200);
        String master = senhaDoArquivo("master");
        String free = senhaDoArquivo("free");
        assertThat(master).hasSize(20).isNotEqualTo(free);
        assertThat(login("master@instadino.test", master).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("free@instadino.test", free).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("free@instadino.test", master).getResponse().getStatus()).as("senha de outra conta").isEqualTo(401);
    }

    @Test
    void masterTemAcessoTotalPremiumTemLicencaEFreeNaoTem() throws Exception {
        String tokenMaster = entrar("master@instadino.test", senhaDoArquivo("master"));
        String tokenPremium = entrar("premium@instadino.test", "Chave-Longa-De-Teste-7!");
        String tokenFree = entrar("free@instadino.test", senhaDoArquivo("free"));

        assertThat(executar(obter("/v1/admin/contas", tokenMaster)).getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(executar(obter("/v1/admin/contas", tokenMaster)))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(executar(obter("/v1/admin/contas", tokenPremium)).getResponse().getStatus()).isEqualTo(403);
        assertThat(executar(obter("/v1/admin/contas", tokenFree)).getResponse().getStatus()).isEqualTo(403);

        var licenca = Map.of("idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC");
        assertThat(executar(comToken(postJson("/v1/licencas", licenca), tokenMaster)).getResponse().getStatus()).isEqualTo(200);
        assertThat(executar(comToken(postJson("/v1/licencas", licenca), tokenPremium)).getResponse().getStatus()).isEqualTo(200);
        assertThat(executar(comToken(postJson("/v1/licencas", licenca), tokenFree)).getResponse().getStatus()).isEqualTo(403);
        assertThat(corpo(executar(obter("/v1/assinatura", tokenPremium))).get("origem").asText()).isEqualTo("CONCESSAO_ADMINISTRATIVA");
        assertThat(corpo(executar(obter("/v1/assinatura", tokenMaster))).get("origem").asText()).isEqualTo("PAPEL_MASTER");
    }

    @Test
    void asSenhasNuncaApareceNoLogENoArquivoSoODonoLe(CapturedOutput saida) throws Exception {
        assertThat(saida.getAll()).doesNotContain(senhaDoArquivo("master"), senhaDoArquivo("free"), "Chave-Longa-De-Teste-7!");
        assertThat(saida.getAll()).contains("Contas de demonstração criadas");
        try {
            assertThat(Files.getPosixFilePermissions(ARQUIVO)).containsExactlyInAnyOrder(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        } catch (UnsupportedOperationException e) {
            // Windows
        }
    }

    @Test
    void oPlanoDeTesteEstaClaramenteIdentificadoComoFicticio() throws Exception {
        var catalogo = corpo(executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/planos")));
        boolean achou = false;
        for (var p : catalogo.get("planos")) {
            if (p.get("id").asText().equals("premium-mensal-sandbox")) {
                assertThat(p.get("nome").asText()).contains("PREÇO DE TESTE");
                achou = true;
            }
        }
        assertThat(achou).isTrue();
    }

    @Test
    void asContasSemeadasContinuamSujeitasAoBloqueioPorTentativas() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("free@instadino.test", "errada-errada-errada-" + i);
        }
        assertThat(login("free@instadino.test", senhaDoArquivo("free")).getResponse().getStatus()).isEqualTo(401);
        relogio.avancar(java.time.Duration.ofMinutes(16));
        assertThat(login("free@instadino.test", senhaDoArquivo("free")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void chaveDeLicencaDeDesenvolvimentoEGeradaForaDoRepositorio() {
        assertThat(assinador.chavePublica()).isNotNull();
        assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"), "instadino-semeadura-teste", "chaves-dev", "licenca-dev.pub"))).isTrue();
    }
}
