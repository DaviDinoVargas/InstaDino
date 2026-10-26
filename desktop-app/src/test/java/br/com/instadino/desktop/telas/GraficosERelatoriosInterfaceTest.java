package br.com.instadino.desktop.telas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.desktop.JanelaPrincipal;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import br.com.instadino.servicos.ContextoLocal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gráfico de evolução no Histórico e relatórios em PDF na tela de Relatórios. */
class GraficosERelatoriosInterfaceTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(EstadoDaLicenca.GRATUITO);
    private final List<String> urlsAbertas = new ArrayList<>();
    private ContextoLocal local;
    private JanelaPrincipal janela;
    private Stage palco;

    @BeforeAll
    static void iniciarToolkit() {
        FxTeste.iniciar();
    }

    private void abrir(ConstrutorDeExportacao... importacoes) {
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        local.preferencias().definir("reduzir_animacoes", "true");
        for (ConstrutorDeExportacao e : importacoes) {
            Path zip = e.escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
            var r = local.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
            local.importacao().confirmar(r, local.sessao().perfilAtivo(), "e.zip", 10L);
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        FxTeste.naInterface(() -> {
            janela = new JanelaPrincipal(local, new br.com.instadino.licenciamento.GerenciadorDeLicenca(null,
                    new br.com.instadino.licenciamento.ArmazemDeConta(pasta.resolve("conta.cache"), new ProtetorSomenteDesenvolvimento()),
                    new br.com.instadino.licenciamento.VerificadorDeLicenca(br.com.instadino.licenciamento.ChavesConfiaveis.vazio()),
                    java.time.Clock.systemUTC(), "dispositivo-de-teste-ui", "PC"), urlsAbertas::add);
            palco = new Stage();
            palco.setScene(janela.criarCena());
            palco.show();
        });
    }

    @AfterEach
    void encerrar() {
        if (palco != null) {
            FxTeste.naInterface(() -> palco.close());
        }
        if (local != null) {
            local.close();
        }
    }

    private Node raiz() {
        return palco.getScene().getRoot();
    }

    private void ir(Destino d) {
        FxTeste.naInterface(() -> janela.contexto().roteador().ir(d));
    }

    private static ConstrutorDeExportacao exportacao(int seguidores, int seguindo) {
        String[] a = new String[seguidores];
        String[] b = new String[seguindo];
        for (int i = 0; i < seguidores; i++) {
            a[i] = "seg" + i;
        }
        for (int i = 0; i < seguindo; i++) {
            b[i] = "seg" + i;
        }
        return new ConstrutorDeExportacao().seguidores(a).seguindo(b);
    }

    @SuppressWarnings("unchecked")
    private LineChart<String, Number> grafico() {
        return (LineChart<String, Number>) FxTeste.todos(raiz()).stream().filter(n -> n instanceof LineChart && FxTeste.visivelDeFato(n))
                .findFirst().orElse(null);
    }

    private CheckBox caixa(String inicioDoTexto) {
        return (CheckBox) FxTeste.todos(raiz()).stream().filter(n -> n instanceof CheckBox c && c.getText().startsWith(inicioDoTexto))
                .findFirst().orElseThrow();
    }

    // ---- gráfico ----

    @Test
    void noFreeOGraficoFicaBloqueadoComChamadaParaOPremium() {
        abrir(exportacao(3, 3));
        ir(Destino.HISTORICO);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Evolução entre importações (Premium)"), "cartão do gráfico");
        assertThat(FxTeste.naInterface(() -> grafico())).isNull();
    }

    @Test
    void comUmaSoImportacaoOGraficoExplicaQueFaltamDados() {
        licenca.set(PREMIUM);
        abrir(exportacao(3, 3));
        ir(Destino.HISTORICO);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Importe ao menos duas exportações com as listas"), "mensagem");
        assertThat(FxTeste.naInterface(() -> grafico())).isNull();
    }

    @Test
    void noPremiumOGraficoMostraTresSeriesComUmPontoPorImportacao() {
        licenca.set(PREMIUM);
        abrir(exportacao(2, 2), exportacao(3, 2), exportacao(5, 3));
        ir(Destino.HISTORICO);
        FxTeste.esperar(() -> grafico() != null, "gráfico de linhas");
        var dados = FxTeste.naInterface(() -> grafico().getData().stream().map(s -> s.getName() + ":" + s.getData().stream()
                .map(p -> String.valueOf(p.getYValue())).toList()).toList());
        assertThat(dados).containsExactly("Seguidores:[2, 3, 5]", "Seguindo:[2, 2, 3]", "Mútuos:[2, 2, 3]");
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "não mostra o que aconteceu entre elas"))).isTrue();
        FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().filter(n -> n instanceof javafx.scene.control.ScrollPane)
                .forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(0.55)));
        FxTeste.capturar(raiz(), "14-historico-grafico");
    }

    // ---- relatórios em PDF ----

    @Test
    void noFreeORelatorioPdfEstaBloqueado() {
        abrir(exportacao(3, 3));
        ir(Destino.RELATORIOS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Relatório em PDF (Premium)"), "cartão do PDF");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Gerar relatório PDF…"))).isNull();
    }

    @Test
    void noPremiumOsDadosDeTerceirosENotasComecamDesmarcados() {
        licenca.set(PREMIUM);
        abrir(exportacao(3, 3));
        ir(Destino.RELATORIOS);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Gerar relatório PDF…") != null, "botão de gerar");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Gerar relatório PDF…").isDisabled())).isFalse();
        FxTeste.naInterface(() -> {
            assertThat(caixa("Indicadores").isSelected()).isTrue();
            assertThat(caixa("Qualidade da importação").isSelected()).isTrue();
            assertThat(caixa("Lista: contas que você segue").isSelected()).isFalse();
            assertThat(caixa("Lista: seguidores que você não segue").isSelected()).isFalse();
            assertThat(caixa("Lista: relações mútuas").isSelected()).isFalse();
            assertThat(caixa("Favoritas, etiquetas e notas privadas").isSelected()).isFalse();
            assertThat(caixa("Evolução entre importações").isSelected()).isFalse();
        });
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "ficam desmarcadas por padrão"))).isTrue();
        FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().filter(n -> n instanceof javafx.scene.control.ScrollPane)
                .forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(0.4)));
        FxTeste.capturar(raiz(), "15-relatorios-pdf");
    }

    @Test
    void semImportacaoOBotaoDeGerarFicaDesabilitado() {
        licenca.set(PREMIUM);
        abrir();
        ir(Destino.RELATORIOS);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Gerar relatório PDF…") != null, "botão de gerar");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Gerar relatório PDF…").isDisabled())).isTrue();
    }

    @Test
    void relatorioComparativoExigeDuasImportacoes() {
        licenca.set(PREMIUM);
        abrir(exportacao(2, 2));
        ir(Destino.RELATORIOS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Importe ao menos duas exportações para comparar períodos."), "aviso");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Gerar relatório comparativo…"))).isNull();
    }

    @Test
    void comDuasImportacoesORelatorioComparativoFicaDisponivel() {
        licenca.set(PREMIUM);
        abrir(exportacao(2, 2), exportacao(3, 2));
        ir(Destino.RELATORIOS);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Gerar relatório comparativo…") != null, "botão do comparativo");
        assertThat(FxTeste.naInterface(() -> caixa("Incluir listas de contas novas e ausentes").isSelected())).isFalse();
    }
}
