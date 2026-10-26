package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.servico.AssinadorDeLicencas;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class LicenciamentoServidorTest extends TesteBase {

    @Autowired
    AssinadorDeLicencas assinador;

    private String clienteEToken() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        return entrar(email, SENHA_FORTE);
    }

    private String masterEToken() throws Exception {
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.MASTER, true);
        return entrar(email, SENHA_FORTE);
    }

    private MvcResult pedirLicenca(String token, String dispositivo) throws Exception {
        return executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", dispositivo, "nomeDoDispositivo", "Meu PC")), token));
    }

    private JsonNode payloadVerificado(JsonNode envelope) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(envelope.get("payload").asText());
        byte[] assinatura = Base64.getUrlDecoder().decode(envelope.get("assinatura").asText());
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(assinador.chavePublica());
        s.update(payload);
        assertThat(s.verify(assinatura)).as("assinatura Ed25519 válida").isTrue();
        return json.readTree(new String(payload, StandardCharsets.UTF_8));
    }

    private UUID idDaConta(String token) throws Exception {
        return UUID.fromString(corpo(executar(obter("/v1/conta", token))).get("id").asText());
    }

    @Test
    void clienteSemAssinaturaNaoRecebeLicenca() throws Exception {
        String token = clienteEToken();
        MvcResult r = pedirLicenca(token, dispositivo());
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(corpo(r).get("codigo").asText()).isEqualTo("LICENCA_INDISPONIVEL");
    }

    @Test
    void masterRecebeLicencaAssinadaComOsCamposEsperados() throws Exception {
        String token = masterEToken();
        String disp = dispositivo();
        MvcResult r = pedirLicenca(token, disp);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode env = corpo(r);
        assertThat(env.get("algoritmo").asText()).isEqualTo("Ed25519");
        assertThat(env.get("kid").asText()).isEqualTo("k1");
        JsonNode p = payloadVerificado(env);
        assertThat(p.get("produto").asText()).isEqualTo("instadino-desktop");
        assertThat(p.get("plano").asText()).isEqualTo("PREMIUM");
        assertThat(p.get("titularId").asText()).isEqualTo(idDaConta(token).toString());
        assertThat(p.get("dispositivoId").asText()).isEqualTo(disp);
        assertThat(p.get("origem").asText()).isEqualTo("PAPEL_MASTER");
        Instant emitida = Instant.parse(p.get("emitidaEm").asText());
        assertThat(emitida).isEqualTo(relogio.instant());
        assertThat(Instant.parse(p.get("validaAte").asText())).isEqualTo(emitida.plus(Duration.ofDays(7)));
        assertThat(Instant.parse(p.get("toleranciaOfflineAte").asText())).isEqualTo(emitida.plus(Duration.ofDays(14)));
    }

    @Test
    void concessaoAdministrativaLiberaELimitaOPrazoDaLicenca() throws Exception {
        String master = masterEToken();
        String cliente = clienteEToken();
        UUID contaCliente = idDaConta(cliente);
        MvcResult c = executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", contaCliente.toString(), "dias", 3, "motivo", "Cortesia de teste")), master));
        assertThat(c.getResponse().getStatus()).isEqualTo(200);

        JsonNode p = payloadVerificado(corpo(pedirLicenca(cliente, dispositivo())));
        assertThat(p.get("origem").asText()).isEqualTo("CONCESSAO_ADMINISTRATIVA");
        assertThat(Instant.parse(p.get("validaAte").asText())).as("limitada ao fim da concessão").isEqualTo(relogio.instant().plus(Duration.ofDays(3)));

        relogio.avancar(Duration.ofDays(4));
        assertThat(pedirLicenca(cliente, dispositivo()).getResponse().getStatus()).as("concessão vencida").isEqualTo(403);
    }

    @Test
    void revogarConcessaoNegaARenovacao() throws Exception {
        String master = masterEToken();
        String cliente = clienteEToken();
        MvcResult c = executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", idDaConta(cliente).toString(), "motivo", "Teste")), master));
        String concessaoId = corpo(c).get("id").asText();
        String disp = dispositivo();
        assertThat(pedirLicenca(cliente, disp).getResponse().getStatus()).isEqualTo(200);
        assertThat(executar(apagar("/v1/admin/concessoes/" + concessaoId, master)).getResponse().getStatus()).isEqualTo(204);
        assertThat(pedirLicenca(cliente, disp).getResponse().getStatus()).isEqualTo(403);
        assertThat(corpo(executar(obter("/v1/assinatura", cliente))).get("premiumVigente").asBoolean()).isFalse();
    }

    @Test
    void limiteDeDispositivosEImpostoERemoverLibera() throws Exception {
        String token = masterEToken();
        String d1 = dispositivo();
        String d2 = dispositivo();
        String d3 = dispositivo();
        assertThat(pedirLicenca(token, d1).getResponse().getStatus()).isEqualTo(200);
        assertThat(pedirLicenca(token, d2).getResponse().getStatus()).isEqualTo(200);
        assertThat(pedirLicenca(token, d3).getResponse().getStatus()).isEqualTo(200);
        MvcResult quarto = pedirLicenca(token, dispositivo());
        assertThat(quarto.getResponse().getStatus()).isEqualTo(409);
        assertThat(corpo(quarto).get("codigo").asText()).isEqualTo("LIMITE_DE_DISPOSITIVOS");
        assertThat(pedirLicenca(token, d1).getResponse().getStatus()).as("renovar o mesmo dispositivo não conta como novo").isEqualTo(200);

        JsonNode lista = corpo(executar(obter("/v1/dispositivos", token)));
        assertThat(lista).hasSize(3);
        String idParaRemover = lista.get(0).get("id").asText();
        assertThat(executar(apagar("/v1/dispositivos/" + idParaRemover, token)).getResponse().getStatus()).isEqualTo(204);
        assertThat(pedirLicenca(token, dispositivo()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void naoEPossivelRemoverDispositivoDeOutraConta() throws Exception {
        String dono = masterEToken();
        String intruso = masterEToken();
        pedirLicenca(dono, dispositivo());
        String idDoDono = corpo(executar(obter("/v1/dispositivos", dono))).get(0).get("id").asText();
        assertThat(executar(apagar("/v1/dispositivos/" + idDoDono, intruso)).getResponse().getStatus()).isEqualTo(404);
        assertThat(corpo(executar(obter("/v1/dispositivos", dono)))).hasSize(1);
        assertThat(corpo(executar(obter("/v1/dispositivos", intruso)))).isEmpty();
    }

    @Test
    void identificadorDeDispositivoInvalidoERejeitado() throws Exception {
        String token = masterEToken();
        for (String ruim : new String[] {"", "curto", "tem espaço no meio do id", "a".repeat(65), "../../etc/passwd"}) {
            assertThat(pedirLicenca(token, ruim).getResponse().getStatus()).as(ruim).isEqualTo(400);
        }
    }

    @Test
    void licencaPorDispositivoTemIdentificadoresDiferentes() throws Exception {
        String token = masterEToken();
        JsonNode a = payloadVerificado(corpo(pedirLicenca(token, dispositivo())));
        JsonNode b = payloadVerificado(corpo(pedirLicenca(token, dispositivo())));
        assertThat(a.get("licencaId").asText()).isNotEqualTo(b.get("licencaId").asText());
        assertThat(a.get("dispositivoId").asText()).isNotEqualTo(b.get("dispositivoId").asText());
    }

    @Test
    void contaDesativadaNaoRenovaELicencasSaoRevogadas() throws Exception {
        String master = masterEToken();
        String cliente = clienteEToken();
        executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", idDaConta(cliente).toString(), "motivo", "Teste")), master));
        assertThat(pedirLicenca(cliente, dispositivo()).getResponse().getStatus()).isEqualTo(200);
        assertThat(executar(comToken(post("/v1/admin/contas/" + idDaConta(cliente) + "/desativar"), master)).getResponse().getStatus()).isEqualTo(204);
        assertThat(pedirLicenca(cliente, dispositivo()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void clienteNaoPodeSeAutoconcederPremium() throws Exception {
        String cliente = clienteEToken();
        MvcResult r = executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", idDaConta(cliente).toString(), "motivo", "Eu mesmo")), cliente));
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(pedirLicenca(cliente, dispositivo()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void clienteNaoConsegueSeTornarMasterPorNenhumCaminho() throws Exception {
        String cliente = clienteEToken();
        for (String caminho : new String[] {"/v1/admin/contas", "/v1/admin/auditoria"}) {
            assertThat(executar(obter(caminho, cliente)).getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(executar(comToken(postJson("/v1/admin/planos", Map.of("id", "x-plano", "nome", "n", "descricao", "d", "periodicidade", "MENSAL", "precoEmCentavos", 1)), cliente))
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(corpo(executar(obter("/v1/conta", cliente))).get("papel").asText()).isEqualTo("CLIENTE");
    }

    @Test
    void acoesAdministrativasFicamNaAuditoriaComOAtor() throws Exception {
        String masterEmail = emailUnico();
        var master = criarConta(masterEmail, SENHA_FORTE, Papel.MASTER, true);
        String token = entrar(masterEmail, SENHA_FORTE);
        String cliente = clienteEToken();
        executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", idDaConta(cliente).toString(), "dias", 5, "motivo", "Motivo auditável 123")), token));
        JsonNode auditoria = corpo(executar(obter("/v1/admin/auditoria", token)));
        boolean achou = false;
        for (JsonNode e : auditoria) {
            if ("CONCESSAO_CRIADA".equals(e.get("acao").asText()) && e.get("detalhe").asText().contains("Motivo auditável 123")) {
                assertThat(e.get("atorId").asText()).isEqualTo(master.getId().toString());
                assertThat(e.get("atorTipo").asText()).isEqualTo("MASTER");
                achou = true;
            }
        }
        assertThat(achou).isTrue();
        assertThat(auditoria.toString()).doesNotContain(SENHA_FORTE);
    }

    @Test
    void concessaoExigeMotivoEPrazoValido() throws Exception {
        String master = masterEToken();
        String cliente = clienteEToken();
        String id = idDaConta(cliente).toString();
        assertThat(executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", id, "motivo", " ")), master)).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", id, "dias", 0, "motivo", "x")), master)).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", id, "dias", 99999, "motivo", "x")), master)).getResponse().getStatus()).isEqualTo(400);
        assertThat(executar(comToken(postJson("/v1/admin/concessoes", Map.of("contaId", UUID.randomUUID().toString(), "motivo", "x")), master)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void masterNaoPodeDesativarASiMesmo() throws Exception {
        String token = masterEToken();
        assertThat(executar(comToken(post("/v1/admin/contas/" + idDaConta(token) + "/desativar"), token)).getResponse().getStatus()).isEqualTo(409);
    }
}
