package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.servicos.VisaoDeRelacionamento;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Exportação de listas em CSV (plano Free). */
final class RelatoriosTela extends TelaBase {

    private final VBox lista = new VBox(8);

    RelatoriosTela(ContextoDaInterface ui) {
        super(ui, "Relatórios", "Exporte suas listas em CSV para abrir em planilhas.");
        VBox cartao = Ui.cartao(Ui.tituloDeCartao("Listas disponíveis"), lista);
        corpo(cartao,
                Ui.cartao(Ui.tituloDeCartao("Exportar tudo"),
                        Ui.suave("Grava todas as listas da última importação em arquivos CSV dentro de uma pasta que você escolher."),
                        Ui.secundario("Exportar todas as listas…", this::exportarTudo)),
                Ui.caixa(Ui.Tipo.INFO, "Os arquivos CSV usam ponto e vírgula, codificação UTF-8 e protegem contra fórmulas de planilha. "
                        + "Relatórios em PDF, gráficos históricos e relatórios comparativos são recursos do plano Premium e ainda estão em desenvolvimento."));
    }

    @Override
    public void aoExibir() {
        lista.getChildren().clear();
        boolean temDados = ui.local().relacionamentos().painel().isPresent();
        for (VisaoDeRelacionamento v : VisaoDeRelacionamento.values()) {
            Label nome = new Label(v.rotulo());
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            var botao = Ui.secundario("Exportar CSV…", () -> exportar(v));
            botao.setDisable(!temDados);
            HBox linha = new HBox(12, nome, espaco, botao);
            linha.setAlignment(Pos.CENTER_LEFT);
            lista.getChildren().add(linha);
        }
        if (!temDados) {
            lista.getChildren().add(Ui.suave("Importe uma exportação para habilitar os relatórios."));
        }
    }

    private static String nomeDeArquivo(VisaoDeRelacionamento v) {
        return "instadino-" + v.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ".csv";
    }

    private void exportar(VisaoDeRelacionamento v) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar " + v.rotulo());
        chooser.setInitialFileName(nomeDeArquivo(v));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv"));
        File destino = chooser.showSaveDialog(ui.janela());
        if (destino == null) {
            return;
        }
        Tarefas.executar(p -> {
            try (OutputStream saida = Files.newOutputStream(destino.toPath())) {
                ui.local().exportacao().exportarCsv(v, saida);
            }
            return destino.getName();
        }, nome -> ui.notificador().sucesso("Arquivo " + nome + " salvo."), erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }

    private void exportarTudo() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher pasta de destino");
        File pasta = chooser.showDialog(ui.janela());
        if (pasta == null) {
            return;
        }
        Tarefas.executar(p -> {
            int gravados = 0;
            for (VisaoDeRelacionamento v : VisaoDeRelacionamento.values()) {
                if (ui.local().relacionamentos().todas(v).isEmpty()) {
                    continue;
                }
                Path arquivo = pasta.toPath().resolve(nomeDeArquivo(v));
                try (OutputStream saida = Files.newOutputStream(arquivo)) {
                    ui.local().exportacao().exportarCsv(v, saida);
                }
                gravados++;
            }
            return gravados;
        }, n -> ui.notificador().sucesso(n + " arquivo(s) CSV salvos na pasta escolhida."),
                erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }
}
