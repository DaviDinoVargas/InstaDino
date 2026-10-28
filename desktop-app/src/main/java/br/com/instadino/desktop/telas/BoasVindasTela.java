package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Primeira tela: apresentação do produto, aviso de privacidade e caminhos iniciais. */
final class BoasVindasTela extends TelaBase {

    BoasVindasTela(ContextoDaInterface ui) {
        super(ui, "Bem-vindo ao InstaDino", "Entenda sua conta do Instagram a partir dos dados que você mesmo exporta.");
        Label chamada = new Label("Veja quem segue você, quem você segue e o que mudou entre exportações — sem informar sua senha do Instagram.");
        chamada.setWrapText(true);
        chamada.getStyleClass().add("titulo-cartao");
        HBox acoes = new HBox(12,
                Ui.primario("Importar minha exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)),
                Ui.secundario("Continuar com o plano Free", () -> ui.roteador().ir(Destino.PAINEL)));
        VBox destaque = Ui.cartaoDestaque(chamada,
                Ui.suave("O plano Free é gratuito, não expira e não exige cadastro. Recursos Premium são opcionais."), acoes);

        FlowPane pilares = new FlowPane(16, 16,
                pilar("Tudo fica no seu computador",
                        "A exportação é lida e analisada localmente e gravada em um banco cifrado. Os dados dos seus seguidores não são enviados a nenhum servidor."),
                pilar("Sem senha do Instagram",
                        "O InstaDino usa apenas o arquivo ZIP oficial que você baixa do Instagram. Não faz login, não usa serviços privados e não automatiza a plataforma."),
                pilar("Resultados com transparência",
                        "Uma conta ausente na lista de seguidores não prova que deixou de seguir você. O aplicativo mostra o que está nos arquivos e explica os limites."));
        pilares.getChildren().forEach(n -> ((VBox) n).setPrefWidth(340));

        corpo(destaque, pilares, Ui.caixa(Ui.Tipo.INFO,
                "Privacidade: você pode excluir todos os dados locais a qualquer momento em Segurança e privacidade."));
        pagina.setPadding(new Insets(28, 32, 32, 32));
    }

    private static VBox pilar(String titulo, String texto) {
        return Ui.cartao(Ui.tituloDeCartao(titulo), Ui.suave(texto));
    }
}
