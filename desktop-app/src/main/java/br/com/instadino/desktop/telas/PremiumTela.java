package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.contratos.pagamento.PlanoComercial;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import br.com.instadino.desktop.componentes.Ui.Tipo;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.nucleo.acesso.Plano;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Comparação Free x Premium, estado da licença e situação da compra. */
final class PremiumTela extends TelaBase {

    /** Rótulo e se o recurso já existe nesta versão do aplicativo. */
    private record Recurso(String rotulo, boolean disponivelNestaVersao) {
    }

    private static final Map<Funcionalidade, Recurso> RECURSOS = new LinkedHashMap<>();

    static {
        RECURSOS.put(Funcionalidade.IMPORTACAO_ZIP, new Recurso("Importação do ZIP oficial", true));
        RECURSOS.put(Funcionalidade.DASHBOARD_SOCIAL, new Recurso("Painel social", true));
        RECURSOS.put(Funcionalidade.RELACIONAMENTOS_BASICO, new Recurso("Seguidores, seguindo, mútuos e não recíprocos", true));
        RECURSOS.put(Funcionalidade.BUSCA_BASICA, new Recurso("Busca e ordenação", true));
        RECURSOS.put(Funcionalidade.COMPARACAO_MANUAL_LISTAS, new Recurso("Comparação manual de duas listas", true));
        RECURSOS.put(Funcionalidade.EXPORTACAO_CSV_BASICA, new Recurso("Exportação CSV básica", true));
        RECURSOS.put(Funcionalidade.EXCLUSAO_DE_DADOS, new Recurso("Exclusão de dados locais", true));
        RECURSOS.put(Funcionalidade.HISTORICO_COMPLETO, new Recurso("Histórico completo de importações", true));
        RECURSOS.put(Funcionalidade.COMPARACAO_ENTRE_PERIODOS, new Recurso("Comparação entre períodos", true));
        RECURSOS.put(Funcionalidade.FILTROS_COMPOSTOS, new Recurso("Filtros compostos", true));
        RECURSOS.put(Funcionalidade.CSV_PERSONALIZADO, new Recurso("CSV personalizado", true));
        RECURSOS.put(Funcionalidade.GRAFICOS_HISTORICOS, new Recurso("Gráficos históricos", true));
        RECURSOS.put(Funcionalidade.RELATORIO_PDF, new Recurso("Relatórios em PDF", true));
        RECURSOS.put(Funcionalidade.RELATORIOS_COMPARATIVOS, new Recurso("Relatório comparativo entre períodos", true));
        RECURSOS.put(Funcionalidade.FAVORITOS, new Recurso("Favoritos", true));
        RECURSOS.put(Funcionalidade.ETIQUETAS, new Recurso("Etiquetas", true));
        RECURSOS.put(Funcionalidade.NOTAS_PRIVADAS, new Recurso("Notas privadas", true));
        RECURSOS.put(Funcionalidade.MULTIPLOS_PERFIS, new Recurso("Múltiplos perfis locais", true));
        RECURSOS.put(Funcionalidade.BIBLIOTECA_DE_CONTEUDO, new Recurso("Biblioteca de conteúdo", false));
        RECURSOS.put(Funcionalidade.ANALISE_DE_ATIVIDADE, new Recurso("Análise de atividade", false));
        RECURSOS.put(Funcionalidade.ARQUIVO_DE_MENSAGENS, new Recurso("Arquivo de mensagens (opcional, com consentimento)", false));
        RECURSOS.put(Funcionalidade.BACKUP_PROTEGIDO, new Recurso("Backup local protegido", true));
    }

    private final VBox area = new VBox(16);

    PremiumTela(ContextoDaInterface ui) {
        super(ui, "Premium", "Compare os planos e veja a situação da sua licença.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        Plano plano = ui.local().politica().planoEfetivo();
        Label situacao = new Label(plano == Plano.PREMIUM ? "Plano atual: Premium" : "Plano atual: Free (gratuito, sem validade)");
        situacao.getStyleClass().add("titulo-cartao");
        VBox estado = Ui.cartaoDestaque(situacao,
                Ui.suave(plano == Plano.PREMIUM
                        ? "Sua licença foi validada. Os recursos Premium estão liberados."
                        : "Você usa o InstaDino gratuitamente. Seus dados locais nunca são bloqueados ao mudar de plano."));
        if (ui.licenca().servicoConfigurado()) {
            estado.getChildren().add(Ui.suave(ui.licenca().descricaoDaLicenca()));
        }
        area.getChildren().add(estado);
        VBox comercio = new VBox(12);
        area.getChildren().add(comercio);
        carregarComercio(comercio);

        TableView<Map.Entry<Funcionalidade, Recurso>> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Recurso", e -> e.getValue().rotulo(), 360));
        tabela.getColumns().add(coluna("Free", e -> e.getKey().planoMinimo() == Plano.FREE ? "Incluído" : "—", 90));
        tabela.getColumns().add(coluna("Premium", e -> "Incluído", 90));
        tabela.getColumns().add(coluna("Nesta versão", e -> e.getValue().disponivelNestaVersao() ? "Disponível" : "Em desenvolvimento", 150));
        tabela.getItems().setAll(RECURSOS.entrySet());
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setFixedCellSize(42);
        tabela.setPrefHeight(46 + RECURSOS.size() * 42);
        tabela.setAccessibleText("Comparação entre os planos Free e Premium");
        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Free e Premium"), tabela));
        area.getChildren().add(new HBox(Ui.pequeno("Os recursos “Em desenvolvimento” fazem parte do plano Premium, mas ainda não existem nesta versão do aplicativo.")));
    }

    // ---- compra e histórico comercial ----

    private void carregarComercio(VBox comercio) {
        var g = ui.licenca();
        if (!g.servicoConfigurado()) {
            comercio.getChildren().setAll(Ui.caixa(Tipo.INFO,
                    "A compra do plano Premium ainda não está habilitada. Nenhuma cobrança é feita por este aplicativo. "
                            + "O serviço comercial não está configurado neste computador, então os preços não podem ser exibidos."));
            return;
        }
        comercio.getChildren().setAll(Ui.suave("Consultando o serviço comercial…"));
        Tarefas.executar(p -> g.api().catalogo(), c -> mostrarCatalogo(comercio, c),
                e -> comercio.getChildren().setAll(Ui.caixa(Tipo.AVISO, Mensagens.paraUsuario(e))));
    }

    private void mostrarCatalogo(VBox comercio, RespostaDeCatalogo catalogo) {
        comercio.getChildren().clear();
        if (catalogo.mensagem() != null) {
            comercio.getChildren().add(Ui.caixa(catalogo.comprasHabilitadas() ? Tipo.INFO : Tipo.AVISO, catalogo.mensagem()));
        }
        if (catalogo.planos().isEmpty()) {
            comercio.getChildren().add(Ui.suave("Nenhum plano está disponível no momento."));
            return;
        }
        for (PlanoComercial plano : catalogo.planos()) {
            Label nome = new Label(plano.nome());
            nome.getStyleClass().add("titulo-cartao");
            Label preco = new Label(preco(plano));
            preco.getStyleClass().add("metrica-valor");
            VBox cartao = Ui.cartao(nome, Ui.suave(plano.descricao()), preco);
            if (catalogo.comprasHabilitadas()) {
                cartao.getChildren().add(botaoDeAssinar(comercio, plano));
            }
            comercio.getChildren().add(cartao);
        }
        if (ui.licenca().autenticado()) {
            carregarHistorico(comercio);
        }
    }

    private static String preco(PlanoComercial plano) {
        BigDecimal valor = BigDecimal.valueOf(plano.precoEmCentavos()).movePointLeft(2);
        String texto = NumberFormat.getCurrencyInstance(Formatos.PT_BR).format(valor);
        return texto + ("ANUAL".equals(plano.periodicidade()) ? " / ano" : " / mês");
    }

    private javafx.scene.Node botaoDeAssinar(VBox comercio, PlanoComercial plano) {
        if (!ui.licenca().autenticado()) {
            return Ui.primario("Entrar para assinar", () -> ui.roteador().ir(Destino.CONTA));
        }
        return Ui.primario("Assinar", () -> Tarefas.executar(p -> ui.licenca().api().criarCheckout(ui.licenca().token().orElseThrow(), plano.id()),
                checkout -> mostrarPedido(comercio, checkout), e -> ui.notificador().erro(Mensagens.paraUsuario(e))));
    }

    private void mostrarPedido(VBox comercio, RespostaDeCheckout checkout) {
        VBox cartao = Ui.cartaoDestaque(Ui.tituloDeCartao(checkout.sandbox() ? "Pedido de TESTE criado" : "Pedido criado"));
        if (checkout.sandbox()) {
            cartao.getChildren().addAll(Ui.suave("Modo de teste: nenhuma cobrança real será feita. Use os botões abaixo para simular o aviso que o provedor "
                            + "enviaria. O servidor verifica o evento exatamente como faria com um pagamento real."),
                    new HBox(8, simular(comercio, checkout, "Simular pagamento aprovado", "PAGAMENTO_APROVADO"),
                            simular(comercio, checkout, "Simular pagamento recusado", "PAGAMENTO_RECUSADO"),
                            simular(comercio, checkout, "Simular reembolso", "PAGAMENTO_REEMBOLSADO")));
        } else if (br.com.instadino.desktop.UrlsSeguras.paginaDePagamento(checkout.urlDoCheckout())) {
            cartao.getChildren().addAll(Ui.suave("Conclua o pagamento na página segura do provedor. O InstaDino não coleta dados de cartão."),
                    Ui.primario("Abrir página de pagamento", () -> ui.abrirPaginaDePagamento(checkout.urlDoCheckout())));
        } else {
            cartao.getChildren().add(Ui.caixa(Tipo.ERRO, "O serviço devolveu um endereço de pagamento inválido. Nada foi cobrado."));
        }
        comercio.getChildren().add(0, cartao);
    }

    private Button simular(VBox comercio, RespostaDeCheckout checkout, String rotulo, String tipo) {
        return Ui.secundario(rotulo, () -> Tarefas.executar(p -> {
            var g = ui.licenca();
            g.api().simularEventoDeSandbox(g.token().orElseThrow(), checkout.pedidoId(), tipo);
            return g.atualizarLicenca();
        }, r -> {
            ui.notificador().info(r.mensagem());
            ui.estadoMudou();
            aoExibir();
        }, e -> ui.notificador().erro(Mensagens.paraUsuario(e))));
    }

    private void carregarHistorico(VBox comercio) {
        Tarefas.executar(p -> ui.licenca().api().historico(ui.licenca().token().orElseThrow()), itens -> {
            VBox cartao = Ui.cartao(Ui.tituloDeCartao("Histórico comercial"));
            if (itens.isEmpty()) {
                cartao.getChildren().add(Ui.suave("Nenhum pedido ainda."));
            }
            itens.forEach(i -> cartao.getChildren().add(Ui.suave(Formatos.dataHora(i.criadoEm()) + "  •  "
                    + NumberFormat.getCurrencyInstance(Formatos.PT_BR).format(BigDecimal.valueOf(i.valorEmCentavos()).movePointLeft(2))
                    + "  •  " + i.situacao().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))));
            comercio.getChildren().add(cartao);
        }, e -> { });
    }

    private static TableColumn<Map.Entry<Funcionalidade, Recurso>, String> coluna(String titulo,
            java.util.function.Function<Map.Entry<Funcionalidade, Recurso>, String> valor, double largura) {
        TableColumn<Map.Entry<Funcionalidade, Recurso>, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
