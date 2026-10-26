package br.com.instadino.servidor.pagamento;

import br.com.instadino.contratos.pagamento.AssinaturaInvalidaException;
import br.com.instadino.contratos.pagamento.EventoDePagamento;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.servidor.servico.ErroDeNegocio;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Marcador do provedor de PRODUÇÃO: ainda não existe adaptador, então qualquer tentativa de cobrar falha fechada.
 * Será substituído somente depois de validar a documentação oficial do provedor (docs/payments/PROVIDER_DECISION.md).
 */
public final class ProvedorNaoImplementado implements ProvedorPagamento {

    @Override
    public String nome() {
        return "nenhum";
    }

    @Override
    public String cabecalhoDeAssinatura() {
        return "X-Signature";
    }

    @Override
    public boolean sandbox() {
        return false;
    }

    @Override
    public CheckoutCriado criarCheckout(PedidoParaProvedor pedido) {
        throw new ErroDeNegocio(HttpStatus.SERVICE_UNAVAILABLE, "COMPRAS_INDISPONIVEIS", "A compra ainda não está habilitada.");
    }

    @Override
    public EventoDePagamento interpretarEvento(String corpoBruto, String cabecalhoDeAssinatura, Instant agora) {
        throw new AssinaturaInvalidaException();
    }
}
