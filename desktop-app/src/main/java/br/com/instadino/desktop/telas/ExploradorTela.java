package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import br.com.instadino.servicos.ServicoDeExplorador.LinhaDeCategoria;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Explorador de dados exportados (Premium): o que havia na exportação e o que o InstaDino fez com cada categoria. */
final class ExploradorTela extends TelaBase {

    private final VBox area = new VBox(16);

    ExploradorTela(ContextoDaInterface ui) {
        super(ui, "Explorador de dados", "O que a sua exportação continha e o que o InstaDino leu de cada categoria.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        if (!ui.local().politica().permite(br.com.instadino.nucleo.acesso.Funcionalidade.EXPLORADOR_DE_DADOS)) {
            area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Explorador de dados (Premium)"),
                    Ui.suave("Veja, categoria por categoria, quantos arquivos e registros cada importação trouxe e quais foram lidos, "
                            + "ficaram incompletos ou foram deixados de fora por serem sensíveis."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
            return;
        }
        var importacoes = ui.local().explorador().importacoes();
        if (importacoes.isEmpty()) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Nenhuma importação ainda", "Importe uma exportação para explorar o que ela contém.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        ComboBox<ResumoDaImportacao> seletor = new ComboBox<>();
        seletor.getItems().setAll(importacoes);
        seletor.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ResumoDaImportacao i) {
                return i == null ? "" : Formatos.dataHora(i.importadaEm()) + (i.nomeDoArquivo() == null ? "" : " — " + i.nomeDoArquivo());
            }

            @Override
            public ResumoDaImportacao fromString(String s) {
                return null;
            }
        });
        seletor.setAccessibleText("Importação a explorar");
        TableView<LinhaDeCategoria> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Categoria", l -> l.categoria().rotulo(), 280));
        tabela.getColumns().add(coluna("Arquivos", l -> Formatos.inteiro(l.arquivos()), 90));
        tabela.getColumns().add(coluna("Registros lidos", l -> Formatos.inteiro(l.registros()), 120));
        tabela.getColumns().add(coluna("Situação", l -> l.situacao().rotulo() + (l.incompleta() ? " (possivelmente incompleta)" : ""), 320));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(520);
        tabela.setPlaceholder(new Label("Nenhuma categoria registrada."));
        tabela.setAccessibleText("Categorias encontradas na importação selecionada");
        seletor.setOnAction(e -> tabela.getItems().setAll(ui.local().explorador().categorias(seletor.getValue().id())));
        seletor.getSelectionModel().selectFirst();
        tabela.getItems().setAll(ui.local().explorador().categorias(importacoes.get(0).id()));
        area.getChildren().addAll(
                Ui.caixa(Ui.Tipo.INFO, "Categorias sensíveis (perfil, segurança e, sem a sua autorização, mensagens) aparecem aqui apenas como contagem de arquivos: o conteúdo não é lido."),
                new HBox(8, new Label("Importação:"), seletor), Ui.cartao(Ui.tituloDeCartao("Categorias"), tabela));
    }

    private static <T> TableColumn<T, String> coluna(String titulo, java.util.function.Function<T, String> valor, double largura) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
