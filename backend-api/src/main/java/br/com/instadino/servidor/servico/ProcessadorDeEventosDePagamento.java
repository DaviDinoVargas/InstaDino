package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.pagamento.EventoDePagamento;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.servidor.dominio.Assinatura;
import br.com.instadino.servidor.dominio.EventoPagamento;
import br.com.instadino.servidor.dominio.Pedido;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeAssinaturas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeEventosDePagamento;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeLicencas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePedidos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Único caminho pelo qual um pagamento altera direitos. Garantias:
 * <ul>
 *   <li>autenticidade: o provedor verifica a assinatura antes de qualquer leitura do conteúdo;</li>
 *   <li>idempotência: o id do evento é chave primária; repetição não altera nada;</li>
 *   <li>ordem: evento mais antigo que o último já aplicado à assinatura é ignorado;</li>
 *   <li>valor: o valor do evento deve ser igual ao do pedido, que veio do catálogo do servidor;</li>
 *   <li>o cliente desktop nunca participa: não existe endpoint que aceite "pagamento confirmado" do cliente.</li>
 * </ul>
 */
@Service
public class ProcessadorDeEventosDePagamento {

    public enum Resultado {
        PROCESSADO, DUPLICADO, IGNORADO_FORA_DE_ORDEM, IGNORADO_PEDIDO_DESCONHECIDO, IGNORADO_VALOR_DIVERGENTE, IGNORADO_TRANSICAO_INVALIDA
    }

    private final ProvedorPagamento provedor;
    private final RepositorioDeEventosDePagamento eventos;
    private final RepositorioDePedidos pedidos;
    private final RepositorioDeAssinaturas assinaturas;
    private final RepositorioDePlanos planos;
    private final RepositorioDeLicencas licencas;
    private final ServicoDeAuditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    ProcessadorDeEventosDePagamento(ProvedorPagamento provedor, RepositorioDeEventosDePagamento eventos, RepositorioDePedidos pedidos,
                                    RepositorioDeAssinaturas assinaturas, RepositorioDePlanos planos, RepositorioDeLicencas licencas,
                                    ServicoDeAuditoria auditoria, TransactionTemplate transacao, Clock relogio) {
        this.provedor = provedor;
        this.eventos = eventos;
        this.pedidos = pedidos;
        this.assinaturas = assinaturas;
        this.planos = planos;
        this.licencas = licencas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    /** @throws br.com.instadino.contratos.pagamento.AssinaturaInvalidaException se o evento não for autêntico */
    public Resultado processar(String corpoBruto, String cabecalhoDeAssinatura) {
        EventoDePagamento evento = provedor.interpretarEvento(corpoBruto, cabecalhoDeAssinatura, relogio.instant());
        try {
            return transacao.execute(status -> aplicar(evento));
        } catch (DataIntegrityViolationException concorrente) {
            // outro processamento do mesmo evento venceu a corrida: o resultado é o mesmo de uma repetição
            return Resultado.DUPLICADO;
        }
    }

    private Resultado aplicar(EventoDePagamento ev) {
        if (eventos.existsById(ev.id())) {
            return Resultado.DUPLICADO;
        }
        Instant agora = relogio.instant();
        EventoPagamento registro = eventos.saveAndFlush(new EventoPagamento(ev.id(), ev.tipo().name(), agora, ev.criadoEm(), ev.pedidoId(), "PROCESSANDO"));
        Resultado resultado = decidir(ev, agora);
        registro.definirResultado(resultado.name());
        auditoria.sistema("EVENTO_DE_PAGAMENTO", ev.pedidoId() == null ? null : ev.pedidoId().toString(), ev.tipo() + " -> " + resultado);
        return resultado;
    }

    private Resultado decidir(EventoDePagamento ev, Instant agora) {
        Pedido pedido = ev.pedidoId() == null ? null : pedidos.findById(ev.pedidoId()).orElse(null);
        if (pedido == null) {
            return Resultado.IGNORADO_PEDIDO_DESCONHECIDO;
        }
        if (ev.valorEmCentavos() != pedido.getValorCentavos() || !pedido.getMoeda().equals(ev.moeda())) {
            auditoria.sistema("EVENTO_DE_PAGAMENTO_DIVERGENTE", pedido.getId().toString(), "valor ou moeda diferentes do pedido");
            return Resultado.IGNORADO_VALOR_DIVERGENTE;
        }
        Plano plano = planos.findById(pedido.getPlanoId()).orElse(null);
        if (plano == null) {
            return Resultado.IGNORADO_PEDIDO_DESCONHECIDO;
        }
        Assinatura assinatura = assinaturas.bloquearPorConta(pedido.getContaId()).orElseGet(() -> assinaturas.save(new Assinatura(pedido.getContaId(), plano.getId(), agora)));
        if (assinatura.getUltimoEventoEm() != null && ev.criadoEm().isBefore(assinatura.getUltimoEventoEm())) {
            return Resultado.IGNORADO_FORA_DE_ORDEM;
        }
        boolean pedidoEncerrado = pedido.getSituacao() == Pedido.Situacao.REEMBOLSADO || pedido.getSituacao() == Pedido.Situacao.EM_DISPUTA;
        switch (ev.tipo()) {
            case PAGAMENTO_APROVADO, RENOVACAO_APROVADA -> {
                boolean renovacao = ev.tipo() == br.com.instadino.contratos.pagamento.TipoDeEventoDePagamento.RENOVACAO_APROVADA;
                boolean semDireitoParaRenovar = assinatura.getSituacao() == Assinatura.Situacao.REVOGADA || assinatura.getSituacao() == Assinatura.Situacao.SUSPENSA
                        || assinatura.getPeriodoFim() == null;
                if (pedidoEncerrado || (renovacao && semDireitoParaRenovar)) {
                    return Resultado.IGNORADO_TRANSICAO_INVALIDA;
                }
                Instant base = assinatura.getPeriodoFim() != null && assinatura.getPeriodoFim().isAfter(ev.criadoEm()) && assinatura.getSituacao() != Assinatura.Situacao.REVOGADA
                        && assinatura.getSituacao() != Assinatura.Situacao.SUSPENSA ? assinatura.getPeriodoFim() : ev.criadoEm();
                assinatura.atualizar(Assinatura.Situacao.ATIVA, plano.getId(), base.plus(Duration.ofDays(plano.diasDoPeriodo())), ev.criadoEm(), agora);
                pedido.definirSituacao(Pedido.Situacao.PAGO, agora);
            }
            case PAGAMENTO_RECUSADO -> {
                if (pedido.getSituacao() == Pedido.Situacao.PENDENTE) {
                    pedido.definirSituacao(Pedido.Situacao.RECUSADO, agora);
                }
                Assinatura.Situacao nova = assinatura.getSituacao() == Assinatura.Situacao.ATIVA ? Assinatura.Situacao.PAGAMENTO_PENDENTE : assinatura.getSituacao();
                assinatura.atualizar(nova, assinatura.getPlanoId(), assinatura.getPeriodoFim(), ev.criadoEm(), agora);
            }
            case ASSINATURA_CANCELADA -> {
                boolean cancelavel = assinatura.getSituacao() == Assinatura.Situacao.ATIVA || assinatura.getSituacao() == Assinatura.Situacao.PAGAMENTO_PENDENTE;
                assinatura.atualizar(cancelavel ? Assinatura.Situacao.CANCELADA : assinatura.getSituacao(), assinatura.getPlanoId(), assinatura.getPeriodoFim(),
                        ev.criadoEm(), agora);
            }
            case PAGAMENTO_REEMBOLSADO -> {
                pedido.definirSituacao(Pedido.Situacao.REEMBOLSADO, agora);
                assinatura.atualizar(Assinatura.Situacao.REVOGADA, assinatura.getPlanoId(), ev.criadoEm(), ev.criadoEm(), agora);
                licencas.revogarTodasDaConta(pedido.getContaId(), agora);
            }
            case DISPUTA_ABERTA -> {
                pedido.definirSituacao(Pedido.Situacao.EM_DISPUTA, agora);
                assinatura.atualizar(Assinatura.Situacao.SUSPENSA, assinatura.getPlanoId(), ev.criadoEm(), ev.criadoEm(), agora);
                licencas.revogarTodasDaConta(pedido.getContaId(), agora);
            }
            default -> {
                return Resultado.IGNORADO_TRANSICAO_INVALIDA;
            }
        }
        return Resultado.PROCESSADO;
    }
}
