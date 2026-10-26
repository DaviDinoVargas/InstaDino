package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.servicos.ServicoDeDados.SituacaoDaProtecao;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Estado da proteção local, eventos de segurança e exclusão de dados. */
final class SegurancaTela extends TelaBase {

    private final VBox area = new VBox(16);

    SegurancaTela(ContextoDaInterface ui) {
        super(ui, "Segurança e privacidade", "O que está armazenado, como está protegido e como apagar.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        SituacaoDaProtecao s = ui.local().dados().situacao();

        FlowPane indicadores = new FlowPane(16, 16,
                indicador("Cifragem do banco", s.cifragemAtiva() ? "Ativa" : "INATIVA", s.cifragemAtiva() ? "ChaCha20-Poly1305" : "Não use o aplicativo até corrigir", s.cifragemAtiva()),
                indicador("Integridade", s.integridadeOk() ? "Íntegro" : "Problemas encontrados", "Verificação do banco local", s.integridadeOk()),
                indicador("Proteção da chave", s.seguroParaProducao() ? "Segura" : "Modo de teste", s.mecanismoDeChave(), s.seguroParaProducao()));
        indicadores.getChildren().forEach(n -> ((VBox) n).setPrefWidth(300));
        area.getChildren().add(indicadores);
        if (!s.seguroParaProducao()) {
            area.getChildren().add(Ui.caixa(Ui.Tipo.ERRO,
                    "Este computador está usando um modo de desenvolvimento sem proteção real da chave do banco. Não use dados reais neste modo."));
        }

        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Dados armazenados"),
                Ui.suave("Perfis locais: " + Formatos.inteiro(s.perfis()) + "  •  Importações: " + Formatos.inteiro(s.importacoes())
                        + "  •  Tamanho do banco: " + Formatos.bytes(s.tamanhoDoBancoEmBytes())),
                Ui.suave("Pasta de dados: " + ui.local().diretorioDeDados()),
                Ui.pequeno("Os dados da sua exportação ficam somente neste computador, no banco cifrado. A chave é protegida pelo sistema "
                        + "(DPAPI no Windows) e fica fora do banco.")));

        VBox eventos = Ui.cartao(Ui.tituloDeCartao("Eventos de segurança recentes"));
        var lista = ui.local().dados().eventosDeSeguranca(15);
        if (lista.isEmpty()) {
            eventos.getChildren().add(Ui.suave("Nenhum evento registrado."));
        }
        lista.forEach(e -> eventos.getChildren().add(Ui.suave(Formatos.dataHora(e.ocorridoEm()) + " — " + descricao(e.tipo())
                + (e.detalhe() == null ? "" : " (" + e.detalhe() + ")"))));
        area.getChildren().add(eventos);

        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Limites da proteção"),
                Ui.pequeno("A cifragem protege o arquivo em disco, mas não protege contra programas maliciosos executando na sua sessão do Windows, "
                        + "contra administradores do computador nem em perfis compartilhados com outras pessoas."),
                Ui.pequeno("A exclusão sobrescreve os arquivos, mas em SSDs a eliminação física não pode ser garantida. A chave também é apagada, "
                        + "o que inutiliza qualquer cópia do banco.")));

        VBox perigo = Ui.cartao(Ui.tituloDeCartao("Excluir todos os dados"),
                Ui.suave("Remove o banco local, todas as importações e a chave de cifragem deste computador. A ação não pode ser desfeita."),
                Ui.perigo("Excluir todos os dados locais…", this::excluirTudo));
        perigo.setAlignment(Pos.TOP_LEFT);
        area.getChildren().add(perigo);
    }

    private static VBox indicador(String rotulo, String valor, String detalhe, boolean ok) {
        Label v = new Label(valor);
        v.getStyleClass().add(ok ? "texto-sucesso" : "texto-erro");
        return Ui.metrica(rotulo, v, detalhe);
    }

    private static String descricao(String tipo) {
        return switch (tipo) {
            case "IMPORTACAO_CONCLUIDA" -> "Importação concluída";
            case "IMPORTACAO_EXCLUIDA" -> "Importação excluída";
            case "CSV_EXPORTADO" -> "Lista exportada em CSV";
            default -> tipo;
        };
    }

    private void excluirTudo() {
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Excluir todos os dados");
        dialogo.setHeaderText("Esta ação apaga tudo e não pode ser desfeita.");
        dialogo.setContentText("Digite EXCLUIR para confirmar:");
        dialogo.initOwner(ui.janela());
        String resposta = dialogo.showAndWait().orElse("");
        if (!"EXCLUIR".equals(resposta.strip())) {
            ui.notificador().info("Nada foi excluído.");
            return;
        }
        try {
            ui.local().dados().excluirTodosOsDados();
        } catch (RuntimeException e) {
            ui.notificador().erro(Mensagens.paraUsuario(e));
            return;
        }
        Alert fim = new Alert(Alert.AlertType.INFORMATION, "Todos os dados locais foram excluídos. O aplicativo será fechado; ao abri-lo novamente, ele começará do zero.", ButtonType.OK);
        fim.setHeaderText("Dados excluídos");
        fim.initOwner(ui.janela());
        fim.showAndWait();
        Platform.exit();
    }
}
