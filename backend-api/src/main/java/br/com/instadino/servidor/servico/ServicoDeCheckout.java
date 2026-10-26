package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import br.com.instadino.contratos.pagamento.ItemDoHistorico;
import br.com.instadino.contratos.pagamento.PlanoComercial;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import br.com.instadino.contratos.pagamento.SituacaoDosPagamentos;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.Pedido;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePedidos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo e criação de pedidos. Preço e plano vêm SEMPRE do banco do servidor, nunca do cliente. */
@Service
public class ServicoDeCheckout {

    static final String MENSAGEM_INDISPONIVEL = "A compra do plano Premium ainda não está habilitada. Nenhuma cobrança é feita por este aplicativo.";

    private final ConfiguracaoDePagamentos configuracao;
    private final ProvedorPagamento provedor;
    private final RepositorioDePlanos planos;
    private final RepositorioDePedidos pedidos;
    private final RepositorioDeContas contas;
    private final ServicoDeDireitos direitos;
    private final ServicoDeAuditoria auditoria;
    private final Clock relogio;

    ServicoDeCheckout(ConfiguracaoDePagamentos configuracao, ProvedorPagamento provedor, RepositorioDePlanos planos,
                      RepositorioDePedidos pedidos, RepositorioDeContas contas, ServicoDeDireitos direitos, ServicoDeAuditoria auditoria, Clock relogio) {
        this.configuracao = configuracao;
        this.provedor = provedor;
        this.planos = planos;
        this.pedidos = pedidos;
        this.contas = contas;
        this.direitos = direitos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public RespostaDeCatalogo catalogo() {
        List<PlanoComercial> lista = planos.findByAtivoTrueOrderByPrecoCentavosAsc().stream()
                .map(p -> new PlanoComercial(p.getId(), p.getNome(), p.getDescricao(), p.getPeriodicidade(), p.getPrecoCentavos(), p.getMoeda())).toList();
        SituacaoDosPagamentos situacao = configuracao.situacao();
        boolean habilitado = configuracao.comprasPermitidas();
        String mensagem = switch (situacao) {
            case SANDBOX_ATIVO -> "Modo de teste: nenhuma cobrança real será feita.";
            case PRODUCAO_ATIVA -> null;
            default -> MENSAGEM_INDISPONIVEL;
        };
        return new RespostaDeCatalogo(lista, situacao, habilitado, mensagem);
    }

    @Transactional
    public RespostaDeCheckout criar(UUID contaId, String planoId) {
        if (!configuracao.comprasPermitidas()) {
            auditoria.conta(contaId, "CHECKOUT_RECUSADO", planoId, "compras desabilitadas");
            throw new ErroDeNegocio(HttpStatus.CONFLICT, "COMPRAS_INDISPONIVEIS", MENSAGEM_INDISPONIVEL);
        }
        Plano plano = planoId == null ? null : planos.findById(planoId).filter(Plano::isAtivo).orElse(null);
        if (plano == null) {
            throw new ErroDeNegocio(HttpStatus.NOT_FOUND, "PLANO_INEXISTENTE", "Plano não encontrado.");
        }
        Conta conta = contas.findById(contaId).filter(Conta::isAtiva).orElseThrow(
                () -> new ErroDeNegocio(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Entre na sua conta para continuar."));
        var direito = direitos.direitoDa(contaId);
        if (direito.premium() && ServicoDeDireitos.ORIGEM_ASSINATURA.equals(direito.origem()) && "ATIVA".equals(direito.situacao())) {
            throw new ErroDeNegocio(HttpStatus.CONFLICT, "JA_ASSINANTE", "Sua assinatura já está ativa.");
        }
        Pedido pedido = pedidos.save(new Pedido(contaId, plano, provedor.sandbox(), relogio.instant()));
        var criado = provedor.criarCheckout(new ProvedorPagamento.PedidoParaProvedor(pedido.getId(), plano.getId(), plano.getPrecoCentavos(),
                plano.getMoeda(), conta.getEmail()));
        pedido.definirReferencia(criado.referenciaDoProvedor());
        auditoria.conta(contaId, "PEDIDO_CRIADO", pedido.getId().toString(), "plano=" + plano.getId() + "; sandbox=" + provedor.sandbox());
        return new RespostaDeCheckout(pedido.getId(), criado.urlDoCheckout(), provedor.sandbox());
    }

    @Transactional(readOnly = true)
    public List<ItemDoHistorico> historico(UUID contaId) {
        return pedidos.findByContaIdOrderByCriadoEmDesc(contaId).stream().map(p -> new ItemDoHistorico(p.getId(), p.getPlanoId(),
                p.getValorCentavos(), p.getMoeda(), p.getSituacao().name(), p.getCriadoEm())).toList();
    }
}
