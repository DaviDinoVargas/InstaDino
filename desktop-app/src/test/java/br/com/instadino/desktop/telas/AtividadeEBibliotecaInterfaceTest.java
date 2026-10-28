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
import javafx.scene.chart.BarChart;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtividadeEBibliotecaInterfaceTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
    private static final long BASE = 1_704_110_400L;

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(EstadoDaLicenca.GRATUITO);
    private final List<String> urls = new ArrayList<>();
    private ContextoLocal local;
    private JanelaPrincipal janela;
    private Stage palco;

    @BeforeAll
    static void iniciarToolkit() {
        FxTeste.iniciar();
    }

    private void abrir(boolean comDados) {
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        local.preferencias().definir("reduzir_animacoes", "true");
        if (comDados) {
            var c = new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana", "caio");
            c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", "{\"likes_media_likes\":[{\"title\":\"caio\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/p/A/\",\"timestamp\":"
                    + BASE + "}]},{\"title\":\"ana\",\"string_list_data\":[{\"timestamp\":" + (BASE + 86_400L * 40) + "}]}]}");
            c.arquivo("conta/logged_information/recent_searches/profile_searches.json",
                    "{\"searches_user\":[{\"string_map_data\":{\"Search\":{\"value\":\"novo_perfil\"},\"Time\":{\"timestamp\":" + BASE + "}}}]}");
            c.arquivo("conta/your_instagram_activity/media/posts_1.json", "[{\"media\":[{\"uri\":\"media/posts/a.jpg\",\"creation_timestamp\":" + BASE + ",\"title\":\"Praia linda\"}]},"
                    + "{\"media\":[{\"uri\":\"media/posts/b.mp4\",\"creation_timestamp\":" + (BASE + 86_400L * 40) + ",\"title\":\"Montanha\"}]}]");
            c.arquivo("conta/media/posts/a.jpg", new byte[1000]);
            Path zip = c.escreverEm(pasta.resolve("e.zip"));
            var r = local.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
            local.importacao().confirmar(r, local.sessao().perfilAtivo(), "e.zip", 1L);
        }
        FxTeste.naInterface(() -> {
            janela = new JanelaPrincipal(local, new br.com.instadino.licenciamento.GerenciadorDeLicenca(null,
                    new br.com.instadino.licenciamento.ArmazemDeConta(pasta.resolve("conta.cache"), new ProtetorSomenteDesenvolvimento()),
                    new br.com.instadino.licenciamento.VerificadorDeLicenca(br.com.instadino.licenciamento.ChavesConfiaveis.vazio()),
                    java.time.Clock.systemUTC(), "dispositivo-de-teste-ui", "PC"), urls::add);
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

    @Test
    void noFreeAtividadeEBibliotecaFicamBloqueadasComChamadaParaOPremium() {
        abrir(true);
        ir(Destino.ATIVIDADE);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Análise de atividade (Premium)"), "cartão de atividade");
        ir(Destino.BIBLIOTECA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Biblioteca de conteúdo (Premium)"), "cartão da biblioteca");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Conhecer o Premium"))).isNotNull();
    }

    @Test
    void premiumSemDadosExplicaQueEPreciso() {
        licenca.set(PREMIUM);
        abrir(false);
        ir(Destino.ATIVIDADE);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Sem dados de atividade"), "estado vazio");
        ir(Destino.BIBLIOTECA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Sem conteúdo catalogado"), "estado vazio da biblioteca");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Importar exportação"))).isNotNull();
    }

    @Test
    void premiumComDadosMostraAbasGraficoELimitacao() {
        licenca.set(PREMIUM);
        abrir(true);
        ir(Destino.ATIVIDADE);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "não permitem concluir interesse"), "aviso de limitação");
        TabPane abas = FxTeste.naInterface(() -> (TabPane) FxTeste.todos(raiz()).stream().filter(n -> n instanceof TabPane && FxTeste.visivelDeFato(n)).findFirst().orElseThrow());
        assertThat(FxTeste.naInterface(() -> abas.getTabs().stream().map(t -> t.getText()).toList()))
                .containsExactly("Resumo", "Calendário", "Ranking", "Pesquisar", "Cruzamentos");
        assertThat(FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof BarChart && FxTeste.visivelDeFato(n)))).isTrue();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Curtidas"), "texto Curtidas");
        FxTeste.capturar(raiz(), "16-atividade-resumo");
        FxTeste.naInterface(() -> abas.getSelectionModel().select(1));
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Horários e dias da semana"), "calendário");
        FxTeste.capturar(raiz(), "17-atividade-calendario");
        FxTeste.naInterface(() -> abas.getSelectionModel().select(4));
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Perfis que você pesquisou"), "cruzamentos");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Não indica interesse nem intenção"), "texto Não indica interesse nem intenção");
        FxTeste.capturar(raiz(), "18-atividade-cruzamentos");
    }

    @SuppressWarnings("unchecked")
    @Test
    void rankingMostraQuantasVezesEPesquisaFiltra() {
        licenca.set(PREMIUM);
        abrir(true);
        ir(Destino.ATIVIDADE);
        TabPane abas = FxTeste.naInterface(() -> (TabPane) FxTeste.todos(raiz()).stream().filter(n -> n instanceof TabPane && FxTeste.visivelDeFato(n)).findFirst().orElseThrow());
        FxTeste.naInterface(() -> abas.getSelectionModel().select(2));
        FxTeste.esperar(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof TableView<?> t && FxTeste.visivelDeFato(t) && t.getItems().size() == 2), "ranking com 2 contas");
        FxTeste.naInterface(() -> abas.getSelectionModel().select(3));
        FxTeste.esperar(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof TableView<?> t && FxTeste.visivelDeFato(t) && t.getItems().size() == 3), "pesquisa com todos os registros");
    }

    @Test
    void bibliotecaMostraEstatisticasECatalogoComDetalhes() {
        licenca.set(PREMIUM);
        abrir(true);
        ir(Destino.BIBLIOTECA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Catálogo"), "catálogo");
        FxTeste.esperar(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof TableView<?> t && FxTeste.visivelDeFato(t) && t.getItems().size() == 2), "2 publicações");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "não são copiados"), "texto não são copiados");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Publicações"), "texto Publicações");
        FxTeste.capturar(raiz(), "19-biblioteca");
    }
}
