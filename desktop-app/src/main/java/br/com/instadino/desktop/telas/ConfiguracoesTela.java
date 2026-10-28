package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.importacao.ImportadorDeExportacao;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Tema, idioma, animações, diretório de dados e diagnóstico. */
final class ConfiguracoesTela extends TelaBase {

    private final ComboBox<String> tema = new ComboBox<>();
    private final CheckBox reduzir = new CheckBox("Reduzir animações");
    private boolean carregando;

    ConfiguracoesTela(ContextoDaInterface ui) {
        super(ui, "Configurações", "Aparência, idioma e informações do aplicativo.");
        tema.getItems().setAll("Escuro", "Claro");
        tema.setAccessibleText("Tema");
        tema.setOnAction(e -> {
            if (!carregando) {
                ui.local().preferencias().definir("tema", "Claro".equals(tema.getValue()) ? "claro" : "escuro");
                ui.estadoMudou();
            }
        });
        reduzir.setOnAction(e -> {
            if (!carregando) {
                ui.local().preferencias().definir("reduzir_animacoes", Boolean.toString(reduzir.isSelected()));
            }
        });
        Label idioma = new Label("Português (Brasil)");
        Label aviso = Ui.pequeno("É o único idioma disponível nesta versão.");
        Label diagnostico = Ui.suave("");
        Label resultadoIntegridade = Ui.suave("");

        VBox aparencia = Ui.cartao(Ui.tituloDeCartao("Aparência"), new HBox(12, new Label("Tema:"), tema), reduzir,
                Ui.pequeno("A preferência de reduzir animações também desativa transições entre telas."));
        VBox idiomaCartao = Ui.cartao(Ui.tituloDeCartao("Idioma"), idioma, aviso);
        VBox dados = Ui.cartao(Ui.tituloDeCartao("Dados do aplicativo"),
                Ui.suave("Pasta de dados: " + ui.local().diretorioDeDados()),
                Ui.secundario("Copiar caminho", () -> {
                    ClipboardContent c = new ClipboardContent();
                    c.putString(ui.local().diretorioDeDados().toString());
                    Clipboard.getSystemClipboard().setContent(c);
                    ui.notificador().info("Caminho copiado.");
                }),
                Ui.pequeno("O backup protegido por senha fica em Segurança e privacidade. A troca da pasta de dados ainda não está disponível nesta versão."));
        diagnostico.setText("InstaDino 0.1.0 (desenvolvimento)  •  Java " + System.getProperty("java.version") + "  •  "
                + System.getProperty("os.name") + "  •  leitor de exportações " + ImportadorDeExportacao.VERSAO_DO_PARSER);
        VBox diag = Ui.cartao(Ui.tituloDeCartao("Diagnóstico"), diagnostico,
                Ui.secundario("Verificar integridade do banco", () -> Tarefas.executar(
                        p -> ui.local().dados().situacao(),
                        s -> resultadoIntegridade.setText(s.integridadeOk() && s.cifragemAtiva()
                                ? "Banco íntegro e cifrado." : "Foram encontrados problemas. Evite novas importações e verifique em Segurança e privacidade."),
                        e -> resultadoIntegridade.setText(Mensagens.paraUsuario(e)))),
                resultadoIntegridade);
        corpo(aparencia, idiomaCartao, dados, diag);
    }

    @Override
    public void aoExibir() {
        carregando = true;
        tema.setValue("claro".equals(ui.local().preferencias().obter("tema").orElse("escuro")) ? "Claro" : "Escuro");
        reduzir.setSelected(ui.reduzirAnimacoes());
        carregando = false;
    }
}
