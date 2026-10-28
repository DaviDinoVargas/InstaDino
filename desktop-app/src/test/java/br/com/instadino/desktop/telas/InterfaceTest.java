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
import br.com.instadino.servicos.VisaoDeRelacionamento;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes da interface sob display virtual. As capturas ficam em target/capturas. */
class InterfaceTest {

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
        local.preferencias().definir("reduzir_animacoes", "true"); // capturas nítidas e teste da preferência de acessibilidade
        FxTeste.naInterface(() -> {
            janela = new JanelaPrincipal(local, gerenciadorSemServico(), urlsAbertas::add);
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

    private br.com.instadino.licenciamento.GerenciadorDeLicenca gerenciadorSemServico() {
        return new br.com.instadino.licenciamento.GerenciadorDeLicenca(null,
                new br.com.instadino.licenciamento.ArmazemDeConta(pasta.resolve("conta.cache"), new ProtetorSomenteDesenvolvimento()),
                new br.com.instadino.licenciamento.VerificadorDeLicenca(br.com.instadino.licenciamento.ChavesConfiaveis.vazio()),
                java.time.Clock.systemUTC(), "dispositivo-de-teste-ui", "PC");
    }

    private void importarPeloServico(ConstrutorDeExportacao c) {
        Path zip = c.escreverEm(pasta.resolve("e" + System.nanoTime() + ".zip"));
        var r = local.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        local.importacao().confirmar(r, local.sessao().perfilAtivo(), zip.getFileName().toString(), 10L);
    }

    private Node raiz() {
        return palco.getScene().getRoot();
    }

    private void ir(Destino d) {
        FxTeste.naInterface(() -> janela.contexto().roteador().ir(d));
    }

    @Test
    void primeiraExecucaoMostraBoasVindasComOPlanoFree() {
        abrir();
        assertThat(janela.contexto().roteador().atual()).isEqualTo(Destino.BOAS_VINDAS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Bem-vindo ao InstaDino"), "texto Bem-vindo ao InstaDino");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "FREE"), "texto FREE");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Continuar com o plano Free"))).isNotNull();
        FxTeste.capturar(raiz(), "01-boas-vindas");
    }

    @Test
    void todasAsTelasAbremSemErroEComEstadosVaziosCoerentes() {
        abrir();
        for (Destino d : Destino.values()) {
            ir(d);
            FxTeste.capturar(raiz(), "vazio-" + d.name().toLowerCase());
            assertThat(FxTeste.naInterface(() -> FxTeste.textos(raiz()))).as(d.name()).isNotEmpty();
        }
        ir(Destino.PAINEL);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Nenhuma exportação importada ainda"), "texto Nenhuma exportação importada ainda");
    }

    @Test
    void fluxoCompletoDeImportacaoPelaInterface() {
        abrir();
        Path zip = ConstrutorDeExportacao.exemplo().comCategoriasDiversas().escreverEm(pasta.resolve("export.zip"));
        ir(Destino.IMPORTACAO);
        ImportacaoTela tela = (ImportacaoTela) janela.contexto().roteador().tela(Destino.IMPORTACAO);
        FxTeste.capturar(raiz(), "02-importacao-selecao");

        FxTeste.naInterface(() -> tela.inspecionar(zip));
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Processar") != null, "inventário");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Inventário do arquivo"), "título do inventário"); // as células da tabela são criadas no próximo layout
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Seguidores"), "linha de seguidores na tabela do inventário"); // as células da tabela são criadas no próximo layout
        FxTeste.capturar(raiz(), "03-importacao-inventario");
        assertThat(local.historico().listar()).as("nada gravado antes da confirmação").isEmpty();

        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Processar").fire());
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Confirmar e salvar") != null, "resumo");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Nada foi gravado ainda"), "aviso de que nada foi gravado"); // as células da tabela são criadas no próximo layout
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Diagnóstico por arquivo"), "cartão de diagnóstico"); // as células da tabela são criadas no próximo layout
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "followers_1.json"), "linha do diagnóstico"); // as células da tabela são criadas no próximo layout
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Copiar diagnóstico"))).isNotNull();
        FxTeste.capturar(raiz(), "04-importacao-resumo");
        assertThat(local.historico().listar()).isEmpty();

        FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Confirmar e salvar").fire());
        FxTeste.esperar(() -> janela.contexto().roteador().atual() == Destino.PAINEL, "painel após confirmar");
        assertThat(local.historico().listar()).hasSize(1);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Relações mútuas"), "texto Relações mútuas");
        FxTeste.capturar(raiz(), "05-painel");
    }

    @Test
    void importacaoDeArquivoInvalidoMostraErroLegivelSemDetalhesInternos() {
        abrir();
        Path ruim = pasta.resolve("ruim.zip");
        FxTeste.naInterface(() -> {
            try {
                java.nio.file.Files.write(ruim, br.com.instadino.fixtures.ZipsMaliciosos.caminhoComPontosPontos());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        ir(Destino.IMPORTACAO);
        ImportacaoTela tela = (ImportacaoTela) janela.contexto().roteador().tela(Destino.IMPORTACAO);
        FxTeste.naInterface(() -> tela.inspecionar(ruim));
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Escolher outro arquivo") != null, "mensagem de erro");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "caminhos inseguros"), "texto caminhos inseguros");
        assertThat(FxTeste.naInterface(() -> String.join("|", FxTeste.textos(raiz())))).doesNotContain("Exception", "etc/instadino", pasta.toString());
        FxTeste.capturar(raiz(), "06-importacao-erro");
    }

    @Test
    void painelEListasMostramNumerosCorretos() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        ir(Destino.PAINEL);
        List<String> valores = FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream()
                .filter(n -> n.getStyleClass().contains("metrica-valor")).map(n -> ((Labeled) n).getText()).toList());
        assertThat(valores).containsExactly("4", "4", "2", "2", "2", "1,00");

        ir(Destino.RELACIONAMENTOS);
        FxTeste.esperar(() -> tabela().getItems().size() == 4, "lista de seguidores");
        FxTeste.capturar(raiz(), "07-relacionamentos");
    }

    @SuppressWarnings("unchecked")
    private TableView<br.com.instadino.servicos.ContaListada> tabela() {
        return (TableView<br.com.instadino.servicos.ContaListada>) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof TableView && n.isVisible()).findFirst().orElseThrow();
    }

    @Test
    void buscaFiltraEAbrirPerfilSoUsaEnderecosValidados() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        FxTeste.naInterface(() -> br.com.instadino.desktop.telas.Telas.abrirRelacionamentos(janela.contexto(), VisaoDeRelacionamento.SEGUIDORES, ""));
        FxTeste.esperar(() -> tabela().getItems().size() == 4, "carga inicial");
        TextField busca = FxTeste.naInterface(() -> (TextField) FxTeste.todos(raiz()).stream()
                .filter(n -> n instanceof TextField t && "Buscar por nome de usuário".equals(t.getPromptText())).findFirst().orElseThrow());
        FxTeste.naInterface(() -> busca.setText("BRU"));
        FxTeste.esperar(() -> tabela().getItems().size() == 1, "resultado da busca");
        assertThat(FxTeste.naInterface(() -> tabela().getItems().get(0).usuario())).isEqualTo("bruno");

        FxTeste.naInterface(() -> {
            janela.contexto().abrirPerfil(tabela().getItems().get(0).urlDoPerfil());
            janela.contexto().abrirPerfil("https://evil.example.com/bruno/");
        });
        assertThat(urlsAbertas).containsExactly("https://www.instagram.com/bruno/");
    }

    @Test
    void naoSeguemDeVoltaExibeALimitacaoDosDados() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        FxTeste.naInterface(() -> br.com.instadino.desktop.telas.Telas.abrirRelacionamentos(janela.contexto(), VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA, ""));
        FxTeste.esperar(() -> tabela().getItems().size() == 2, "lista de não recíprocos");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "não prova que"), "aviso de limitação dos dados");
        FxTeste.capturar(raiz(), "08-nao-seguem-de-volta");
    }

    @Test
    void filtrosAvancadosFicamBloqueadosNoFreeELiberadosNoPremium() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        ir(Destino.RELACIONAMENTOS);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Filtros avançados (Premium)"), "filtros avançados no Free");
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Aplicar filtros") != null, "botão de aplicar filtros (Free)");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Aplicar filtros").getParent().isDisabled())).isTrue();

        licenca.set(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA));
        encerrar();
        // reabre a janela com o plano Premium para reconstruir a tela de relacionamentos
        abrir();
        ir(Destino.RELACIONAMENTOS);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Aplicar filtros") != null, "botão de aplicar filtros (Premium)");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Aplicar filtros").getParent().isDisabled())).isFalse();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "PREMIUM"), "texto PREMIUM");
        FxTeste.capturar(raiz(), "09-relacionamentos-premium");
    }

    @Test
    void mudancaDePlanoAtualizaOSeloSemReiniciar() {
        abrir();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "FREE"), "texto FREE");
        licenca.set(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA));
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "PREMIUM"), "texto PREMIUM");
        licenca.set(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.EXPIRADA));
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "FREE"), "texto FREE");
    }

    @Test
    void temaClaroEAplicadoEPersistido() {
        abrir();
        int antes = FxTeste.naInterface(() -> palco.getScene().getStylesheets().size());
        local.preferencias().definir("tema", "claro");
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        assertThat(FxTeste.naInterface(() -> palco.getScene().getStylesheets().size())).isEqualTo(antes + 1);
        ir(Destino.CONFIGURACOES);
        FxTeste.capturar(raiz(), "10-configuracoes-tema-claro");
        local.preferencias().definir("tema", "escuro");
        FxTeste.naInterface(() -> janela.contexto().estadoMudou());
        assertThat(FxTeste.naInterface(() -> palco.getScene().getStylesheets().size())).isEqualTo(antes);
    }

    @Test
    void premiumInformaQueACompraAindaNaoEstaHabilitada() {
        abrir();
        ir(Destino.PREMIUM);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "A compra do plano Premium ainda não está habilitada"), "texto A compra do plano Premium ainda não está habilitada");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Em desenvolvimento"), "texto Em desenvolvimento");
        FxTeste.capturar(raiz(), "11-premium");
    }

    @Test
    void segurancaMostraCifragemAtivaEAvisaModoDeDesenvolvimento() {
        abrir();
        ir(Destino.SEGURANCA);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Ativa"), "texto Ativa");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "modo de desenvolvimento"), "texto modo de desenvolvimento");
        FxTeste.capturar(raiz(), "12-seguranca");
    }

    @Test
    void backupProtegidoEstaBloqueadoNoFreeMasARestauracaoContinuaLivre() {
        abrir();
        ir(Destino.SEGURANCA);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Criar backup…") != null, "cartão de backup");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Criar backup…").isDisabled())).isTrue();
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Restaurar backup…").isDisabled())).isFalse();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Backup protegido por senha (Premium)"), "texto Backup protegido por senha (Premium)");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Restaurar um backup seu é sempre gratuito"), "texto Restaurar um backup seu é sempre gratuito");
    }

    @Test
    void backupProtegidoFicaDisponivelNoPremium() {
        licenca.set(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA));
        abrir();
        ir(Destino.SEGURANCA);
        FxTeste.esperar(() -> FxTeste.botao(raiz(), "Criar backup…") != null, "cartão de backup");
        assertThat(FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Criar backup…").isDisabled())).isFalse();
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Guarde a senha em um lugar seguro"), "texto Guarde a senha em um lugar seguro");
        FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().filter(n -> n instanceof javafx.scene.control.ScrollPane)
                .forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0)));
        FxTeste.capturar(raiz(), "12b-backup-premium");
    }

    @Test
    void redimensionamentoMantemALayoutUtilizavel() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        ir(Destino.PAINEL);
        for (double[] tamanho : new double[][] {{1000, 680}, {1600, 1000}, {1280, 800}}) {
            FxTeste.naInterface(() -> {
                palco.setWidth(tamanho[0]);
                palco.setHeight(tamanho[1]);
            });
            FxTeste.esperar(() -> Math.abs(palco.getScene().getWidth() - tamanho[0]) < 40, "redimensionar para " + tamanho[0]);
            FxTeste.capturar(raiz(), "13-painel-" + (int) tamanho[0] + "x" + (int) tamanho[1]);
        }
    }

    @Test
    void listaMuitoGrandeSePaginaEMantemAInterfaceResponsiva() {
        abrir();
        var c = new ConstrutorDeExportacao().seguidores(ConstrutorDeExportacao.nomes(50_000, "pessoa").toArray(String[]::new)).seguindo("pessoa1");
        importarPeloServico(c);
        FxTeste.naInterface(() -> br.com.instadino.desktop.telas.Telas.abrirRelacionamentos(janela.contexto(), VisaoDeRelacionamento.SEGUIDORES, ""));
        FxTeste.esperar(() -> tabela().getItems().size() == 100, "primeira página");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Página 1 de 500"), "texto Página 1 de 500");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "50.000 contas"), "texto 50.000 contas");
        Button proxima = FxTeste.naInterface(() -> FxTeste.botao(raiz(), "Próxima ›"));
        FxTeste.naInterface(proxima::fire);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Página 2 de 500"), "segunda página");
    }

    @Test
    void historicoNoFreeAvisaDoLimiteENaoOferecaComparacao() {
        abrir();
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        importarPeloServico(ConstrutorDeExportacao.exemplo());
        ir(Destino.HISTORICO);
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "apenas a importação mais recente"), "texto apenas a importação mais recente");
        FxTeste.esperar(() -> FxTeste.temTexto(raiz(), "Comparar períodos (Premium)"), "texto Comparar períodos (Premium)");
        FxTeste.capturar(raiz(), "14-historico-free");
    }

    @Test
    void navegacaoPorTecladoEstaDisponivel() {
        abrir();
        var botoes = FxTeste.naInterface(() -> FxTeste.todos(raiz()).stream().filter(n -> n.getStyleClass().contains("botao-nav")).toList());
        assertThat(botoes).hasSize(13);
        assertThat(botoes).allMatch(Node::isFocusTraversable);
        assertThat(FxTeste.naInterface(() -> palco.getScene().getAccelerators().size())).isGreaterThanOrEqualTo(10);
    }
}
