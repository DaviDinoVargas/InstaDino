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
import br.com.instadino.servicos.ContaListada;
import br.com.instadino.servicos.ContextoLocal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Favoritos, etiquetas, notas e perfis locais na interface. */
class AnotacoesInterfaceTest {

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

    private void abrir() {
        local = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        local.preferencias().definir("reduzir_animacoes", "true");
        Path zip = ConstrutorDeExportacao.exemplo().escreverEm(pasta.resolve("e.zip"));
        var r = local.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        local.importacao().confirmar(r, local.sessao().perfilAtivo(), "e.zip", 10L);
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

    private void irParaRelacionamentos() {
        FxTeste.naInterface(() -> janela.contexto().roteador().ir(Destino.RELACIONAMENTOS));
        FxTeste.esperar(() -> tabela().getItems().size() == 4, "lista de seguidores");
    }

    @SuppressWarnings("unchecked")
    private TableView<ContaListada> tabela() {
        return (TableView<ContaListada>) FxTeste.todos(raiz()).stream().filter(n -> n instanceof TableView && n.isVisible()).findFirst().orElseThrow();
    }

    /** Deve ser chamado DENTRO da thread da interface (por exemplo, dentro de naInterface ou de esperar). */
    private Button estrelaNaInterface(String usuario) {
        var itens = tabela().getItems();
        int indice = -1;
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).usuario().equals(usuario)) {
                indice = i;
            }
        }
        tabela().applyCss();
        tabela().layout();
        for (Node n : FxTeste.todos(tabela())) {
            if (n instanceof javafx.scene.control.TableRow<?> linha && linha.getIndex() == indice) {
                return FxTeste.todos(linha).stream().filter(x -> x instanceof Button b && b.getStyleClass().contains("botao-estrela"))
                        .map(x -> (Button) x).findFirst().orElse(null);
            }
        }
        return null;
    }

    @Test
    void estrelaMarcaEPersisteFavoritoNoPremium() {
        licenca.set(PREMIUM);
        abrir();
        irParaRelacionamentos();
        FxTeste.esperar(() -> estrelaNaInterface("ana") != null, "estrela da linha");
        assertThat(FxTeste.naInterface(() -> estrelaNaInterface("ana").getText())).isEqualTo("☆");
        FxTeste.naInterface(() -> estrelaNaInterface("ana").fire());
        FxTeste.esperar(() -> local.anotacoes().doPerfilAtivo().containsKey("ana"), "favorito gravado");
        FxTeste.esperar(() -> { Button b = estrelaNaInterface("ana"); return b != null && "★".equals(b.getText()); }, "estrela preenchida");
        FxTeste.capturar(raiz(), "13-relacionamentos-favorito");
    }

    @Test
    void noFreeAEstrelaNaoGravaNadaELevaAoPremium() {
        abrir();
        irParaRelacionamentos();
        FxTeste.esperar(() -> estrelaNaInterface("ana") != null, "estrela da linha");
        FxTeste.naInterface(() -> estrelaNaInterface("ana").fire());
        FxTeste.esperar(() -> janela.contexto().roteador().atual() == Destino.PREMIUM, "redirecionamento ao Premium");
        assertThat(local.anotacoes().doPerfilAtivo()).isEmpty();
    }

    @Test
    void dialogoGravaFavoritoNotaEEtiquetaNova() {
        licenca.set(PREMIUM);
        abrir();
        irParaRelacionamentos();
        FxTeste.naInterface(() -> {
            AnotacoesDialogo d = new AnotacoesDialogo(janela.contexto(), "bruno");
            d.campoFavorito().setSelected(true);
            d.campoNota().setText("conheci no evento");
            d.campoNovaEtiqueta().setText("Trabalho");
            d.criarEtiquetaDoCampo();
            assertThat(d.caixasDeEtiqueta()).hasSize(1);
            assertThat(d.caixasDeEtiqueta().values().iterator().next().isSelected()).isTrue();
            assertThat(d.aplicar()).isTrue();
        });
        var a = local.anotacoes().doPerfilAtivo().get("bruno");
        assertThat(a.favorito()).isTrue();
        assertThat(a.nota()).isEqualTo("conheci no evento");
        assertThat(a.etiquetas()).extracting(e -> e.nome()).containsExactly("Trabalho");
    }

    @Test
    void dialogoNoFreeRecusaCriarERemoverContinuaPossivel() {
        licenca.set(PREMIUM);
        abrir();
        var etiqueta = local.anotacoes().criarEtiqueta("Amigos");
        local.anotacoes().favoritar("ana", true);
        local.anotacoes().aplicarEtiqueta("ana", etiqueta.id());
        licenca.set(EstadoDaLicenca.GRATUITO);
        FxTeste.naInterface(() -> {
            AnotacoesDialogo d = new AnotacoesDialogo(janela.contexto(), "ana");
            assertThat(d.campoNota().isEditable()).isFalse();
            assertThat(d.campoNovaEtiqueta().isDisabled()).isTrue();
            d.campoFavorito().setSelected(false);
            d.caixasDeEtiqueta().get(etiqueta.id()).setSelected(false);
            assertThat(d.aplicar()).isTrue();
            // tentar criar algo novo é recusado pelo serviço, mesmo que a interface fosse contornada
            AnotacoesDialogo outro = new AnotacoesDialogo(janela.contexto(), "bruno");
            outro.campoFavorito().setDisable(false);
            outro.campoFavorito().setSelected(true);
            assertThat(outro.aplicar()).isFalse();
        });
        assertThat(local.anotacoes().doPerfilAtivo()).doesNotContainKey("ana").doesNotContainKey("bruno");
    }

    @Test
    void filtrosDeFavoritasEEtiquetaFicamBloqueadosNoFreeELiberadosNoPremium() {
        abrir();
        irParaRelacionamentos();
        CheckBox favoritas = FxTeste.naInterface(() -> (CheckBox) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof CheckBox c && "Somente favoritas".equals(c.getText())).findFirst().orElseThrow());
        assertThat(FxTeste.naInterface(favoritas::isDisabled)).isTrue();
        encerrar();
        licenca.set(PREMIUM);
        abrir();
        local.anotacoes().favoritar("carla", true);
        irParaRelacionamentos();
        CheckBox liberada = FxTeste.naInterface(() -> (CheckBox) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof CheckBox c && "Somente favoritas".equals(c.getText())).findFirst().orElseThrow());
        assertThat(FxTeste.naInterface(liberada::isDisabled)).isFalse();
        FxTeste.naInterface(() -> {
            liberada.setSelected(true);
            FxTeste.botao(raiz(), "Aplicar filtros").fire();
        });
        FxTeste.esperar(() -> tabela().getItems().size() == 1 && tabela().getItems().get(0).usuario().equals("carla"), "somente a favorita");
        @SuppressWarnings("unchecked")
        ComboBox<Object> etiquetas = (ComboBox<Object>) FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof ComboBox<?> c && "Etiqueta".equals(c.getPromptText())).findFirst().orElseThrow());
        assertThat(FxTeste.naInterface(etiquetas::isDisabled)).isFalse();
    }

    @Test
    void menuDePerfisListaTrocaECriaNoPremium() {
        licenca.set(PREMIUM);
        abrir();
        MenuButton menu = FxTeste.naInterface(() -> (MenuButton) FxTeste.todos(raiz()).stream().filter(n -> n instanceof MenuButton)
                .findFirst().orElseThrow());
        assertThat(FxTeste.naInterface(menu::getText)).isEqualTo("Perfil: Perfil principal");
        long principal = local.servicoDePerfis().ativo().id();
        FxTeste.naInterface(() -> local.servicoDePerfis().criar("Segundo"));
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        assertThat(FxTeste.naInterface(menu::getText)).isEqualTo("Perfil: Segundo");
        List<String> itens = FxTeste.naInterface(() -> menu.getItems().stream().map(i -> i.getText() == null ? "—" : i.getText()).toList());
        assertThat(itens).contains("Perfil principal", "Segundo", "Novo perfil…", "Renomear este perfil…", "Excluir este perfil…");
        // escolher o primeiro item (perfil principal) troca o perfil ativo
        FxTeste.naInterface(() -> menu.getItems().get(0).fire());
        assertThat(local.servicoDePerfis().ativo().id()).isEqualTo(principal);
        assertThat(FxTeste.naInterface(menu::getText)).isEqualTo("Perfil: Perfil principal");
    }

    @Test
    void noFreeSoExisteUmPerfilEExcluirFicaDesabilitado() {
        abrir();
        MenuButton menu = FxTeste.naInterface(() -> (MenuButton) FxTeste.todos(raiz()).stream().filter(n -> n instanceof MenuButton)
                .findFirst().orElseThrow());
        boolean excluirDesabilitado = FxTeste.naInterface(() -> menu.getItems().stream()
                .filter(i -> "Excluir este perfil…".equals(i.getText())).findFirst().orElseThrow().isDisable());
        assertThat(excluirDesabilitado).isTrue();
    }
}
