package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.pagamento.VerificadorDeAssinaturaHmac;
import br.com.instadino.servidor.dominio.Pedido;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class PagamentosSandboxTest extends PagamentosBase {

    // ---- catálogo e checkout ----

    @Test
    void catalogoEPublicoEInformaOModoDeTeste() throws Exception {
        garantirPlano();
        var c = corpo(executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/planos")));
        assertThat(c.get("situacao").asText()).isEqualTo("SANDBOX_ATIVO");
        assertThat(c.get("comprasHabilitadas").asBoolean()).isTrue();
        assertThat(c.get("mensagem").asText()).contains("nenhuma cobrança real");
        boolean achou = false;
        for (var p : c.get("planos")) {
            if (PLANO.equals(p.get("id").asText())) {
                assertThat(p.get("precoEmCentavos").asLong()).isEqualTo(PRECO);
                achou = true;
            }
        }
        assertThat(achou).isTrue();
    }

    @Test
    void checkoutExigeLoginEPlanoExistente() throws Exception {
        garantirPlano();
        assertThat(executar(postJson("/v1/checkout", Map.of("planoId", PLANO))).getResponse().getStatus()).isEqualTo(401);
        String token = clienteEToken();
        assertThat(executar(comToken(postJson("/v1/checkout", Map.of("planoId", "nao-existe")), token)).getResponse().getStatus()).isEqualTo(404);
        assertThat(executar(comToken(postJson("/v1/checkout", Map.of()), token)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void oPrecoVemSempreDoServidorMesmoQueOClienteTenteEnviarOutro() throws Exception {
        String token = clienteEToken();
        MvcResult r = executar(comToken(postJson("/v1/checkout", Map.of("planoId", PLANO, "precoEmCentavos", 1, "valorEmCentavos", 1, "desconto", 99)), token));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        UUID id = UUID.fromString(corpo(r).get("pedidoId").asText());
        assertThat(pedidos.findById(id).orElseThrow().getValorCentavos()).isEqualTo(PRECO);
        assertThat(corpo(r).get("sandbox").asBoolean()).isTrue();
        assertThat(corpo(r).get("urlDoCheckout").asText()).startsWith("https://checkout.sandbox.instadino.invalid/");
    }

    // ---- fluxo feliz ----

    @Test
    void pagamentoAprovadoAtivaOPremiumSomenteDepoisDoEventoVerificado() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        assertThat(premium(token)).as("antes do pagamento").isFalse();
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(403);

        assertThat(simular(token, pedido, "PAGAMENTO_APROVADO")).isEqualTo("PROCESSADO");
        var a = assinatura(token);
        assertThat(a.get("premiumVigente").asBoolean()).isTrue();
        assertThat(a.get("situacao").asText()).isEqualTo("ATIVA");
        assertThat(a.get("origem").asText()).isEqualTo("ASSINATURA");
        assertThat(java.time.Instant.parse(a.get("validaAte").asText())).isEqualTo(relogio.instant().plus(Duration.ofDays(30)));
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(200);
        assertThat(pedidos.findById(pedido).orElseThrow().getSituacao()).isEqualTo(Pedido.Situacao.PAGO);
        assertThat(corpo(executar(obter("/v1/pedidos", token)))).hasSize(1);
    }

    @Test
    void jaAssinanteNaoCompraDeNovo() throws Exception {
        String token = clienteEToken();
        simular(token, criarPedido(token), "PAGAMENTO_APROVADO");
        MvcResult r = executar(comToken(postJson("/v1/checkout", Map.of("planoId", PLANO)), token));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(corpo(r).get("codigo").asText()).isEqualTo("JA_ASSINANTE");
    }

    // ---- autenticidade do webhook ----

    @Test
    void webhookAssinadoCorretamenteEProcessadoSemLoginDeUsuario() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        String corpo = corpoDeEvento("evt_" + UUID.randomUUID(), "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "BRL");
        MvcResult r = webhookAssinado(corpo);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado(r)).isEqualTo("PROCESSADO");
        assertThat(premium(token)).isTrue();
    }

    @Test
    void webhookInvalidoNuncaAlteraEstado() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        String corpo = corpoDeEvento("evt_" + UUID.randomUUID(), "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "BRL");
        String valida = VerificadorDeAssinaturaHmac.assinar(SEGREDO, relogio.instant(), corpo);
        String outraChave = VerificadorDeAssinaturaHmac.assinar("outro-segredo-qualquer-0123456789".getBytes(), relogio.instant(), corpo);
        String antiga = VerificadorDeAssinaturaHmac.assinar(SEGREDO, relogio.instant().minus(Duration.ofMinutes(10)), corpo);

        for (MvcResult r : new MvcResult[] {webhook(corpo, null), webhook(corpo, ""), webhook(corpo, "lixo"), webhook(corpo, outraChave), webhook(corpo, antiga),
            webhook(corpo.replace("PAGAMENTO_APROVADO", "PAGAMENTO_APROVADO ") , valida)}) {
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            assertThat(r.getResponse().getContentAsString()).doesNotContain("segredo", "HMAC", "Exception");
        }
        assertThat(premium(token)).isFalse();
        assertThat(pedidos.findById(pedido).orElseThrow().getSituacao()).isEqualTo(Pedido.Situacao.PENDENTE);
    }

    @Test
    void webhookComAssinaturaValidaMasConteudoMalformadoNaoGravaNada() throws Exception {
        long antes = eventos.count();
        for (String corpo : new String[] {"{}", "não é json", "{\"id\":\"x\",\"tipo\":\"INVENTADO\",\"criadoEm\":\"2026-01-01T00:00:00Z\",\"pedidoId\":\"" + UUID.randomUUID() + "\",\"valorEmCentavos\":1,\"moeda\":\"BRL\"}",
            "{\"id\":\"x\",\"tipo\":\"PAGAMENTO_APROVADO\",\"criadoEm\":\"ontem\",\"pedidoId\":\"abc\",\"valorEmCentavos\":1,\"moeda\":\"BRL\"}"}) {
            assertThat(webhookAssinado(corpo).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(eventos.count()).isEqualTo(antes);
    }

    @Test
    void webhookGrandeDemaisERecusado() throws Exception {
        assertThat(webhookAssinado("{\"x\":\"" + "a".repeat(300_000) + "\"}").getResponse().getStatus()).isEqualTo(413);
    }

    @Test
    void clienteNaoTemNenhumCaminhoParaDeclararPagamentoConfirmado() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        String corpo = corpoDeEvento("evt_forjado", "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "BRL");
        assertThat(executar(comToken(postJson("/v1/webhooks/pagamento", corpo), token)).getResponse().getStatus()).isEqualTo(400);
        for (String caminho : new String[] {"/v1/pedidos/" + pedido + "/confirmar", "/v1/pedidos/" + pedido + "/pagar", "/v1/assinatura/ativar", "/v1/premium"}) {
            assertThat(executar(comToken(postJson(caminho, Map.of("pago", true)), token)).getResponse().getStatus()).isIn(404, 405);
        }
        assertThat(premium(token)).isFalse();
    }

    // ---- idempotência, ordem e valores ----

    @Test
    void eventoRepetidoNaoAplicaDuasVezes() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        String corpo = corpoDeEvento("evt_repetido_" + UUID.randomUUID(), "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "BRL");
        assertThat(resultado(webhookAssinado(corpo))).isEqualTo("PROCESSADO");
        var fim = assinatura(token).get("validaAte").asText();
        assertThat(resultado(webhookAssinado(corpo))).isEqualTo("DUPLICADO");
        assertThat(resultado(webhookAssinado(corpo))).isEqualTo("DUPLICADO");
        assertThat(assinatura(token).get("validaAte").asText()).as("o período não foi estendido de novo").isEqualTo(fim);
    }

    @Test
    void entregaConcorrenteDoMesmoEventoProcessaExatamenteUmaVez() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        String corpo = corpoDeEvento("evt_corrida_" + UUID.randomUUID(), "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "BRL");
        String cabecalho = VerificadorDeAssinaturaHmac.assinar(SEGREDO, relogio.instant(), corpo);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futuros = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<String> tarefa = () -> resultado(webhook(corpo, cabecalho));
            futuros.add(pool.submit(tarefa));
        }
        int processados = 0;
        int duplicados = 0;
        for (Future<String> f : futuros) {
            String r = f.get();
            if (r.equals("PROCESSADO")) {
                processados++;
            } else if (r.equals("DUPLICADO")) {
                duplicados++;
            }
        }
        pool.shutdown();
        assertThat(processados).isEqualTo(1);
        assertThat(duplicados).isEqualTo(7);
        assertThat(java.time.Instant.parse(assinatura(token).get("validaAte").asText())).isEqualTo(relogio.instant().plus(Duration.ofDays(30)));
    }

    /**
     * Prova determinística da idempotência no banco: gravar duas vezes o mesmo id de evento tem de falhar por violação
     * de chave. Com {@code merge} (comportamento padrão para id atribuído) a segunda gravação seria um UPDATE silencioso
     * e a corrida entre entregas simultâneas processaria o mesmo evento duas vezes.
     */
    @Test
    void gravarOMesmoIdDeEventoDuasVezesViolaAChavePrimaria() {
        String id = "evt_pk_" + UUID.randomUUID();
        var agora = relogio.instant();
        eventos.saveAndFlush(new br.com.instadino.servidor.dominio.EventoPagamento(id, "PAGAMENTO_APROVADO", agora, agora, null, "PROCESSADO"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> eventos.saveAndFlush(
                new br.com.instadino.servidor.dominio.EventoPagamento(id, "PAGAMENTO_APROVADO", agora, agora, null, "PROCESSADO")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void eventoAtrasadoForaDeOrdemNaoConcedePremium() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        var t1 = relogio.instant().minus(Duration.ofMinutes(2));
        var t2 = relogio.instant().minus(Duration.ofMinutes(1));
        // o reembolso (t2) chega ANTES da aprovação (t1 < t2)
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_b", "PAGAMENTO_REEMBOLSADO", t2, pedido, PRECO, "BRL")))).isEqualTo("PROCESSADO");
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_a", "PAGAMENTO_APROVADO", t1, pedido, PRECO, "BRL")))).isEqualTo("IGNORADO_FORA_DE_ORDEM");
        assertThat(premium(token)).isFalse();
    }

    @Test
    void cancelamentoAtrasadoNaoDesfazRenovacaoMaisNova() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        var t0 = relogio.instant().minus(Duration.ofMinutes(10));
        var t1 = relogio.instant().minus(Duration.ofMinutes(5));
        var t2 = relogio.instant().minus(Duration.ofMinutes(1));
        webhookAssinado(corpoDeEvento("evt_1", "PAGAMENTO_APROVADO", t0, pedido, PRECO, "BRL"));
        webhookAssinado(corpoDeEvento("evt_3", "RENOVACAO_APROVADA", t2, pedido, PRECO, "BRL"));
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_2", "ASSINATURA_CANCELADA", t1, pedido, PRECO, "BRL")))).isEqualTo("IGNORADO_FORA_DE_ORDEM");
        assertThat(assinatura(token).get("situacao").asText()).isEqualTo("ATIVA");
    }

    @Test
    void valorOuMoedaDiferentesDoPedidoSaoIgnorados() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_v1", "PAGAMENTO_APROVADO", relogio.instant(), pedido, 1, "BRL")))).isEqualTo("IGNORADO_VALOR_DIVERGENTE");
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_v2", "PAGAMENTO_APROVADO", relogio.instant(), pedido, PRECO, "USD")))).isEqualTo("IGNORADO_VALOR_DIVERGENTE");
        assertThat(premium(token)).isFalse();
    }

    @Test
    void eventoParaPedidoDesconhecidoEIgnoradoMasRegistrado() throws Exception {
        assertThat(resultado(webhookAssinado(corpoDeEvento("evt_desc_" + UUID.randomUUID(), "PAGAMENTO_APROVADO", relogio.instant(), UUID.randomUUID(), PRECO, "BRL"))))
                .isEqualTo("IGNORADO_PEDIDO_DESCONHECIDO");
    }

    // ---- ciclo de vida ----

    @Test
    void pagamentoRecusadoNaoLibera() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        assertThat(simular(token, pedido, "PAGAMENTO_RECUSADO")).isEqualTo("PROCESSADO");
        assertThat(premium(token)).isFalse();
        assertThat(pedidos.findById(pedido).orElseThrow().getSituacao()).isEqualTo(Pedido.Situacao.RECUSADO);
    }

    @Test
    void renovacaoEstendeAPartirDoFimDoPeriodoAtual() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        simular(token, pedido, "PAGAMENTO_APROVADO");
        relogio.avancar(Duration.ofDays(29));
        token = entrarDeNovo();
        assertThat(simular(token, pedido, "RENOVACAO_APROVADA")).isEqualTo("PROCESSADO");
        assertThat(java.time.Instant.parse(assinatura(token).get("validaAte").asText())).isEqualTo(relogio.instant().minus(Duration.ofDays(29)).plus(Duration.ofDays(60)));
    }

    @Test
    void renovacaoSemAssinaturaPreviaNaoConcedeNada() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        assertThat(simular(token, pedido, "RENOVACAO_APROVADA")).isEqualTo("IGNORADO_TRANSICAO_INVALIDA");
        assertThat(premium(token)).isFalse();
    }

    @Test
    void cancelamentoMantemOAcessoAteOFimDoPeriodoPago() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        simular(token, pedido, "PAGAMENTO_APROVADO");
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(simular(token, pedido, "ASSINATURA_CANCELADA")).isEqualTo("PROCESSADO");
        assertThat(assinatura(token).get("situacao").asText()).isEqualTo("CANCELADA");
        assertThat(premium(token)).as("ainda dentro do período pago").isTrue();
        relogio.avancar(Duration.ofDays(31));
        token = entrarDeNovo();
        assertThat(premium(token)).isFalse();
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void reembolsoRevogaOPremiumImediatamenteEAsLicencas() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        simular(token, pedido, "PAGAMENTO_APROVADO");
        String disp = dispositivo();
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", disp, "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(200);
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(simular(token, pedido, "PAGAMENTO_REEMBOLSADO")).isEqualTo("PROCESSADO");
        assertThat(premium(token)).isFalse();
        assertThat(assinatura(token).get("situacao").asText()).isEqualTo("REVOGADA");
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", disp, "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(403);
        assertThat(pedidos.findById(pedido).orElseThrow().getSituacao()).isEqualTo(Pedido.Situacao.REEMBOLSADO);
        UUID conta = pedidos.findById(pedido).orElseThrow().getContaId();
        assertThat(licencas.findByContaIdAndRevogadaEmIsNull(conta)).as("licenças emitidas ficam marcadas como revogadas").isEmpty();
    }

    @Test
    void aprovacaoPosteriorDoMesmoPedidoReembolsadoNaoReativa() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        simular(token, pedido, "PAGAMENTO_APROVADO");
        relogio.avancar(Duration.ofSeconds(1));
        simular(token, pedido, "PAGAMENTO_REEMBOLSADO");
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(simular(token, pedido, "PAGAMENTO_APROVADO")).isEqualTo("IGNORADO_TRANSICAO_INVALIDA");
        assertThat(premium(token)).isFalse();
    }

    @Test
    void novaCompraDepoisDeReembolsoPodeReativar() throws Exception {
        String token = clienteEToken();
        UUID primeiro = criarPedido(token);
        simular(token, primeiro, "PAGAMENTO_APROVADO");
        relogio.avancar(Duration.ofSeconds(1));
        simular(token, primeiro, "PAGAMENTO_REEMBOLSADO");
        relogio.avancar(Duration.ofSeconds(1));
        UUID segundo = criarPedido(token);
        assertThat(simular(token, segundo, "PAGAMENTO_APROVADO")).isEqualTo("PROCESSADO");
        assertThat(premium(token)).isTrue();
    }

    @Test
    void chargebackSuspendeOAcesso() throws Exception {
        String token = clienteEToken();
        UUID pedido = criarPedido(token);
        simular(token, pedido, "PAGAMENTO_APROVADO");
        assertThat(executar(comToken(postJson("/v1/licencas", Map.of("idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC")), token)).getResponse().getStatus()).isEqualTo(200);
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(simular(token, pedido, "DISPUTA_ABERTA")).isEqualTo("PROCESSADO");
        assertThat(premium(token)).isFalse();
        assertThat(assinatura(token).get("situacao").asText()).isEqualTo("SUSPENSA");
        assertThat(pedidos.findById(pedido).orElseThrow().getSituacao()).isEqualTo(Pedido.Situacao.EM_DISPUTA);
        assertThat(licencas.findByContaIdAndRevogadaEmIsNull(pedidos.findById(pedido).orElseThrow().getContaId())).isEmpty();
    }

    // ---- isolamento ----

    @Test
    void usuarioNaoSimulaNemVePedidosDeOutro() throws Exception {
        String dono = clienteEToken();
        String intruso = clienteEToken();
        UUID pedido = criarPedido(dono);
        assertThat(executar(comToken(postJson("/v1/sandbox/pedidos/" + pedido + "/eventos/PAGAMENTO_APROVADO", "{}"), intruso)).getResponse().getStatus()).isEqualTo(404);
        assertThat(corpo(executar(obter("/v1/pedidos", intruso)))).isEmpty();
        assertThat(premium(dono)).isFalse();
        assertThat(executar(comToken(postJson("/v1/sandbox/pedidos/" + pedido + "/eventos/INVENTADO", "{}"), dono)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void oPagamentoDeUmaContaNaoLiberaOutra() throws Exception {
        String a = clienteEToken();
        String b = clienteEToken();
        simular(a, criarPedido(a), "PAGAMENTO_APROVADO");
        assertThat(premium(a)).isTrue();
        assertThat(premium(b)).isFalse();
    }
}
