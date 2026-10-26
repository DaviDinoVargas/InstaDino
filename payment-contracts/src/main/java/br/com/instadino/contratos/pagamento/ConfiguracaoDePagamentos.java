package br.com.instadino.contratos.pagamento;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuração central de pagamentos. FALHA FECHADA:
 * <ul>
 *   <li>{@code PAYMENTS_ENABLED} ausente, inválido ou diferente de "true" significa desabilitado;</li>
 *   <li>{@code PAYMENTS_MODE} ausente ou desconhecido significa sandbox (nunca produção);</li>
 *   <li>produção só é ativa com: habilitado, modo produção, segredo do webhook, credencial do provedor e
 *       {@code PAYMENTS_PRODUCTION_APPROVED=true} (aprovação explícita registrada);</li>
 *   <li>produção incompleta é {@link SituacaoDosPagamentos#PRODUCAO_INCOMPLETA}: sem compras.</li>
 * </ul>
 */
public record ConfiguracaoDePagamentos(boolean habilitado, ModoDePagamento modo, boolean segredoDoWebhookPresente,
                                       boolean credencialDoProvedorPresente, boolean producaoAprovada) {

    public static ConfiguracaoDePagamentos deAmbiente(Map<String, String> ambiente) {
        boolean habilitado = "true".equals(valor(ambiente, "PAYMENTS_ENABLED"));
        String modoTexto = valor(ambiente, "PAYMENTS_MODE").toLowerCase(Locale.ROOT);
        ModoDePagamento modo = modoTexto.equals("production") || modoTexto.equals("producao") ? ModoDePagamento.PRODUCAO : ModoDePagamento.SANDBOX;
        return new ConfiguracaoDePagamentos(habilitado, modo,
                !valor(ambiente, "PAYMENTS_WEBHOOK_SECRET").isBlank(),
                !valor(ambiente, "PAYMENTS_PROVIDER_API_KEY").isBlank(),
                "true".equals(valor(ambiente, "PAYMENTS_PRODUCTION_APPROVED")));
    }

    private static String valor(Map<String, String> ambiente, String chave) {
        String v = ambiente.get(chave);
        return v == null ? "" : v.strip();
    }

    public SituacaoDosPagamentos situacao() {
        if (!habilitado) {
            return SituacaoDosPagamentos.DESABILITADO;
        }
        if (modo == ModoDePagamento.SANDBOX) {
            return SituacaoDosPagamentos.SANDBOX_ATIVO;
        }
        return segredoDoWebhookPresente && credencialDoProvedorPresente && producaoAprovada
                ? SituacaoDosPagamentos.PRODUCAO_ATIVA : SituacaoDosPagamentos.PRODUCAO_INCOMPLETA;
    }

    /** Verdadeiro somente se for permitido cobrar de verdade. */
    public boolean cobrancaRealPermitida() {
        return situacao() == SituacaoDosPagamentos.PRODUCAO_ATIVA;
    }

    /** Verdadeiro se o servidor pode criar pedidos (de teste ou reais). */
    public boolean comprasPermitidas() {
        SituacaoDosPagamentos s = situacao();
        return s == SituacaoDosPagamentos.SANDBOX_ATIVO || s == SituacaoDosPagamentos.PRODUCAO_ATIVA;
    }

    /** Itens que faltam para ativar a produção; vazio quando completa ou quando a produção não foi solicitada. */
    public List<String> pendenciasDeProducao() {
        if (!habilitado || modo != ModoDePagamento.PRODUCAO) {
            return List.of();
        }
        java.util.ArrayList<String> faltas = new java.util.ArrayList<>();
        if (!segredoDoWebhookPresente) {
            faltas.add("PAYMENTS_WEBHOOK_SECRET");
        }
        if (!credencialDoProvedorPresente) {
            faltas.add("PAYMENTS_PROVIDER_API_KEY");
        }
        if (!producaoAprovada) {
            faltas.add("PAYMENTS_PRODUCTION_APPROVED");
        }
        return faltas;
    }
}
