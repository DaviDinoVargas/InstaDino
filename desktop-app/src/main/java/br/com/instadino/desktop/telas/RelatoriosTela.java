package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import br.com.instadino.servicos.ServicoDeRelatorios;
import br.com.instadino.servicos.VisaoDeRelacionamento;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Exportação de listas em CSV (Free) e relatórios em PDF (Premium). */
final class RelatoriosTela extends TelaBase {

    private final VBox lista = new VBox(8);
    private final VBox pdf = new VBox(12);

    RelatoriosTela(ContextoDaInterface ui) {
        super(ui, "Relatórios", "Exporte suas listas em CSV e gere relatórios em PDF.");
        VBox cartao = Ui.cartao(Ui.tituloDeCartao("Listas disponíveis"), lista);
        corpo(cartao,
                Ui.cartao(Ui.tituloDeCartao("Exportar tudo"),
                        Ui.suave("Grava todas as listas da última importação em arquivos CSV dentro de uma pasta que você escolher."),
                        Ui.secundario("Exportar todas as listas…", this::exportarTudo)),
                pdf,
                Ui.caixa(Ui.Tipo.INFO, "Os arquivos CSV usam ponto e vírgula, codificação UTF-8 e protegem contra fórmulas de planilha. "
                        + "Nenhum relatório inclui contas de terceiros, etiquetas ou notas a menos que você marque essa opção."));
    }

    // ---- relatórios em PDF (Premium) ----

    private void construirSecaoPdf(boolean temDados) {
        pdf.getChildren().clear();
        boolean liberado = ui.local().politica().permite(Funcionalidade.RELATORIO_PDF);
        VBox cartao = Ui.cartao(Ui.tituloDeCartao(liberado ? "Relatório em PDF" : "Relatório em PDF (Premium)"));
        if (!liberado) {
            cartao.getChildren().addAll(Ui.suave("Gere um PDF com indicadores, qualidade da importação, evolução entre importações e relatórios comparativos."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM)));
            pdf.getChildren().add(cartao);
            return;
        }
        CheckBox indicadores = new CheckBox("Indicadores (seguidores, seguidos, mútuos, proporção)");
        indicadores.setSelected(true);
        CheckBox qualidade = new CheckBox("Qualidade da importação (registros, categorias, avisos)");
        qualidade.setSelected(true);
        CheckBox evolucao = new CheckBox("Evolução entre importações (gráfico)");
        evolucao.setDisable(!ui.local().politica().permite(Funcionalidade.GRAFICOS_HISTORICOS));
        boolean atividadeLiberada = ui.local().politica().permite(Funcionalidade.ANALISE_DE_ATIVIDADE);
        boolean conteudoLiberado = ui.local().politica().permite(Funcionalidade.BIBLIOTECA_DE_CONTEUDO);
        CheckBox atividade = new CheckBox("Resumo da atividade registrada (números por categoria e por mês)");
        atividade.setDisable(!atividadeLiberada);
        CheckBox conteudo = new CheckBox("Estatísticas de conteúdo (publicações, reels, stories, mídias)");
        conteudo.setDisable(!conteudoLiberado);
        Label aviso = Ui.pequeno("As opções abaixo incluem dados de terceiros ou pessoais e ficam desmarcadas por padrão.");
        CheckBox ranking = new CheckBox("Ranking de contas na atividade (nomes de terceiros)");
        ranking.setDisable(!atividadeLiberada);
        CheckBox naoSeguem = new CheckBox("Lista: contas que você segue e que não seguem de volta");
        CheckBox voceNaoSegue = new CheckBox("Lista: seguidores que você não segue");
        CheckBox mutuos = new CheckBox("Lista: relações mútuas");
        CheckBox anotacoes = new CheckBox("Favoritas, etiquetas e notas privadas");
        Button gerar = Ui.primario("Gerar relatório PDF…", () -> gerarRelatorio(new ServicoDeRelatorios.Opcoes(indicadores.isSelected(),
                qualidade.isSelected(), evolucao.isSelected(), naoSeguem.isSelected(), mutuos.isSelected(), voceNaoSegue.isSelected(),
                anotacoes.isSelected(), atividade.isSelected(), conteudo.isSelected(), ranking.isSelected())));
        gerar.setDisable(!temDados);
        cartao.getChildren().addAll(indicadores, qualidade, evolucao, atividade, conteudo, aviso, naoSeguem, voceNaoSegue, mutuos, ranking, anotacoes, gerar);
        pdf.getChildren().add(cartao);
        pdf.getChildren().add(secaoComparativa());
    }

    private VBox secaoComparativa() {
        boolean liberado = ui.local().politica().permite(Funcionalidade.RELATORIOS_COMPARATIVOS)
                && ui.local().politica().permite(Funcionalidade.COMPARACAO_ENTRE_PERIODOS);
        VBox cartao = Ui.cartao(Ui.tituloDeCartao(liberado ? "Relatório comparativo entre períodos" : "Relatório comparativo (Premium)"));
        List<ResumoDaImportacao> importacoes = ui.local().historico().listar();
        if (!liberado || importacoes.size() < 2) {
            cartao.getChildren().add(Ui.suave(liberado
                    ? "Importe ao menos duas exportações para comparar períodos."
                    : "Compare duas exportações e leve as diferenças observadas em um PDF."));
            return cartao;
        }
        ComboBox<ResumoDaImportacao> anterior = seletor(importacoes, 1);
        ComboBox<ResumoDaImportacao> recente = seletor(importacoes, 0);
        CheckBox comListas = new CheckBox("Incluir listas de contas novas e ausentes");
        Button gerar = Ui.primario("Gerar relatório comparativo…", () -> gerarComparativo(anterior.getValue(), recente.getValue(), comListas.isSelected()));
        HBox linha = new HBox(10, new Label("Anterior:"), anterior, new Label("Mais recente:"), recente);
        linha.setAlignment(Pos.CENTER_LEFT);
        cartao.getChildren().addAll(linha, comListas, gerar);
        return cartao;
    }

    private static ComboBox<ResumoDaImportacao> seletor(List<ResumoDaImportacao> lista, int indice) {
        ComboBox<ResumoDaImportacao> c = new ComboBox<>();
        c.getItems().setAll(lista);
        c.setValue(lista.get(Math.min(indice, lista.size() - 1)));
        c.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ResumoDaImportacao r) {
                return r == null ? "" : Formatos.dataHora(r.importadaEm());
            }

            @Override
            public ResumoDaImportacao fromString(String s) {
                return null;
            }
        });
        return c;
    }

    private void gerarRelatorio(ServicoDeRelatorios.Opcoes opcoes) {
        File destino = escolherDestinoPdf("instadino-relatorio.pdf");
        if (destino == null) {
            return;
        }
        ui.notificador().info("Gerando o relatório…");
        Tarefas.executar(p -> {
            ByteArrayOutputStream memoria = new ByteArrayOutputStream();
            ui.local().relatorios().gerarRelatorioDaImportacao(memoria, opcoes);
            Files.write(destino.toPath(), memoria.toByteArray());   // só grava no disco depois de o relatório estar completo
            return destino.getName();
        }, nome -> ui.notificador().sucesso("Relatório " + nome + " salvo."), erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }

    private void gerarComparativo(ResumoDaImportacao anterior, ResumoDaImportacao recente, boolean comListas) {
        if (anterior == null || recente == null || anterior.id().equals(recente.id())) {
            ui.notificador().info("Escolha duas importações diferentes.");
            return;
        }
        File destino = escolherDestinoPdf("instadino-comparativo.pdf");
        if (destino == null) {
            return;
        }
        ui.notificador().info("Gerando o relatório comparativo…");
        Tarefas.executar(p -> {
            ByteArrayOutputStream memoria = new ByteArrayOutputStream();
            ui.local().relatorios().gerarRelatorioComparativo(memoria, anterior.id(), recente.id(), comListas);
            Files.write(destino.toPath(), memoria.toByteArray());
            return destino.getName();
        }, nome -> ui.notificador().sucesso("Relatório " + nome + " salvo."), erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }

    private File escolherDestinoPdf(String nomeInicial) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar relatório em PDF");
        chooser.setInitialFileName(nomeInicial);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos PDF", "*.pdf"));
        return chooser.showSaveDialog(ui.janela());
    }

    @Override
    public void aoExibir() {
        lista.getChildren().clear();
        boolean temDados = ui.local().relacionamentos().painel().isPresent();
        construirSecaoPdf(temDados);
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
