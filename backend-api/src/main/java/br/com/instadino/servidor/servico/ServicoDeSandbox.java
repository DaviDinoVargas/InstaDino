package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import br.com.instadino.contratos.pagamento.EventoDePagamento;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.contratos.pagamento.SituacaoDosPagamentos;
import br.com.instadino.contratos.pagamento.TipoDeEventoDePagamento;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePedidos;
import br.com.instadino.servidor.pagamento.ProvedorSandbox;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Simula, SOMENTE em sandbox, o aviso que o provedor enviaria depois de um pagamento de teste. O evento é assinado e
 * entregue ao MESMO processador do webhook real: não há atalho que conceda Premium sem passar pela verificação.
 */
@Service
public class ServicoDeSandbox {

    private final ConfiguracaoDePagamentos configuracao;
    private final ProvedorPagamento provedor;
    private final RepositorioDePedidos pedidos;
    private final ProcessadorDeEventosDePagamento processador;
    private final Clock relogio;

    ServicoDeSandbox(ConfiguracaoDePagamentos configuracao, ProvedorPagamento provedor, RepositorioDePedidos pedidos,
                     ProcessadorDeEventosDePagamento processador, Clock relogio) {
        this.configuracao = configuracao;
        this.provedor = provedor;
        this.pedidos = pedidos;
        this.processador = processador;
        this.relogio = relogio;
    }

    public ProcessadorDeEventosDePagamento.Resultado simular(UUID contaId, UUID pedidoId, TipoDeEventoDePagamento tipo) {
        if (configuracao.situacao() != SituacaoDosPagamentos.SANDBOX_ATIVO || !(provedor instanceof ProvedorSandbox sandbox)) {
            throw new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Recurso não encontrado.");
        }
        var pedido = pedidos.findByIdAndContaId(pedidoId, contaId)
                .orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Pedido não encontrado."));
        var evento = new EventoDePagamento("evt_sbx_" + UUID.randomUUID(), tipo, relogio.instant(), pedido.getId(), pedido.getValorCentavos(),
                pedido.getMoeda(), pedido.getReferenciaProvedor());
        var assinado = sandbox.assinar(evento);
        return processador.processar(assinado.corpo(), assinado.cabecalho());
    }
}
