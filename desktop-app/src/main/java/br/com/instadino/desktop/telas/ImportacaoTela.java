package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.InventarioDoZip;
import br.com.instadino.importacao.ResultadoDaImportacao;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Fluxo de importação: selecionar, inspecionar, revisar o inventário, processar, revisar o resumo e confirmar. */
final class ImportacaoTela extends TelaBase {

    private final VBox area = new VBox(16);
    private Task<?> tarefaAtual;
    private Cancelamento cancelamento;

    ImportacaoTela(ContextoDaInterface ui) {
        super(ui, "Importar exportação", "Os dados são processados somente neste computador. Nada é enviado para a internet.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        if (tarefaAtual == null || tarefaAtual.isDone()) {
            mostrarSelecao();
        }
    }

    // ---- 1. seleção ----

    void mostrarSelecao() {
        area.getChildren().setAll(zonaDeSelecao(), instrucoes());
    }

    private VBox zonaDeSelecao() {
        Label chamada = new Label("Arraste o arquivo ZIP aqui ou escolha no computador");
        chamada.getStyleClass().add("titulo-cartao");
        VBox zona = Ui.cartao(chamada,
                Ui.suave("Use o ZIP da exportação oficial do Instagram, nos formatos JSON ou HTML."),
                Ui.primario("Selecionar arquivo ZIP…", this::escolherArquivo));
        zona.setAlignment(Pos.CENTER);
        zona.setMinHeight(200);
        zona.setOnDragOver((DragEvent e) -> {
            if (e.getDragboard().hasFiles() && e.getDragboard().getFiles().size() == 1) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        zona.setOnDragDropped(e -> {
            var arquivos = e.getDragboard().getFiles();
            boolean ok = arquivos != null && arquivos.size() == 1 && arquivos.get(0).getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
            if (ok) {
                inspecionar(arquivos.get(0).toPath());
            } else {
                ui.notificador().erro("Solte um único arquivo com extensão .zip.");
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        return zona;
    }

    private VBox instrucoes() {
        return Ui.cartao(Ui.tituloDeCartao("Como obter a exportação"),
                Ui.suave("1. No Instagram, abra Central de Contas > Suas informações e permissões > Exportar suas informações."),
                Ui.suave("2. Escolha o formato JSON (recomendado) e inclua ao menos Seguidores e seguindo."),
                Ui.suave("3. Baixe o arquivo ZIP quando o Instagram avisar que está pronto e selecione-o aqui."),
                Ui.pequeno("O InstaDino não usa seu login do Instagram e não acessa serviços privados da plataforma."));
    }

    private void escolherArquivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar exportação do Instagram");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos ZIP", "*.zip"));
        File arquivo = chooser.showOpenDialog(ui.janela());
        if (arquivo != null) {
            inspecionar(arquivo.toPath());
        }
    }

    // ---- 2. inspeção ----

    void inspecionar(Path zip) {
        cancelamento = new Cancelamento();
        ProgressBar barra = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        barra.setMaxWidth(Double.MAX_VALUE);
        Label mensagem = new Label("Verificando o arquivo…");
        Task<InventarioDoZip> tarefa = Tarefas.executar(
                p -> ui.local().importacao().inspecionar(zip, cancelamento, p::atualizar),
                inventario -> mostrarInventario(zip, inventario),
                erro -> mostrarErro(Mensagens.paraUsuario(erro)));
        barra.progressProperty().bind(tarefa.progressProperty());
        mensagem.textProperty().bind(tarefa.messageProperty());
        tarefaAtual = tarefa;
        area.getChildren().setAll(Ui.cartao(Ui.tituloDeCartao("Verificando o arquivo"), barra, mensagem,
                Ui.secundario("Cancelar", this::cancelar)));
    }

    private void cancelar() {
        if (cancelamento != null) {
            cancelamento.cancelar();
        }
        if (tarefaAtual != null) {
            tarefaAtual.cancel();
        }
        ui.notificador().info("Importação cancelada. Nenhum dado foi alterado.");
        mostrarSelecao();
    }

    // ---- 3. inventário ----

    private void mostrarInventario(Path zip, InventarioDoZip inventario) {
        TableView<Map.Entry<CategoriaDeDados, Integer>> tabela = new TableView<>();
        TableColumn<Map.Entry<CategoriaDeDados, Integer>, String> colCategoria = new TableColumn<>("Categoria");
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey().rotulo()));
        colCategoria.setPrefWidth(300);
        TableColumn<Map.Entry<CategoriaDeDados, Integer>, String> colArquivos = new TableColumn<>("Arquivos");
        colArquivos.setCellValueFactory(c -> new SimpleStringProperty(Formatos.inteiro(c.getValue().getValue())));
        colArquivos.setPrefWidth(100);
        TableColumn<Map.Entry<CategoriaDeDados, Integer>, String> colSituacao = new TableColumn<>("Situação nesta versão");
        colSituacao.setCellValueFactory(c -> new SimpleStringProperty(situacao(c.getValue().getKey())));
        colSituacao.setPrefWidth(380);
        tabela.getColumns().add(colCategoria);
        tabela.getColumns().add(colArquivos);
        tabela.getColumns().add(colSituacao);
        tabela.getItems().setAll(inventario.arquivosPorCategoria().entrySet());
        tabela.setFixedCellSize(42);
        tabela.setPrefHeight(Math.min(640, 46 + tabela.getItems().size() * 42));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setAccessibleText("Inventário das categorias encontradas no arquivo");

        VBox corpo = Ui.cartao(Ui.tituloDeCartao("Inventário do arquivo"),
                Ui.suave(Formatos.inteiro(inventario.entradas().size()) + " arquivos • " + Formatos.bytes(inventario.tamanhoDoZip())), tabela);
        for (String aviso : inventario.avisos()) {
            corpo.getChildren().add(Ui.caixa(Ui.Tipo.AVISO, aviso));
        }
        if (!inventario.categoriasSensiveisPresentes().isEmpty()) {
            corpo.getChildren().add(Ui.caixa(Ui.Tipo.INFO,
                    "Foram encontradas categorias sensíveis (perfil, segurança ou mensagens). Esta versão apenas as cataloga e não lê seu conteúdo."));
        }
        HBox acoes = new HBox(10, Ui.primario("Processar", () -> processar(zip)), Ui.secundario("Cancelar", this::mostrarSelecao));
        area.getChildren().setAll(corpo, acoes);
    }

    private static String situacao(CategoriaDeDados categoria) {
        if (categoria.processadaNestaVersao()) {
            return "Será processada";
        }
        if (categoria == CategoriaDeDados.DESCONHECIDA) {
            return "Não reconhecida: apenas listada, nunca executada";
        }
        return categoria.sensivel() ? "Sensível: apenas catalogada nesta versão" : "Apenas catalogada nesta versão";
    }

    // ---- 4. processamento ----

    private void processar(Path zip) {
        cancelamento = new Cancelamento();
        ProgressBar barra = new ProgressBar(0);
        barra.setMaxWidth(Double.MAX_VALUE);
        Label mensagem = new Label("Preparando…");
        Task<ResultadoDaImportacao> tarefa = Tarefas.executar(
                p -> ui.local().importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), cancelamento, p::atualizar),
                resultado -> mostrarResumo(zip, resultado),
                erro -> mostrarErro(Mensagens.paraUsuario(erro)));
        barra.progressProperty().bind(tarefa.progressProperty());
        mensagem.textProperty().bind(tarefa.messageProperty());
        tarefaAtual = tarefa;
        area.getChildren().setAll(Ui.cartao(Ui.tituloDeCartao("Lendo os dados"), barra, mensagem, Ui.secundario("Cancelar", this::cancelar)));
    }

    // ---- 5. resumo e confirmação ----

    private void mostrarResumo(Path zip, ResultadoDaImportacao r) {
        HBox metricas = new HBox(16,
                Ui.metrica("Registros válidos", new Label(Formatos.inteiro(r.registrosValidos())), null),
                Ui.metrica("Registros inválidos", new Label(Formatos.inteiro(r.registrosInvalidos())), "Sem nome de usuário válido"),
                Ui.metrica("Repetições ignoradas", new Label(Formatos.inteiro(r.registrosDuplicados())), null));
        metricas.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        VBox listas = Ui.cartao(Ui.tituloDeCartao("Listas encontradas"));
        r.relacionamentos().forEach((tipo, lista) ->
                listas.getChildren().add(Ui.suave(tipo.rotulo() + ": " + Formatos.inteiro(lista.size()) + " contas")));
        if (r.relacionamentos().isEmpty()) {
            listas.getChildren().add(Ui.suave("Nenhuma lista de relacionamentos foi lida."));
        }
        VBox conteudo = new VBox(16, metricas, listas);
        for (String aviso : r.avisos()) {
            conteudo.getChildren().add(Ui.caixa(Ui.Tipo.AVISO, aviso));
        }
        if (!r.diagnosticos().isEmpty()) {
            conteudo.getChildren().add(cartaoDeDiagnostico(r));
        }
        conteudo.getChildren().add(Ui.suave("Nada foi gravado ainda. Revise o resumo e confirme para salvar no banco local protegido."));

        Runnable confirmar = () -> confirmar(zip, r);
        HBox acoes = new HBox(10, Ui.primario("Confirmar e salvar", confirmar), Ui.secundario("Descartar", () -> {
            ui.notificador().info("Importação descartada. Nenhum dado foi gravado.");
            mostrarSelecao();
        }));
        area.getChildren().setAll(conteudo, acoes);
    }

    /**
     * Mostra, por arquivo de lista, quantos elementos foram lidos, aceitos e descartados. Serve para conferir se os números
     * batem com a sua conta e para investigar quando não batem. O texto copiado traz só estrutura e contagens.
     */
    private VBox cartaoDeDiagnostico(ResultadoDaImportacao r) {
        VBox linhas = new VBox(6);
        boolean suspeito = false;
        for (var d : r.diagnosticos()) {
            boolean problema = d.erro() != null || (d.elementosLidos() > 0 && d.descartados() * 10 > d.elementosLidos());
            suspeito |= problema;
            String resumo = d.erro() != null ? "não foi possível ler (" + d.erro() + ")"
                    : Formatos.inteiro(d.elementosLidos()) + " lidos • " + Formatos.inteiro(d.validos()) + " contas únicas • "
                    + Formatos.inteiro(d.descartados()) + " descartados • " + Formatos.inteiro(d.duplicados()) + " repetidos";
            Label linha = Ui.suave(d.caminho() + ": " + resumo);
            linha.setWrapText(true);
            if (problema) {
                linha.getStyleClass().add("texto-erro");
            }
            linhas.getChildren().add(linha);
        }
        String texto = r.diagnosticos().stream().map(br.com.instadino.importacao.DiagnosticoDeArquivo::comoTexto)
                .collect(java.util.stream.Collectors.joining("\n"));
        Button copiar = Ui.secundario("Copiar diagnóstico", () -> {
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString("InstaDino — diagnóstico da importação (leitor " + r.versaoDoParser() + ")\n\n"
                    + br.com.instadino.importacao.DiagnosticoDeArquivo.inventarioComoTexto(r.inventario()) + "\n" + texto);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            ui.notificador().sucesso("Diagnóstico copiado. Ele não contém nomes de usuário.");
        });
        VBox cartao = Ui.cartao(Ui.tituloDeCartao("Diagnóstico por arquivo"),
                Ui.pequeno("Confira se estes números batem com a sua conta. Se algo parecer errado, copie o diagnóstico: ele mostra a "
                        + "estrutura dos arquivos e contagens, sem nenhum nome de usuário, e permite corrigir o leitor."),
                linhas, copiar);
        if (suspeito) {
            cartao.getChildren().add(1, Ui.caixa(Ui.Tipo.AVISO,
                    "Há arquivos com muitos registros descartados ou ilegíveis. As listas podem estar incompletas: não confie nas contagens antes de investigar."));
        }
        return cartao;
    }

    private void confirmar(Path zip, ResultadoDaImportacao resultado) {
        long perfil = ui.local().sessao().perfilAtivo();
        long tamanho;
        try {
            tamanho = Files.size(zip);
        } catch (java.io.IOException e) {
            tamanho = resultado.inventario().tamanhoDoZip();
        }
        long tamanhoFinal = tamanho;
        Tarefas.executar(p -> ui.local().importacao().confirmar(resultado, perfil, zip.getFileName().toString(), tamanhoFinal),
                id -> {
                    ui.notificador().sucesso("Importação salva com sucesso.");
                    ui.estadoMudou();
                    ui.roteador().ir(Destino.PAINEL);
                },
                erro -> mostrarErro(Mensagens.paraUsuario(erro)));
    }

    private void mostrarErro(String mensagem) {
        area.getChildren().setAll(
                Ui.caixa(Ui.Tipo.ERRO, mensagem),
                new HBox(10, Ui.primario("Escolher outro arquivo", this::mostrarSelecao)));
    }
}
