package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.servicos.ServicoDeMensagens;
import br.com.instadino.servicos.ServicoDeMensagens.Contato;
import br.com.instadino.servicos.ServicoDeMensagens.Mes;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Estatísticas das mensagens (Premium, módulo opcional com consentimento). Mostra apenas contagens e datas; nenhuma
 * parte desta tela lê ou exibe o texto das conversas, que nem existe no banco.
 */
final class MensagensTela extends TelaBase {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private final VBox area = new VBox(16);

    MensagensTela(ContextoDaInterface ui) {
        super(ui, "Mensagens", "Estatísticas de conversas: só contagens e datas, nunca o texto.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        ServicoDeMensagens servico = ui.local().mensagens();
        if (ui.local().politica().planoEfetivo() != Plano.PREMIUM) {
            area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Estatísticas de mensagens (Premium)"),
                    Ui.suave("Veja com quem você mais conversa e como isso evoluiu mês a mês. É um módulo opcional: só funciona se você autorizar, "
                            + "e guarda apenas contagens e datas."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
            if (servico.consentimentoConcedido()) {
                area.getChildren().add(gerenciamento(servico));
            }
            return;
        }
        area.getChildren().add(Ui.caixa(Ui.Tipo.INFO, ServicoDeMensagens.LIMITACAO));
        if (!servico.consentimentoConcedido()) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Módulo de mensagens desligado",
                    "Para usar, importe uma exportação que inclua as mensagens e marque \"Incluir estatísticas das minhas mensagens\". "
                            + "Você será informado do que é guardado antes de qualquer leitura.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        if (!servico.temDados()) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Sem mensagens na importação mais recente",
                    "O módulo está autorizado, mas a importação mais recente não incluiu mensagens. Importe de novo marcando a opção "
                            + "de mensagens, usando uma exportação em JSON que contenha as conversas.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            area.getChildren().add(gerenciamento(servico));
            return;
        }
        var resumo = servico.resumo().orElseThrow();
        FlowPane metricas = new FlowPane(16, 16,
                metrica("Conversas", Formatos.inteiro(resumo.conversas()), Formatos.inteiro(resumo.grupos()) + " em grupo"),
                metrica("Mensagens", Formatos.inteiro(resumo.mensagens()), periodo(resumo)),
                metrica("Enviadas por você", resumo.enviadas() == null ? "—" : Formatos.inteiro(resumo.enviadas()),
                        resumo.enviadas() == null ? "Titular não identificado" : null),
                metrica("Recebidas", resumo.recebidas() == null ? "—" : Formatos.inteiro(resumo.recebidas()), null),
                metrica("Com mídia", Formatos.inteiro(resumo.midias()), "Foto, vídeo, áudio, GIF ou figurinha"));
        area.getChildren().add(metricas);
        if (resumo.enviadas() == null) {
            area.getChildren().add(Ui.caixa(Ui.Tipo.AVISO,
                    "Não foi possível identificar qual participante é você (por exemplo, quando só há uma conversa). Por isso as contagens de enviadas e recebidas não são exibidas."));
        }
        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Mensagens por mês"), graficoPorMes(servico.porMes())));
        area.getChildren().add(ranking(servico));
        area.getChildren().add(gerenciamento(servico));
    }

    private static String periodo(ServicoDeMensagens.Resumo r) {
        return r.primeira() == null ? "sem datas" : Formatos.data(r.primeira()) + " a " + Formatos.data(r.ultima());
    }

    private static VBox metrica(String rotulo, String valor, String detalhe) {
        VBox m = Ui.metrica(rotulo, new Label(valor), detalhe);
        m.setPrefWidth(230);
        return m;
    }

    private BarChart<String, Number> graficoPorMes(List<Mes> meses) {
        BarChart<String, Number> g = new BarChart<>(new CategoryAxis(), new NumberAxis());
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.setPrefHeight(300);
        g.setAccessibleText("Gráfico de barras com as mensagens por mês");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        meses.forEach(m -> s.getData().add(new XYChart.Data<>(MES.format(m.mes()), m.total())));
        g.getData().add(s);
        return g;
    }

    private VBox ranking(ServicoDeMensagens servico) {
        TableView<Contato> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Conversa", c -> c.grupo() ? c.titulo() + " (grupo)" : c.titulo(), 260));
        tabela.getColumns().add(coluna("Mensagens", c -> Formatos.inteiro(c.total()), 100));
        tabela.getColumns().add(coluna("Enviadas por você", c -> c.enviadas() == null ? "—" : Formatos.inteiro(c.enviadas()), 130));
        tabela.getColumns().add(coluna("Com mídia", c -> Formatos.inteiro(c.midias()), 100));
        tabela.getColumns().add(coluna("Primeira", c -> c.primeira() == null ? "—" : Formatos.data(c.primeira()), 110));
        tabela.getColumns().add(coluna("Última", c -> c.ultima() == null ? "—" : Formatos.data(c.ultima()), 110));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(420);
        tabela.setAccessibleText("Conversas com mais mensagens");
        tabela.setPlaceholder(new Label("Nenhuma conversa."));
        CheckBox grupos = new CheckBox("Incluir grupos");
        grupos.setSelected(true);
        Runnable carregar = () -> tabela.getItems().setAll(servico.ranking(100, grupos.isSelected()));
        grupos.setOnAction(e -> carregar.run());
        carregar.run();
        return Ui.cartao(Ui.tituloDeCartao("Conversas mais movimentadas"), grupos, tabela);
    }

    private VBox gerenciamento(ServicoDeMensagens servico) {
        return Ui.cartao(Ui.tituloDeCartao("Seus dados de mensagens"),
                Ui.suave("Você pode revogar a autorização a qualquer momento. Isso apaga todas as estatísticas de mensagens guardadas, de todas as importações."),
                Ui.perigo("Revogar e apagar estatísticas de mensagens", () -> {
                    Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
                            "Apagar todas as estatísticas de mensagens e revogar a autorização? Para voltar a usar o módulo será preciso importar de novo.",
                            ButtonType.OK, ButtonType.CANCEL);
                    confirmacao.setHeaderText("Apagar estatísticas de mensagens?");
                    confirmacao.initOwner(ui.janela());
                    if (confirmacao.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        int apagadas = servico.revogarEApagar();
                        ui.notificador().sucesso("Autorização revogada; " + Formatos.inteiro(apagadas) + " conversas apagadas.");
                        aoExibir();
                    }
                }));
    }

    private static <T> TableColumn<T, String> coluna(String titulo, java.util.function.Function<T, String> valor, double largura) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
