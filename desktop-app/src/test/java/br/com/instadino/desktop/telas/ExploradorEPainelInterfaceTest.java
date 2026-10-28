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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExploradorEPainelInterfaceTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

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

    private void abrir() {
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        local.preferencias().definir("reduzir_animacoes", "true");
        var c = new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana", "caio")
                .arquivo("conta/personal_information/personal_information.json", "{}");
        var r = local.importacao().processar(c.escreverEm(pasta.resolve("e.zip")), ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        local.importacao().confirmar(r, local.sessao().perfilAtivo(), "e.zip", 1L);
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

    @Test
    void exploradorListaCategoriasComSituacao() {
        abrir();
        ir(Destino.EXPLORADOR);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Sensível: não lida"), "situação do perfil");
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "Seguidores"))).isTrue();
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "Perfil e configurações"))).isTrue();
        FxTeste.capturar(raiz(), "21-explorador");
    }

    @Test
    void exploradorNoFreeOferecePremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        abrir();
        ir(Destino.EXPLORADOR);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Explorador de dados (Premium)"), "cartão Premium");
    }

    @Test
    void painelPersonalizavelOcultaBlocosEvoltaAoCompletoNoFree() {
        abrir();
        ir(Destino.PAINEL);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Composição dos relacionamentos"), "painel completo");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Personalizar painel"))).isNotNull();
        local.paineis().definirOcultos(Set.of("composicao", "importacao", "atalhos"));
        ir(Destino.CONFIGURACOES);
        ir(Destino.PAINEL);
        FxTeste.esperar(() -> !FxTeste.temTexto(raiz(), "Composição dos relacionamentos"), "bloco oculto");
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "Última importação"))).isFalse();
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "Relações mútuas"))).isTrue();
        FxTeste.capturar(raiz(), "22-painel-personalizado");
        licenca.set(EstadoDaLicenca.GRATUITO);
        ir(Destino.CONFIGURACOES);
        ir(Destino.PAINEL);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Composição dos relacionamentos"), "painel volta ao completo sem Premium");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Personalizar painel (Premium)"))).isNotNull();
    }
}
