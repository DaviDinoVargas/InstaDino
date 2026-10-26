package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.navegacao.Tela;

/** Fábrica das telas do aplicativo. */
public final class Telas {

    private Telas() {
    }

    public static Tela criar(Destino destino, ContextoDaInterface ui) {
        return switch (destino) {
            case BOAS_VINDAS -> new BoasVindasTela(ui);
            case PAINEL -> new PainelTela(ui);
            case IMPORTACAO -> new ImportacaoTela(ui);
            case RELACIONAMENTOS -> new RelacionamentosTela(ui);
            case HISTORICO -> new HistoricoTela(ui);
            case RELATORIOS -> new RelatoriosTela(ui);
            case CONTA -> new ContaTela(ui);
            case PREMIUM -> new PremiumTela(ui);
            case SEGURANCA -> new SegurancaTela(ui);
            case CONFIGURACOES -> new ConfiguracoesTela(ui);
        };
    }

    /** Pede à tela de relacionamentos que abra uma visão com um termo de busca. */
    public static void abrirRelacionamentos(ContextoDaInterface ui, br.com.instadino.servicos.VisaoDeRelacionamento visao, String busca) {
        ((RelacionamentosTela) ui.roteador().tela(Destino.RELACIONAMENTOS)).abrir(visao, busca);
        ui.roteador().ir(Destino.RELACIONAMENTOS);
    }
}
