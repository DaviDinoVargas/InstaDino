package br.com.instadino.desktop;

import br.com.instadino.desktop.util.DiretorioDeDados;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetoresPadrao;
import br.com.instadino.servicos.ContextoLocal;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Aplicação JavaFX: abre o núcleo local protegido e exibe a janela principal. */
public final class AplicativoInstaDino extends Application {

    private ContextoLocal local;

    @Override
    public void start(Stage palco) {
        palco.setTitle("InstaDino");
        palco.setMinWidth(1000);
        palco.setMinHeight(680);
        try {
            // Nesta etapa não há cliente de licenciamento: o plano efetivo é sempre Free.
            local = ContextoLocal.abrir(DiretorioDeDados.padrao(), ProtetoresPadrao.escolher(), () -> EstadoDaLicenca.GRATUITO);
        } catch (RuntimeException e) {
            System.getLogger(AplicativoInstaDino.class.getName())
                    .log(System.Logger.Level.ERROR, "Falha ao iniciar o núcleo local ({0}).", e.getClass().getSimpleName());
            palco.setScene(cenaDeFalha());
            palco.show();
            return;
        }
        JanelaPrincipal janela = new JanelaPrincipal(local, url -> getHostServices().showDocument(url));
        palco.setScene(janela.criarCena());
        palco.show();
    }

    @Override
    public void stop() {
        if (local != null) {
            local.close();
        }
    }

    private static Scene cenaDeFalha() {
        Label titulo = new Label("Não foi possível iniciar a proteção dos dados locais");
        titulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ecedf6;");
        Label texto = new Label("O InstaDino não cria o banco de dados quando não consegue proteger a chave de cifragem. "
                + "Isso normalmente indica que o sistema não oferece o mecanismo seguro necessário (DPAPI no Windows) ou que os "
                + "arquivos locais estão corrompidos. Nenhum dado foi alterado.");
        texto.setWrapText(true);
        texto.setStyle("-fx-text-fill: #a0a1b8;");
        VBox caixa = new VBox(14, titulo, texto);
        caixa.setPadding(new Insets(40));
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.setStyle("-fx-background-color: #0b0b10;");
        return new Scene(caixa, 720, 300);
    }
}
