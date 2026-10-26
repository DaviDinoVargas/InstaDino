package br.com.instadino.contratos.pagamento;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstração do provedor de pagamento. O núcleo comercial depende só desta interface. Implementações:
 * um provedor de sandbox (testes, sem cobrança) e, no futuro, o adaptador do provedor real, que só deve ser
 * escrito depois de confirmar a documentação oficial (ver docs/payments/PROVIDER_DECISION.md).
 */
public interface ProvedorPagamento {

    record PedidoParaProvedor(UUID pedidoId, String planoId, long valorEmCentavos, String moeda, String emailDoCliente) {
    }

    record CheckoutCriado(String urlDoCheckout, String referenciaDoProvedor) {
    }

    String nome();

    /** Nome do cabeçalho HTTP que carrega a assinatura do webhook neste provedor. */
    String cabecalhoDeAssinatura();

    /** Verdadeiro para provedores que nunca cobram de verdade. */
    boolean sandbox();

    CheckoutCriado criarCheckout(PedidoParaProvedor pedido);

    /** Autentica e interpreta um evento recebido. Lança {@link AssinaturaInvalidaException} se não for autêntico. */
    EventoDePagamento interpretarEvento(String corpoBruto, String cabecalhoDeAssinatura, Instant agora);
}
