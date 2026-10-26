package br.com.instadino.servidor.web;

import br.com.instadino.contratos.conta.ErroApi;
import br.com.instadino.contratos.pagamento.AssinaturaInvalidaException;
import br.com.instadino.contratos.pagamento.ItemDoHistorico;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.contratos.pagamento.RequisicaoDeCheckout;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import br.com.instadino.contratos.pagamento.TipoDeEventoDePagamento;
import br.com.instadino.servidor.seguranca.PrincipalAutenticado;
import br.com.instadino.servidor.servico.ErroDeNegocio;
import br.com.instadino.servidor.servico.ProcessadorDeEventosDePagamento;
import br.com.instadino.servidor.servico.ServicoDeCheckout;
import br.com.instadino.servidor.servico.ServicoDeSandbox;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo, checkout, histórico, webhook do provedor e simulação de eventos (somente sandbox). */
@RestController
class ControladorDePagamentos {

    private static final int LIMITE_DO_WEBHOOK = 256 * 1024;

    private final ServicoDeCheckout checkout;
    private final ProcessadorDeEventosDePagamento processador;
    private final ServicoDeSandbox sandbox;
    private final ProvedorPagamento provedor;

    ControladorDePagamentos(ServicoDeCheckout checkout, ProcessadorDeEventosDePagamento processador, ServicoDeSandbox sandbox, ProvedorPagamento provedor) {
        this.checkout = checkout;
        this.processador = processador;
        this.sandbox = sandbox;
        this.provedor = provedor;
    }

    @GetMapping("/v1/planos")
    RespostaDeCatalogo planos() {
        return checkout.catalogo();
    }

    @PostMapping("/v1/checkout")
    RespostaDeCheckout criarCheckout(@AuthenticationPrincipal PrincipalAutenticado principal, @RequestBody RequisicaoDeCheckout requisicao) {
        return checkout.criar(principal.contaId(), requisicao.planoId());
    }

    @GetMapping("/v1/pedidos")
    List<ItemDoHistorico> pedidos(@AuthenticationPrincipal PrincipalAutenticado principal) {
        return checkout.historico(principal.contaId());
    }

    /**
     * Webhook do provedor. O corpo é lido BRUTO (a assinatura cobre os bytes exatos). Resposta 200 para qualquer evento
     * autêntico, inclusive os ignorados, para que o provedor não repita indefinidamente; 400 genérico se não for autêntico.
     */
    @PostMapping("/v1/webhooks/pagamento")
    ResponseEntity<?> webhook(HttpServletRequest requisicao, @RequestBody String corpo) {
        if (corpo.getBytes(StandardCharsets.UTF_8).length > LIMITE_DO_WEBHOOK) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErroApi("CORPO_GRANDE_DEMAIS", "Requisição grande demais."));
        }
        try {
            var resultado = processador.processar(corpo, requisicao.getHeader(provedor.cabecalhoDeAssinatura()));
            return ResponseEntity.ok(Map.of("resultado", resultado.name()));
        } catch (AssinaturaInvalidaException e) {
            return ResponseEntity.badRequest().body(new ErroApi("REQUISICAO_INVALIDA", "Requisição inválida."));
        }
    }

    @PostMapping("/v1/sandbox/pedidos/{pedidoId}/eventos/{tipo}")
    Map<String, String> simular(@AuthenticationPrincipal PrincipalAutenticado principal, @PathVariable UUID pedidoId, @PathVariable String tipo) {
        TipoDeEventoDePagamento t;
        try {
            t = TipoDeEventoDePagamento.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "EVENTO_INVALIDO", "Tipo de evento inválido.");
        }
        return Map.of("resultado", sandbox.simular(principal.contaId(), pedidoId, t).name());
    }
}
