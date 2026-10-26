package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.servicos.ResumoDoPainel;
import br.com.instadino.servicos.VisaoDeRelacionamento;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Painel social: indicadores da importação mais recente. */
final class PainelTela extends TelaBase {

    PainelTela(ContextoDaInterface ui) {
        super(ui, "Painel social", "Resumo calculado localmente a partir da sua exportação do Instagram.");
    }

    @Override
    public void aoExibir() {
        limparCorpo();
        var painel = ui.local().relacionamentos().painel();
        if (painel.isEmpty()) {
            corpo(Ui.cartao(Ui.estadoVazio("Nenhuma exportação importada ainda",
                    "Importe o arquivo ZIP da exportação oficial do Instagram para ver seus seguidores, contas seguidas e relações mútuas.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        ResumoDoPainel p = painel.get();
        FlowPane metricas = new FlowPane(16, 16);
        metricas.getChildren().addAll(
                Ui.metrica("Seguidores", new Label(Formatos.inteiro(p.seguidores())), null),
                Ui.metrica("Seguindo", new Label(Formatos.inteiro(p.seguindo())), null),
                Ui.metrica("Relações mútuas", new Label(Formatos.inteiro(p.mutuos())), "Aparecem nas duas listas"),
                Ui.metrica("Não seguem de volta", new Label(Formatos.inteiro(p.naoSeguemDeVolta())), "Seguidos que não aparecem entre seus seguidores"),
                Ui.metrica("Você não segue de volta", new Label(Formatos.inteiro(p.voceNaoSegue())), "Seguidores que você não segue"),
                Ui.metrica("Seguidores por conta seguida", new Label(p.proporcao() == null ? "—" : Formatos.decimal(p.proporcao())), null));
        metricas.getChildren().forEach(n -> ((VBox) n).setPrefWidth(220));

        HBox linha = new HBox(16, graficoDeComposicao(p), resumoDaImportacao(p));
        HBox.setHgrow(linha.getChildren().get(0), Priority.ALWAYS);
        ((VBox) linha.getChildren().get(1)).setMinWidth(320);
        ((VBox) linha.getChildren().get(1)).setMaxWidth(380);

        corpo(metricas, linha, Ui.caixa(Ui.Tipo.AVISO, AnaliseDeSeguidores.LIMITACAO), atalhos());
        if (!p.comparacaoDisponivel()) {
            pagina.getChildren().add(1, Ui.caixa(Ui.Tipo.AVISO,
                    "A lista de seguidores ou a de contas seguidas não foi encontrada ou veio vazia. Os indicadores de comparação podem não refletir a realidade."));
        }
    }

    private VBox graficoDeComposicao(ResumoDoPainel p) {
        PieChart grafico = new PieChart(FXCollections.observableArrayList(
                new PieChart.Data("Mútuos", p.mutuos()),
                new PieChart.Data("Não seguem de volta", p.naoSeguemDeVolta()),
                new PieChart.Data("Você não segue de volta", p.voceNaoSegue())));
        grafico.setLegendVisible(true);
        grafico.setLabelsVisible(false);
        grafico.setMinHeight(260);
        grafico.setPrefHeight(300);
        grafico.setMaxHeight(320);
        grafico.setLegendSide(javafx.geometry.Side.BOTTOM);
        grafico.setAccessibleText("Gráfico de composição: " + p.mutuos() + " mútuos, " + p.naoSeguemDeVolta()
                + " não seguem de volta, " + p.voceNaoSegue() + " você não segue de volta.");
        return Ui.cartao(Ui.tituloDeCartao("Composição dos relacionamentos"), grafico);
    }

    private VBox resumoDaImportacao(ResumoDoPainel p) {
        var imp = p.importacao();
        VBox caixa = Ui.cartaoDestaque(Ui.tituloDeCartao("Última importação"),
                linha("Data", Formatos.dataHora(imp.importadaEm())),
                linha("Arquivo", imp.nomeDoArquivo() == null ? "—" : imp.nomeDoArquivo()),
                linha("Registros válidos", Formatos.inteiro(imp.registrosValidos())),
                linha("Registros inválidos", Formatos.inteiro(imp.registrosInvalidos())),
                linha("Versão do leitor", imp.versaoDoParser()),
                Ui.secundario("Ver histórico", () -> ui.roteador().ir(Destino.HISTORICO)));
        return caixa;
    }

    private static HBox linha(String rotulo, String valor) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("texto-suave");
        Label v = new Label(valor);
        v.setWrapText(true);
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        v.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        HBox h = new HBox(12, r, v);
        h.setPadding(new Insets(2, 0, 2, 0));
        return h;
    }

    private HBox atalhos() {
        HBox h = new HBox(10);
        for (VisaoDeRelacionamento v : new VisaoDeRelacionamento[] {VisaoDeRelacionamento.SEGUIDORES, VisaoDeRelacionamento.SEGUINDO,
            VisaoDeRelacionamento.MUTUOS, VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA}) {
            h.getChildren().add(Ui.secundario(v.rotulo(), () -> {
                ((RelacionamentosTela) ui.roteador().tela(Destino.RELACIONAMENTOS)).abrir(v, "");
                ui.roteador().ir(Destino.RELACIONAMENTOS);
            }));
        }
        return h;
    }
}
