package br.com.instadino.desktop.telas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.desktop.JanelaPrincipal;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import br.com.instadino.servicos.ContextoLocal;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MensagensInterfaceTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
    private static final long JAN = 1_704_110_400_000L;

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal local;
    private JanelaPrincipal janela;
    private Stage palco;

    @BeforeAll
    static void iniciarToolkit() {
        FxTeste.iniciar();
    }

    private static String m(String quem, long ms) {
        return "{\"sender_name\":\"" + quem + "\",\"timestamp_ms\":" + ms + ",\"content\":\"texto-que-nunca-deve-aparecer\"}";
    }

    private static String conversa(String titulo, String outro, String... ms) {
        return "{\"participants\":[{\"name\":\"Eu\"},{\"name\":\"" + outro + "\"}],\"messages\":[" + String.join(",", ms) + "],\"title\":\"" + titulo + "\"}";
    }

    private Path zipComMensagens() {
        return new ConstrutorDeExportacao().seguidores("ana").seguindo("ana")
                .arquivo("conta/messages/inbox/ana_1/message_1.json", conversa("Ana Souza", "Ana", m("Eu", JAN), m("Ana", JAN + 1000), m("Ana", JAN + 40L * 86_400_000L)))
                .arquivo("conta/messages/inbox/caio_2/message_1.json", conversa("Caio", "Caio", m("Caio", JAN)))
                .escreverEm(pasta.resolve("e.zip"));
    }

    private void abrir() {
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        local.preferencias().definir("reduzir_animacoes", "true");
        FxTeste.naInterface(() -> {
            janela = new JanelaPrincipal(local, new br.com.instadino.licenciamento.GerenciadorDeLicenca(null,
                    new br.com.instadino.licenciamento.ArmazemDeConta(pasta.resolve("conta.cache"), new ProtetorSomenteDesenvolvimento()),
                    new br.com.instadino.licenciamento.VerificadorDeLicenca(br.com.instadino.licenciamento.ChavesConfiaveis.vazio()),
                    java.time.Clock.systemUTC(), "dispositivo-de-teste-ui", "PC"), u -> { });
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

    private CheckBox opcaoDeMensagens() {
        return FxTeste.naInterface(() -> (CheckBox) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof CheckBox c && "incluir-mensagens".equals(c.getId()) && FxTeste.visivelDeFato(n)).findFirst().orElse(null));
    }

    private ImportacaoTela inspecionar() {
        ImportacaoTela tela = (ImportacaoTela) janela.contexto().roteador().tela(Destino.IMPORTACAO);
        ir(Destino.IMPORTACAO);
        FxTeste.naInterface(() -> tela.inspecionar(zipComMensagens()));
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Processar") != null, "inventário");
        return tela;
    }

    @Test
    void mensagensComecamDesligadasEPedemConsentimentoAntesDeQualquerLeitura() {
        abrir();
        ImportacaoTela tela = inspecionar();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Mensagens (opcional)"), "texto Mensagens (opcional)");
        CheckBox opcao = opcaoDeMensagens();
        assertThat(opcao).isNotNull();
        assertThat(FxTeste.naInterface(opcao::isSelected)).isFalse();
        assertThat(local.mensagens().consentimentoConcedido()).isFalse();

        AtomicBoolean perguntou = new AtomicBoolean();
        tela.confirmadorDeConsentimento = () -> {
            perguntou.set(true);
            return true;
        };
        FxTeste.naInterface(() -> opcao.setSelected(true));
        FxTeste.capturar(raiz(), "19-mensagens-opcao-na-importacao");
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Processar").fire());
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Confirmar e salvar") != null, "resumo");
        assertThat(perguntou).isTrue();
        assertThat(local.mensagens().consentimentoConcedido()).isTrue();
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Confirmar e salvar").fire());
        FxTeste.esperar(() -> local.mensagens().temDados(), "dados de mensagens gravados");

        ir(Destino.MENSAGENS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Conversas mais movimentadas"), "tela de mensagens");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Ana Souza"), "texto Ana Souza");
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "texto-que-nunca-deve-aparecer"))).isFalse();
        assertThat(FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof BarChart && FxTeste.visivelDeFato(n)))).isTrue();
        FxTeste.capturar(raiz(), "20-mensagens-estatisticas");
    }

    @Test
    void recusarOConsentimentoImpedeALeituraDasMensagens() {
        abrir();
        ImportacaoTela tela = inspecionar();
        tela.confirmadorDeConsentimento = () -> false;
        CheckBox opcao = opcaoDeMensagens();
        FxTeste.naInterface(() -> opcao.setSelected(true));
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Processar").fire());
        FxTeste.esperar(() -> !opcao.isSelected(), "opção desmarcada após a recusa");
        assertThat(local.mensagens().consentimentoConcedido()).isFalse();
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Confirmar e salvar"))).isNull();
    }

    @Test
    void noFreeAOpcaoNaoExisteEATelaOferecePremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        abrir();
        inspecionar();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "fazem parte do plano Premium"), "texto fazem parte do plano Premium");
        assertThat(opcaoDeMensagens()).isNull();
        ir(Destino.MENSAGENS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Estatísticas de mensagens (Premium)"), "cartão Premium");
    }

    @Test
    void revogarPelaTelaApagaOsDadosESolicitaNovaImportacao() {
        abrir();
        local.mensagens().conceder();
        var r = local.importacao().processar(zipComMensagens(),
                new br.com.instadino.importacao.ConfiguracaoDeImportacao(java.util.Set.of(br.com.instadino.importacao.CategoriaDeDados.MENSAGENS)),
                new br.com.instadino.importacao.Cancelamento(), br.com.instadino.importacao.OuvinteDeProgresso.NENHUM);
        local.importacao().confirmar(r, local.sessao().perfilAtivo(), "e.zip", 1L);
        assertThat(local.mensagens().temDados()).isTrue();
        local.mensagens().revogarEApagar();
        ir(Destino.MENSAGENS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Módulo de mensagens desligado"), "estado desligado");
    }
}
