package br.com.instadino.servidor.config;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import br.com.instadino.contratos.pagamento.SituacaoDosPagamentos;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de configuração segura. Em produção qualquer violação IMPEDE a inicialização do servidor.
 * Lógica pura, sem Spring, para ser testada isoladamente.
 */
public final class AvaliadorDeConfiguracaoSegura {

    /** Fotografia da configuração relevante para a segurança. */
    public record Instantaneo(boolean producao, String urlDoBanco, boolean tlsDiretoOuNoProxy, boolean chaveDeLicencaConfigurada,
                              boolean semeaduraHabilitada, boolean emailRealConfigurado, boolean documentacaoDaApiExposta,
                              ConfiguracaoDePagamentos pagamentos, boolean consoleDoBancoHabilitado) {
    }

    private AvaliadorDeConfiguracaoSegura() {
    }

    /** Lista de problemas; vazia significa que a configuração é aceitável para o ambiente informado. */
    public static List<String> avaliar(Instantaneo c) {
        List<String> problemas = new ArrayList<>();
        if (!c.producao()) {
            return problemas;
        }
        if (c.urlDoBanco() == null || !c.urlDoBanco().startsWith("jdbc:postgresql:")) {
            problemas.add("O banco de produção deve ser PostgreSQL (jdbc:postgresql:...), não H2.");
        }
        if (!c.tlsDiretoOuNoProxy()) {
            problemas.add("TLS é obrigatório: configure server.ssl ou declare instadino.seguranca.tls-terminado-no-proxy=true.");
        }
        if (!c.chaveDeLicencaConfigurada()) {
            problemas.add("A chave privada de assinatura de licenças (instadino.licenca.chave-privada-arquivo) não está configurada.");
        }
        if (c.semeaduraHabilitada()) {
            problemas.add("A semeadura de contas de demonstração deve estar desabilitada em produção.");
        }
        if (!c.emailRealConfigurado()) {
            problemas.add("O envio real de e-mail não está configurado (verificação de conta e recuperação de senha dependem dele).");
        }
        if (c.documentacaoDaApiExposta()) {
            problemas.add("A documentação interativa da API (Swagger UI) não deve ficar exposta em produção.");
        }
        if (c.consoleDoBancoHabilitado()) {
            problemas.add("O console web do banco de dados deve estar desabilitado em produção.");
        }
        SituacaoDosPagamentos situacao = c.pagamentos().situacao();
        if (situacao == SituacaoDosPagamentos.PRODUCAO_INCOMPLETA) {
            problemas.add("Pagamentos em produção solicitados com configuração incompleta: " + c.pagamentos().pendenciasDeProducao());
        }
        if (situacao == SituacaoDosPagamentos.SANDBOX_ATIVO) {
            problemas.add("Pagamentos em modo sandbox não podem ficar habilitados em produção.");
        }
        if (situacao == SituacaoDosPagamentos.PRODUCAO_ATIVA) {
            problemas.add("O adaptador do provedor de pagamento real ainda não foi implementado; a cobrança em produção permanece bloqueada.");
        }
        return problemas;
    }
}
