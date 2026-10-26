package br.com.instadino.desktop.telas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.desktop.JanelaPrincipal;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.licenciamento.ArmazemDeConta;
import br.com.instadino.licenciamento.ChavesConfiaveis;
import br.com.instadino.licenciamento.GerenciadorDeLicenca;
import br.com.instadino.licenciamento.VerificadorDeLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import br.com.instadino.servicos.ContextoLocal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Conta, Premium e compra em sandbox pela interface, com um serviço comercial falso que assina licenças de verdade. */
class ContaInterfaceTest {

    @TempDir
    Path pasta;

    private ApiDeTeste api;
    private GerenciadorDeLicenca licenca;
    private ContextoLocal local;
    private JanelaPrincipal janela;
    private Stage palco;
    private final List<String> urls = new java.util.ArrayList<>();

    @BeforeAll
    static void iniciarToolkit() {
        FxTeste.iniciar();
    }

    private void abrir(boolean servicoConfigurado) {
        api = new ApiDeTeste();
        licenca = new GerenciadorDeLicenca(servicoConfigurado ? api : null,
                new ArmazemDeConta(pasta.resolve("conta.cache"), new ProtetorSomenteDesenvolvimento()),
                new VerificadorDeLicenca(ChavesConfiaveis.de("k1", api.chaves.getPublic())), Clock.systemUTC(), "dispositivo-de-teste-ui", "PC de teste");
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca);
        local.preferencias().definir("reduzir_animacoes", "true");
        FxTeste.naInterface(() -> {
            janela = new JanelaPrincipal(local, licenca, urls::add);
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

    /** Verdadeiro se o texto aparece; sonda por até 5 s porque abas e CSS só são aplicados no próximo ciclo de renderização. */
    private boolean tem(String trecho) {
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), trecho))) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private TextField campo(String prompt) {
        FxTeste.esperar(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof TextField t && prompt.equals(t.getPromptText()) && FxTeste.visivelDeFato(n)),
                "campo " + prompt);
        return FxTeste.naInterface(() -> (TextField) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof TextField t && prompt.equals(t.getPromptText()) && FxTeste.visivelDeFato(n)).findFirst().orElseThrow());
    }

    /** Botão com o rótulo dado entre os irmãos do campo (evita confundir com o botão "Entrar" da barra superior). */
    private Button botaoPerto(TextField campo, String rotulo) {
        return FxTeste.naInterface(() -> FxTeste.todos(campo.getParent()).stream()
                .filter(n -> n instanceof Button b && rotulo.equals(b.getText())).map(n -> (Button) n).findFirst()
                .orElseThrow(() -> new AssertionError("Botão '" + rotulo + "' não encontrado. Pai=" + campo.getParent().getClass().getSimpleName()
                        + " filhos=" + FxTeste.todos(campo.getParent()).stream().map(n -> n.getClass().getSimpleName()
                        + (n instanceof javafx.scene.control.Labeled l ? "(" + l.getText() + ")" : "")).toList())));
    }

    private void entrarPelaInterface(String email, String senha) {
        ir(Destino.CONTA);
        TextField e = campo("E-mail");
        PasswordField s = (PasswordField) campo("Senha");
        FxTeste.naInterface(() -> {
            e.setText(email);
            s.setText(senha);
        });
        Button entrar = botaoPerto(e, "Entrar");
        FxTeste.naInterface(() -> entrar.fire());
    }

    @Test
    void semServicoComercialATelaExplicaEOPlanoFreeContinuaIntegral() {
        abrir(false);
        ir(Destino.CONTA);
        assertThat(tem("serviço comercial não está configurado")).isTrue();
        assertThat(tem("continua funcionando normalmente com o plano Free")).isTrue();
        ir(Destino.PREMIUM);
        assertThat(tem("ainda não está habilitada")).isTrue();
        FxTeste.capturar(raiz(), "20-conta-sem-servico");
    }

    @Test
    void telaDeEntradaTemLoginCadastroERecuperacao() {
        abrir(true);
        ir(Destino.CONTA);
        assertThat(tem("Criar conta")).isTrue();
        assertThat(tem("Esqueci a senha")).isTrue();
        assertThat(FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().anyMatch(n -> n instanceof PasswordField))).isTrue();
        FxTeste.capturar(raiz(), "21-conta-entrar");
    }

    @Test
    void loginComSucessoMostraAContaELimpaOCampoDeSenha() {
        abrir(true);
        entrarPelaInterface(api.email, ApiDeTeste.SENHA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Plano e licença"), "tela da conta autenticada");
        assertThat(tem(api.email)).isTrue();
        assertThat(tem("Maria Teste")).isTrue();
        assertThat(tem("Nenhuma licença Premium neste dispositivo")).isTrue();
        assertThat(FxTeste.naInterface(() -> palco.getScene().getRoot().lookup(".botao-link") != null)).isTrue();
        assertThat(String.join("|", FxTeste.naInterface(() -> FxTeste.textos(raiz())))).doesNotContain("token-secreto-de-sessao", ApiDeTeste.SENHA);
        FxTeste.capturar(raiz(), "22-conta-autenticada");
    }

    @Test
    void loginComSenhaErradaMostraErroGenericoEMantemOPlanoFree() {
        abrir(true);
        ir(Destino.CONTA);
        TextField e = campo("E-mail");
        PasswordField s = (PasswordField) campo("Senha");
        Button entrar = botaoPerto(e, "Entrar");
        FxTeste.naInterface(() -> {
            e.setText(api.email);
            s.setText("senha-errada-123456");
            entrar.fire();
        });
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "incorretos"), "mensagem de erro");
        assertThat(FxTeste.naInterface(() -> s.getText())).as("a senha digitada é apagada do campo").isEmpty();
        assertThat(licenca.estadoAtual().planoEfetivo().name()).isEqualTo("FREE");
        assertThat(FxTeste.naInterface(() -> FxTeste.temTexto(raiz(), "FREE"))).isTrue();
        FxTeste.capturar(raiz(), "23-conta-erro-de-login");
    }

    @Test
    void contaPremiumAtivaOSeloELiberaOsRecursosPremiumNaInterface() {
        abrir(true);
        api.premium = true;
        entrarPelaInterface(api.email, ApiDeTeste.SENHA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Plano e licença"), "conta autenticada");
        assertThat(tem("Licença Premium ativa neste dispositivo")).isTrue();
        assertThat(tem("PREMIUM")).isTrue();
        assertThat(local.politica().permite(br.com.instadino.nucleo.acesso.Funcionalidade.RELATORIO_PDF)).isTrue();
        FxTeste.capturar(raiz(), "24-conta-premium");
    }

    @Test
    void sairVoltaAoFreeSemApagarDadosLocais() {
        abrir(true);
        api.premium = true;
        entrarPelaInterface(api.email, ApiDeTeste.SENHA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Plano e licença"), "conta autenticada");
        Button sair = FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Sair da conta"));
        FxTeste.naInterface(() -> sair.fire());
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Criar conta"), "tela de entrada");
        assertThat(local.politica().planoEfetivo().name()).isEqualTo("FREE");
        assertThat(api.chamadas).contains("sair");
        assertThat(java.nio.file.Files.exists(pasta.resolve("dados").resolve("instadino.db"))).isTrue();
    }

    @Test
    void catalogoMostraPrecoDoServidorEExplicaOModoDeTeste() {
        abrir(true);
        ir(Destino.PREMIUM);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Premium mensal (TESTE)"), "catálogo");
        assertThat(tem("19,90")).isTrue();
        assertThat(tem("nenhuma cobrança real")).isTrue();
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Entrar para assinar"))).isNotNull();
        FxTeste.capturar(raiz(), "25-premium-catalogo");
    }

    @Test
    void comprasDesabilitadasMostramAMensagemENaoOferecemAssinar() {
        abrir(true);
        api.comprasHabilitadas = false;
        ir(Destino.PREMIUM);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "ainda não está habilitada"), "mensagem do servidor");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Assinar"))).isNull();
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Entrar para assinar"))).isNull();
        FxTeste.capturar(raiz(), "26-premium-compras-desabilitadas");
    }

    @Test
    void compraEmSandboxPelaInterfaceAtivaOPremiumSomenteDepoisDoEvento() {
        abrir(true);
        entrarPelaInterface(api.email, ApiDeTeste.SENHA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Plano e licença"), "conta autenticada");
        ir(Destino.PREMIUM);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Assinar") != null, "botão de assinar");
        assertThat(local.politica().planoEfetivo().name()).isEqualTo("FREE");

        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Assinar").fire());
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Pedido de TESTE criado"), "pedido de teste");
        assertThat(tem("nenhuma cobrança real")).isTrue();
        assertThat(local.politica().planoEfetivo().name()).as("criar o pedido não concede nada").isEqualTo("FREE");
        FxTeste.capturar(raiz(), "27-premium-pedido-de-teste");

        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Simular pagamento aprovado").fire());
        FxTeste.esperar(() -> local.politica().planoEfetivo().name().equals("PREMIUM"), "licença Premium validada");
        assertThat(api.chamadas).contains("checkout", "simular:PAGAMENTO_APROVADO", "ativarLicenca");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "PREMIUM"), "selo Premium");
    }

    @Test
    void reembolsoPelaInterfaceDevolveOFree() {
        abrir(true);
        entrarPelaInterface(api.email, ApiDeTeste.SENHA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Plano e licença"), "conta");
        ir(Destino.PREMIUM);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Assinar") != null, "assinar");
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Assinar").fire());
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Simular pagamento aprovado") != null, "pedido");
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Simular pagamento aprovado").fire());
        FxTeste.esperar(() -> local.politica().planoEfetivo().name().equals("PREMIUM"), "premium");
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Assinar") != null || FxTeste.botao(raiz(), "Simular reembolso") != null, "tela atualizada");
        // a tela é refeita depois da atualização; cria um novo pedido para poder simular o reembolso
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Assinar").fire());
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Simular reembolso") != null, "novo pedido");
        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Simular reembolso").fire());
        FxTeste.esperar(() -> local.politica().planoEfetivo().name().equals("FREE"), "volta ao Free");
    }

    @Test
    void enderecosDePagamentoInseguroNuncaSaoAbertos() {
        abrir(true);
        FxTeste.naInterface(() -> {
            janela.contexto().abrirPaginaDePagamento("http://pagamento.exemplo.com/x");
            janela.contexto().abrirPaginaDePagamento("https://checkout.sandbox.instadino.invalid/pedido/1");
            janela.contexto().abrirPaginaDePagamento("https://127.0.0.1/x");
            janela.contexto().abrirPaginaDePagamento("javascript:alert(1)");
            janela.contexto().abrirPaginaDePagamento("https://usuario:senha@pagamento.exemplo.com/x");
            janela.contexto().abrirPaginaDePagamento("https://pagamento.exemplo.com/x");
        });
        assertThat(urls).containsExactly("https://pagamento.exemplo.com/x");
    }
}
