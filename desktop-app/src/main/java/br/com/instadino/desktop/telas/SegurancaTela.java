package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.persistencia.BackupComSenha;
import br.com.instadino.servicos.ServicoDeDados.SituacaoDaProtecao;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

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
                        + "o que inutiliza qualquer cópia do arquivo do banco; backups protegidos por senha continuam abrindo com a senha.")));

        area.getChildren().add(cartaoDeBackup());

        VBox perigo = Ui.cartao(Ui.tituloDeCartao("Excluir todos os dados"),
                Ui.suave("Remove o banco local, todas as importações e a chave de cifragem deste computador. A ação não pode ser desfeita."),
                Ui.perigo("Excluir todos os dados locais…", this::excluirTudo));
        perigo.setAlignment(Pos.TOP_LEFT);
        area.getChildren().add(perigo);
    }

    // ---- backup protegido por senha ----

    private VBox cartaoDeBackup() {
        boolean premium = ui.local().politica().permite(Funcionalidade.BACKUP_PROTEGIDO);
        javafx.scene.control.Button criar = Ui.secundario("Criar backup…", this::criarBackup);
        criar.setDisable(!premium);
        VBox cartao = Ui.cartao(Ui.tituloDeCartao(premium ? "Backup protegido por senha" : "Backup protegido por senha (Premium)"),
                Ui.suave("Cria um arquivo cifrado que só abre com uma senha escolhida por você, em qualquer computador. "
                        + "Use-o para guardar uma cópia fora deste computador ou para mudar de máquina."),
                Ui.pequeno("Guarde a senha em um lugar seguro: sem ela ninguém recupera os dados, nem o InstaDino. "
                        + "O backup contém suas listas de seguidores; proteja o arquivo como qualquer dado pessoal."),
                new HBox(10, criar, Ui.secundario("Restaurar backup…", this::restaurarBackup)));
        if (!premium) {
            cartao.getChildren().add(new HBox(8, Ui.pequeno("Criar backups faz parte do plano Premium. Restaurar um backup seu é sempre gratuito."),
                    Ui.link("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
        }
        return cartao;
    }

    private void criarBackup() {
        FileChooser escolha = new FileChooser();
        escolha.setTitle("Salvar backup protegido");
        escolha.setInitialFileName("InstaDino-backup-" + LocalDate.now() + ".instadinobkp");
        escolha.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup do InstaDino", "*.instadinobkp"));
        File arquivo = escolha.showSaveDialog(ui.janela());
        if (arquivo == null) {
            return;
        }
        if (arquivo.exists()) {
            ui.notificador().erro("Já existe um arquivo com esse nome. Escolha um nome novo; o InstaDino não sobrescreve backups.");
            return;
        }
        char[] senha = pedirSenha("Proteger o backup", "Escolha uma senha para este backup. Ela não pode ser recuperada.", true);
        if (senha == null) {
            return;
        }
        executarCriacao(arquivo.toPath(), senha);
    }

    void executarCriacao(Path destino, char[] senha) {
        ui.notificador().info("Criando o backup… isso pode levar alguns segundos.");
        Tarefas.executar(p -> {
            ui.local().dados().criarBackup(destino, senha);
            return destino.getFileName().toString();
        }, nome -> ui.notificador().sucesso("Backup " + nome + " criado."), erro -> ui.notificador().erro(Mensagens.paraBackup(erro)));
    }

    private void restaurarBackup() {
        Alert aviso = new Alert(Alert.AlertType.CONFIRMATION,
                "Restaurar um backup SUBSTITUI todos os dados atuais deste computador pelos do backup. Os dados atuais serão mantidos "
                        + "em um arquivo \"instadino.db.antes\" na pasta de dados, mas o aplicativo será fechado ao final.",
                ButtonType.OK, ButtonType.CANCEL);
        aviso.setHeaderText("Substituir os dados atuais?");
        aviso.initOwner(ui.janela());
        if (aviso.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        FileChooser escolha = new FileChooser();
        escolha.setTitle("Selecionar backup do InstaDino");
        escolha.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup do InstaDino", "*.instadinobkp"));
        File arquivo = escolha.showOpenDialog(ui.janela());
        if (arquivo == null) {
            return;
        }
        char[] senha = pedirSenha("Abrir o backup", "Digite a senha usada ao criar este backup.", false);
        if (senha == null) {
            return;
        }
        executarRestauracao(arquivo.toPath(), senha);
    }

    void executarRestauracao(Path arquivo, char[] senha) {
        ui.notificador().info("Verificando e restaurando o backup…");
        Tarefas.executar(p -> {
            ui.local().dados().restaurarBackup(arquivo, senha);
            return Boolean.TRUE;
        }, ok -> {
            Alert fim = new Alert(Alert.AlertType.INFORMATION,
                    "Backup restaurado. O aplicativo será fechado; ao abri-lo novamente, ele mostrará os dados do backup.", ButtonType.OK);
            fim.setHeaderText("Restauração concluída");
            fim.initOwner(ui.janela());
            fim.showAndWait();
            Platform.exit();
        }, erro -> ui.notificador().erro(Mensagens.paraBackup(erro)));
    }

    /** Diálogo de senha. Devolve a senha digitada ou {@code null} se o usuário cancelar. */
    private char[] pedirSenha(String titulo, String cabecalho, boolean confirmar) {
        Dialog<char[]> dialogo = new Dialog<>();
        dialogo.initOwner(ui.janela());
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(cabecalho);
        PasswordField primeira = new PasswordField();
        primeira.setPromptText(confirmar ? "Senha (mínimo " + BackupComSenha.TAMANHO_MINIMO_DA_SENHA + " caracteres)" : "Senha do backup");
        PasswordField segunda = new PasswordField();
        segunda.setPromptText("Repita a senha");
        VBox conteudo = new VBox(8, primeira);
        if (confirmar) {
            conteudo.getChildren().add(segunda);
        }
        dialogo.getDialogPane().setContent(conteudo);
        ButtonType continuar = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(continuar, ButtonType.CANCEL);
        Node botao = dialogo.getDialogPane().lookupButton(continuar);
        Runnable validar = () -> {
            String a = primeira.getText();
            boolean valida = confirmar ? a.length() >= BackupComSenha.TAMANHO_MINIMO_DA_SENHA && a.equals(segunda.getText()) : !a.isEmpty();
            botao.setDisable(!valida);
        };
        primeira.textProperty().addListener((o, antes, depois) -> validar.run());
        segunda.textProperty().addListener((o, antes, depois) -> validar.run());
        validar.run();
        dialogo.setResultConverter(tipo -> tipo == continuar ? primeira.getText().toCharArray() : null);
        char[] resultado = dialogo.showAndWait().orElse(null);
        primeira.clear();
        segunda.clear();
        return resultado;
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
