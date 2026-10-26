package br.com.instadino.servidor.pagamento;

import br.com.instadino.contratos.pagamento.AssinaturaInvalidaException;
import br.com.instadino.contratos.pagamento.EventoDePagamento;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.contratos.pagamento.TipoDeEventoDePagamento;
import br.com.instadino.contratos.pagamento.VerificadorDeAssinaturaHmac;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Provedor SIMULADO para testes. Nunca cobra: o "checkout" aponta para um domínio reservado (.invalid) e os eventos são
 * gerados pelo próprio servidor. Usa o mesmo esquema de assinatura HMAC de um webhook real, de modo que o caminho de
 * verificação, idempotência e ordenação é exercitado de verdade.
 */
public final class ProvedorSandbox implements ProvedorPagamento {

    public static final String CABECALHO = "X-Sandbox-Signature";

    /** Corpo e cabeçalho de um evento assinado. */
    public record EventoAssinado(String corpo, String cabecalho) {
    }

    private final byte[] segredo;
    private final Clock relogio;
    private final ObjectMapper json;
    private final VerificadorDeAssinaturaHmac verificador;

    public ProvedorSandbox(byte[] segredo, Clock relogio, ObjectMapper json) {
        this.segredo = segredo.clone();
        this.relogio = relogio;
        this.json = json;
        this.verificador = new VerificadorDeAssinaturaHmac(segredo, Duration.ofMinutes(5));
    }

    @Override
    public String nome() {
        return "sandbox";
    }

    @Override
    public String cabecalhoDeAssinatura() {
        return CABECALHO;
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public CheckoutCriado criarCheckout(PedidoParaProvedor pedido) {
        return new CheckoutCriado("https://checkout.sandbox.instadino.invalid/pedido/" + pedido.pedidoId(), "sbx_" + UUID.randomUUID());
    }

    @Override
    public EventoDePagamento interpretarEvento(String corpoBruto, String cabecalhoDeAssinatura, Instant agora) {
        verificador.verificar(cabecalhoDeAssinatura, corpoBruto, agora);
        try {
            JsonNode n = json.readTree(corpoBruto);
            String id = texto(n, "id");
            TipoDeEventoDePagamento tipo = TipoDeEventoDePagamento.valueOf(texto(n, "tipo"));
            Instant criadoEm = Instant.parse(texto(n, "criadoEm"));
            UUID pedidoId = UUID.fromString(texto(n, "pedidoId"));
            long valor = n.path("valorEmCentavos").asLong(-1);
            String moeda = texto(n, "moeda");
            if (id.length() > 100 || valor < 0) {
                throw new IllegalArgumentException("evento inválido");
            }
            return new EventoDePagamento(id, tipo, criadoEm, pedidoId, valor, moeda, n.path("referencia").asText(null));
        } catch (IOException | RuntimeException e) {
            // a assinatura era válida, mas o conteúdo não pode ser interpretado
            throw new IllegalArgumentException("Evento de pagamento malformado.");
        }
    }

    /** Gera um evento assinado, como o provedor faria. */
    public EventoAssinado assinar(EventoDePagamento evento) {
        try {
            var no = json.createObjectNode();
            no.put("id", evento.id());
            no.put("tipo", evento.tipo().name());
            no.put("criadoEm", evento.criadoEm().toString());
            no.put("pedidoId", evento.pedidoId().toString());
            no.put("valorEmCentavos", evento.valorEmCentavos());
            no.put("moeda", evento.moeda());
            if (evento.referenciaDoProvedor() != null) {
                no.put("referencia", evento.referenciaDoProvedor());
            }
            String corpo = json.writeValueAsString(no);
            return new EventoAssinado(corpo, VerificadorDeAssinaturaHmac.assinar(segredo, relogio.instant(), corpo));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao assinar o evento de teste.");
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException("campo ausente: " + campo);
        }
        return v.asText();
    }
}
