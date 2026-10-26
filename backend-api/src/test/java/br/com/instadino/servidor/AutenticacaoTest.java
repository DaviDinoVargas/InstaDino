package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.email.MensagemDeEmail.Tipo;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
class AutenticacaoTest extends TesteBase {

    private MvcResult cadastrar(String email, String senha) throws Exception {
        return executar(postJson("/v1/contas", Map.of("email", email, "senha", senha, "nome", "Maria Teste")));
    }

    private MvcResult login(String email, String senha) throws Exception {
        return executar(postJson("/v1/sessoes", Map.of("email", email, "senha", senha, "idDoDispositivo", dispositivo())));
    }

    // ---- cadastro e verificação ----

    @Test
    void cadastroExigeVerificarOEmailAntesDoLogin() throws Exception {
        String email = emailUnico();
        assertThat(cadastrar(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(202);

        MvcResult antes = login(email, SENHA_FORTE);
        assertThat(antes.getResponse().getStatus()).isEqualTo(403);
        assertThat(corpo(antes).get("codigo").asText()).isEqualTo("EMAIL_NAO_VERIFICADO");

        String token = tokenDoEmail(email, Tipo.VERIFICACAO_EMAIL);
        assertThat(executar(postJson("/v1/contas/verificar-email", Map.of("token", token))).getResponse().getStatus()).isEqualTo(204);
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void tokenDeVerificacaoSoPodeSerUsadoUmaVez() throws Exception {
        String email = emailUnico();
        cadastrar(email, SENHA_FORTE);
        String token = tokenDoEmail(email, Tipo.VERIFICACAO_EMAIL);
        assertThat(executar(postJson("/v1/contas/verificar-email", Map.of("token", token))).getResponse().getStatus()).isEqualTo(204);
        assertThat(executar(postJson("/v1/contas/verificar-email", Map.of("token", token))).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void tokenDeVerificacaoExpiraEmVinteEQuatroHoras() throws Exception {
        String email = emailUnico();
        cadastrar(email, SENHA_FORTE);
        String token = tokenDoEmail(email, Tipo.VERIFICACAO_EMAIL);
        relogio.avancar(Duration.ofHours(25));
        assertThat(executar(postJson("/v1/contas/verificar-email", Map.of("token", token))).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void tokenDeRecuperacaoNaoServeParaVerificarEmail() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String tokenDeRecuperacao = tokenDoEmail(email, Tipo.RECUPERACAO_SENHA);
        assertThat(executar(postJson("/v1/contas/verificar-email", Map.of("token", tokenDeRecuperacao))).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void cadastroComEmailExistenteTemRespostaIdenticaENaoCriaOutraConta() throws Exception {
        String email = emailUnico();
        MvcResult novo = cadastrar(email, SENHA_FORTE);
        MvcResult repetido = cadastrar(email, SENHA_FORTE + "x");
        assertThat(repetido.getResponse().getStatus()).isEqualTo(novo.getResponse().getStatus());
        assertThat(repetido.getResponse().getContentAsString()).isEqualTo(novo.getResponse().getContentAsString());
        assertThat(contas.findByEmailNormalizado(email)).isPresent();
        assertThat(emails.quantos(email, Tipo.AVISO_DE_CADASTRO_EXISTENTE)).isEqualTo(1);
    }

    @Test
    void cadastroNormalizaOEmailECapturaDuplicataPorCaixa() throws Exception {
        String email = emailUnico();
        cadastrar(email, SENHA_FORTE);
        cadastrar("  " + email.toUpperCase() + " ", SENHA_FORTE);
        assertThat(emails.quantos(email, Tipo.AVISO_DE_CADASTRO_EXISTENTE)).isEqualTo(1);
    }

    @Test
    void cadastroRejeitaDadosInvalidos() throws Exception {
        assertThat(cadastrar("nao-e-email", SENHA_FORTE).getResponse().getStatus()).isEqualTo(400);
        assertThat(cadastrar("a@b", SENHA_FORTE).getResponse().getStatus()).isEqualTo(400);
        assertThat(cadastrar(emailUnico(), "curta").getResponse().getStatus()).isEqualTo(400);
        assertThat(cadastrar(emailUnico(), "senhasenhasenha").getResponse().getStatus()).isEqualTo(400);
        assertThat(cadastrar(emailUnico(), "a".repeat(129)).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(postJson("/v1/contas", Map.of("email", emailUnico(), "senha", SENHA_FORTE, "nome", " "))).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(postJson("/v1/contas", "{ não é json")).getResponse().getStatus()).isEqualTo(400);
        String e = emailUnico();
        assertThat(cadastrar(e, e).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void cadastroNuncaAceitaDefinirOPapel() throws Exception {
        String email = emailUnico();
        executar(postJson("/v1/contas", Map.of("email", email, "senha", SENHA_FORTE, "nome", "Maria", "papel", "MASTER")));
        assertThat(contas.findByEmailNormalizado(email).orElseThrow().getPapel()).isEqualTo(Papel.CLIENTE);
    }

    // ---- login ----

    @Test
    void loginDevolveTokenOpacoEIdentidadeDoServidor() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        MvcResult r = login(email, SENHA_FORTE);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String token = corpo(r).get("token").asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40).matches("[A-Za-z0-9_-]+");
        assertThat(corpo(r).get("conta").get("papel").asText()).isEqualTo("CLIENTE");
        MvcResult conta = executar(obter("/v1/conta", token));
        assertThat(conta.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(conta).get("email").asText()).isEqualTo(email);
    }

    @Test
    void erroDeCredenciaisNaoRevelaSeAContaExiste() throws Exception {
        String existente = emailUnico();
        criarConta(existente, SENHA_FORTE, Papel.CLIENTE, true);
        MvcResult senhaErrada = login(existente, "senha-errada-qualquer-1");
        MvcResult inexistente = login(emailUnico(), "senha-errada-qualquer-1");
        assertThat(senhaErrada.getResponse().getStatus()).isEqualTo(401).isEqualTo(inexistente.getResponse().getStatus());
        assertThat(senhaErrada.getResponse().getContentAsString()).isEqualTo(inexistente.getResponse().getContentAsString());
    }

    @Test
    void bloqueioProgressivoAposVariasFalhasMesmoComASenhaCerta() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "errada-errada-errada-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult bloqueado = login(email, SENHA_FORTE);
        assertThat(bloqueado.getResponse().getStatus()).as("senha correta durante o bloqueio").isEqualTo(401);
        MvcResult inexistente = login(emailUnico(), SENHA_FORTE);
        assertThat(bloqueado.getResponse().getContentAsString()).as("o bloqueio não é distinguível de credencial inválida")
                .isEqualTo(inexistente.getResponse().getContentAsString());
        relogio.avancar(Duration.ofMinutes(16));
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void bloqueioDobraACadaNovaFalhaAposOLimite() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        for (int i = 0; i < 5; i++) {
            login(email, "errada-errada-errada-" + i);
        }
        relogio.avancar(Duration.ofMinutes(16));
        login(email, "errada-errada-errada-x"); // 6ª falha: bloqueio de 30 minutos
        relogio.avancar(Duration.ofMinutes(20));
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).as("ainda bloqueada").isEqualTo(401);
        relogio.avancar(Duration.ofMinutes(11));
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void loginComSucessoZeraOContadorDeFalhas() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        for (int i = 0; i < 4; i++) {
            login(email, "errada-errada-errada-" + i);
        }
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(200);
        assertThat(contas.findByEmailNormalizado(email).orElseThrow().getTentativasFalhas()).isZero();
    }

    @Test
    void contaDesativadaNaoEntra() throws Exception {
        String email = emailUnico();
        var conta = criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        conta.desativar(relogio.instant());
        contas.save(conta);
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void loginComDadosAusentesOuMalformadosFalhaDeFormaUniforme() throws Exception {
        for (Object corpo : new Object[] {Map.of(), Map.of("email", emailUnico()), Map.of("email", "x", "senha", "y"),
            Map.of("email", emailUnico(), "senha", "abc", "idDoDispositivo", "curto"), "{}"}) {
            assertThat(executar(postJson("/v1/sessoes", corpo)).getResponse().getStatus()).isEqualTo(401);
        }
    }

    // ---- armazenamento de senha ----

    @Test
    void senhasSaoGuardadasComArgon2idESalIndividual() {
        var a = criarConta(emailUnico(), SENHA_FORTE, Papel.CLIENTE, true);
        var b = criarConta(emailUnico(), SENHA_FORTE, Papel.CLIENTE, true);
        assertThat(a.getHashSenha()).startsWith("$argon2id$").doesNotContain(SENHA_FORTE);
        assertThat(a.getHashSenha()).as("mesma senha, sal diferente").isNotEqualTo(b.getHashSenha());
        assertThat(a.getHashSenha()).contains("m=19456,t=2,p=1");
    }

    // ---- recuperação ----

    @Test
    void recuperacaoTemRespostaIdenticaParaContaExistenteEInexistente() throws Exception {
        String existente = emailUnico();
        criarConta(existente, SENHA_FORTE, Papel.CLIENTE, true);
        MvcResult a = executar(postJson("/v1/senha/recuperacao", Map.of("email", existente)));
        MvcResult b = executar(postJson("/v1/senha/recuperacao", Map.of("email", emailUnico())));
        MvcResult c = executar(postJson("/v1/senha/recuperacao", Map.of("email", "lixo")));
        assertThat(a.getResponse().getStatus()).isEqualTo(202).isEqualTo(b.getResponse().getStatus()).isEqualTo(c.getResponse().getStatus());
        assertThat(a.getResponse().getContentAsString()).isEqualTo(b.getResponse().getContentAsString());
        assertThat(emails.quantos(existente, Tipo.RECUPERACAO_SENHA)).isEqualTo(1);
    }

    @Test
    void redefinirSenhaFuncionaUmaVezRevogaSessoesETrocaACredencial() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String sessaoAntiga = entrar(email, SENHA_FORTE);
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String token = tokenDoEmail(email, Tipo.RECUPERACAO_SENHA);
        String nova = "Outra-senha-longa-99?";

        assertThat(executar(postJson("/v1/senha/redefinir", Map.of("token", token, "novaSenha", nova))).getResponse().getStatus()).isEqualTo(204);
        assertThat(executar(postJson("/v1/senha/redefinir", Map.of("token", token, "novaSenha", nova + "1"))).getResponse().getStatus())
                .as("reutilização do token").isEqualTo(400);
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, nova).getResponse().getStatus()).isEqualTo(200);
        assertThat(executar(obter("/v1/conta", sessaoAntiga)).getResponse().getStatus()).as("sessões antigas revogadas").isEqualTo(401);
    }

    @Test
    void tokenDeRecuperacaoExpiraEmTrintaMinutos() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String token = tokenDoEmail(email, Tipo.RECUPERACAO_SENHA);
        relogio.avancar(Duration.ofMinutes(31));
        assertThat(executar(postJson("/v1/senha/redefinir", Map.of("token", token, "novaSenha", "Nova-senha-comprida-1!"))).getResponse().getStatus()).isEqualTo(400);
        assertThat(login(email, SENHA_FORTE).getResponse().getStatus()).as("a senha antiga continua valendo").isEqualTo(200);
    }

    @Test
    void senhaFracaNaRedefinicaoNaoConsomeOToken() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String token = tokenDoEmail(email, Tipo.RECUPERACAO_SENHA);
        assertThat(executar(postJson("/v1/senha/redefinir", Map.of("token", token, "novaSenha", "curta"))).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(postJson("/v1/senha/redefinir", Map.of("token", token, "novaSenha", "Senha-nova-e-forte-7!"))).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void redefinirComTokenInventadoOuVazioFalha() throws Exception {
        for (String token : new String[] {"", "abc", "A".repeat(43), null}) {
            Map<String, String> corpo = new java.util.HashMap<>();
            corpo.put("token", token);
            corpo.put("novaSenha", "Senha-nova-e-forte-7!");
            assertThat(executar(postJson("/v1/senha/redefinir", corpo)).getResponse().getStatus()).isEqualTo(400);
        }
    }

    @Test
    void redefinirSenhaLiberaContaBloqueada() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        for (int i = 0; i < 5; i++) {
            login(email, "errada-errada-errada-" + i);
        }
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String nova = "Senha-nova-e-forte-7!";
        executar(postJson("/v1/senha/redefinir", Map.of("token", tokenDoEmail(email, Tipo.RECUPERACAO_SENHA), "novaSenha", nova)));
        assertThat(login(email, nova).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- sessões ----

    @Test
    void semTokenOuComTokenInvalidoORespostaEUniforme401() throws Exception {
        assertThat(executar(get("/v1/conta")).getResponse().getStatus()).isEqualTo(401);
        for (String ruim : new String[] {"abc", "A".repeat(43), "x".repeat(500), "' OR '1'='1"}) {
            assertThat(executar(obter("/v1/conta", ruim)).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(corpo(executar(get("/v1/conta"))).get("codigo").asText()).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void logoutInvalidaOTokenImediatamente() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);
        assertThat(executar(apagar("/v1/sessoes/atual", token)).getResponse().getStatus()).isEqualTo(204);
        assertThat(executar(obter("/v1/conta", token)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void sessaoExpiraAposOPrazo() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);
        relogio.avancar(Duration.ofDays(13));
        assertThat(executar(obter("/v1/conta", token)).getResponse().getStatus()).isEqualTo(200);
        relogio.avancar(Duration.ofDays(2));
        assertThat(executar(obter("/v1/conta", token)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void contaDesativadaPerdeAsSessoesAbertas() throws Exception {
        String email = emailUnico();
        var conta = criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);
        conta.desativar(relogio.instant());
        contas.save(conta);
        assertThat(executar(obter("/v1/conta", token)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void usuarioListaERevogaSomenteAsPropriasSessoes() throws Exception {
        String emailA = emailUnico();
        String emailB = emailUnico();
        criarConta(emailA, SENHA_FORTE, Papel.CLIENTE, true);
        criarConta(emailB, SENHA_FORTE, Papel.CLIENTE, true);
        String tokenA1 = entrar(emailA, SENHA_FORTE);
        String tokenA2 = entrar(emailA, SENHA_FORTE);
        String tokenB = entrar(emailB, SENHA_FORTE);

        var sessoesA = corpo(executar(obter("/v1/sessoes", tokenA1)));
        assertThat(sessoesA).hasSize(2);
        var sessoesB = corpo(executar(obter("/v1/sessoes", tokenB)));
        assertThat(sessoesB).hasSize(1);
        String idDeB = sessoesB.get(0).get("id").asText();

        assertThat(executar(apagar("/v1/sessoes/" + idDeB, tokenA1)).getResponse().getStatus()).as("IDOR: sessão de outra conta").isEqualTo(404);
        assertThat(executar(obter("/v1/conta", tokenB)).getResponse().getStatus()).isEqualTo(200);

        String idA2 = null;
        for (var s : sessoesA) {
            if (!s.get("atual").asBoolean()) {
                idA2 = s.get("id").asText();
            }
        }
        assertThat(executar(apagar("/v1/sessoes/" + idA2, tokenA1)).getResponse().getStatus()).isEqualTo(204);
        assertThat(executar(obter("/v1/conta", tokenA2)).getResponse().getStatus()).isEqualTo(401);
        assertThat(executar(obter("/v1/conta", tokenA1)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void identificadorDeSessaoInvalidoNaoQuebraNada() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);
        assertThat(executar(apagar("/v1/sessoes/nao-e-uuid", token)).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(apagar("/v1/sessoes/00000000-0000-0000-0000-000000000000", token)).getResponse().getStatus()).isEqualTo(404);
    }

    // ---- autorização ----

    @Test
    void clienteNaoAcessaEndpointsAdministrativos() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);
        assertThat(executar(obter("/v1/admin/contas", token)).getResponse().getStatus()).isEqualTo(403);
        assertThat(executar(get("/v1/admin/contas")).getResponse().getStatus()).isEqualTo(401);
    }

    // ---- vazamentos ----

    @Test
    void logsENaoExpoemSenhasNemTokens(CapturedOutput saida) throws Exception {
        String email = emailUnico();
        String senha = "Senha-Muito-Secreta-731!";
        cadastrar(email, senha);
        String tokenEmail = tokenDoEmail(email, Tipo.VERIFICACAO_EMAIL);
        executar(postJson("/v1/contas/verificar-email", Map.of("token", tokenEmail)));
        String sessao = entrar(email, senha);
        login(email, "tentativa-errada-vazada-1");
        executar(postJson("/v1/senha/recuperacao", Map.of("email", email)));
        String tokenRec = tokenDoEmail(email, Tipo.RECUPERACAO_SENHA);
        executar(postJson("/v1/senha/redefinir", Map.of("token", tokenRec, "novaSenha", "Outra-Senha-Secreta-852!")));
        assertThat(saida.getAll()).doesNotContain(senha, "tentativa-errada-vazada-1", "Outra-Senha-Secreta-852!", tokenEmail, sessao, tokenRec);
    }

    @Test
    void errosNaoVazamDetalhesInternos() throws Exception {
        for (MvcResult r : new MvcResult[] {executar(postJson("/v1/sessoes", "{ quebrado")), executar(get("/v1/inexistente")),
            executar(postJson("/v1/contas", "[]")), executar(obter("/v1/conta", "x"))}) {
            String corpo = r.getResponse().getContentAsString();
            assertThat(corpo).doesNotContain("Exception", "at br.com", "org.springframework", "stackTrace", "jdbc");
            assertThat(r.getResponse().getStatus()).isBetween(400, 499);
        }
    }

    @Test
    void respostasTemCabecalhosDeSegurancaESemCache() throws Exception {
        MvcResult r = executar(get("/v1/status"));
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(r.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(r.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void corpoGrandeDemaisERecusado() throws Exception {
        String grande = "{\"email\":\"" + "a".repeat(70_000) + "\"}";
        assertThat(executar(postJson("/v1/senha/recuperacao", grande)).getResponse().getStatus()).isEqualTo(413);
    }
}
