package br.com.instadino.desktop.telas;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.licenciamento.GerenciadorDeLicenca;
import java.util.List;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Conta comercial (opcional): entrar, criar conta, recuperar senha e, com sessão ativa, ver plano, licença,
 * dispositivos e sessões. As funções gratuitas do aplicativo NÃO dependem desta tela.
 */
final class ContaTela extends TelaBase {

    private final VBox area = new VBox(16);

    ContaTela(ContextoDaInterface ui) {
        super(ui, "Conta", "Entrar é opcional: o plano Free funciona sem cadastro. A conta serve para ativar o plano Premium.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        GerenciadorDeLicenca g = ui.licenca();
        if (!g.servicoConfigurado()) {
            area.getChildren().add(Ui.caixa(Ui.Tipo.INFO,
                    "O serviço comercial não está configurado neste computador. O InstaDino continua funcionando normalmente com o plano Free, "
                            + "e todos os seus dados permanecem locais. Quando o serviço estiver disponível, você poderá entrar aqui."));
            return;
        }
        if (g.autenticado()) {
            mostrarConta(g);
        } else {
            area.getChildren().add(formularios(g));
        }
    }

    // ---- formulários (sem sessão) ----

    private Node formularios(GerenciadorDeLicenca g) {
        TabPane abas = new TabPane(new Tab("Entrar", painelDeLogin(g)), new Tab("Criar conta", painelDeCadastro(g)),
                new Tab("Esqueci a senha", painelDeRecuperacao(g)));
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abas.setMinHeight(430);
        return Ui.cartao(abas);
    }

    private VBox painelDeLogin(GerenciadorDeLicenca g) {
        TextField email = campo("E-mail");
        PasswordField senha = senha("Senha");
        VBox resposta = new VBox();
        Button entrar = Ui.primario("Entrar", () -> {
            String e = email.getText().strip();
            String s = senha.getText();
            senha.clear();
            if (e.isEmpty() || s.isEmpty()) {
                mostrarResposta(resposta, false, "Informe e-mail e senha.");
                return;
            }
            executar(() -> g.entrar(e, s), r -> {
                mostrarResposta(resposta, r.sucesso(), r.mensagem());
                if (r.sucesso()) {
                    ui.estadoMudou();
                    aoExibir();
                }
            });
        });
        senha.setOnAction(e -> entrar.fire());
        return aba(email, senha, entrar, resposta, Ui.pequeno("Sua senha nunca é guardada neste computador; apenas uma sessão protegida pelo sistema."));
    }

    private VBox painelDeCadastro(GerenciadorDeLicenca g) {
        TextField nome = campo("Nome");
        TextField email = campo("E-mail");
        PasswordField senha = senha("Senha (mínimo de 12 caracteres)");
        PasswordField confirmacao = senha("Repita a senha");
        TextField codigo = campo("Código recebido por e-mail");
        VBox verificacao = new VBox(10);
        VBox resposta = new VBox();
        Button criar = Ui.primario("Criar conta", () -> {
            String s = senha.getText();
            String c = confirmacao.getText();
            senha.clear();
            confirmacao.clear();
            if (!s.equals(c)) {
                mostrarResposta(resposta, false, "As senhas não conferem.");
                return;
            }
            executar(() -> {
                g.api().cadastrar(email.getText().strip(), s, nome.getText().strip());
                return new GerenciadorDeLicenca.Resultado(true, "Se o e-mail puder ser usado, enviamos um código de confirmação. Digite-o abaixo.");
            }, r -> {
                mostrarResposta(resposta, r.sucesso(), r.mensagem());
                if (r.sucesso()) {
                    verificacao.getChildren().setAll(Ui.suave("Confirme seu e-mail para poder entrar:"), codigo, Ui.primario("Confirmar e-mail", () -> {
                        String t = codigo.getText().strip();
                        executar(() -> {
                            g.api().verificarEmail(t);
                            return new GerenciadorDeLicenca.Resultado(true, "E-mail confirmado. Agora você pode entrar.");
                        }, r2 -> mostrarResposta(resposta, r2.sucesso(), r2.mensagem()));
                    }));
                }
            });
        });
        return aba(nome, email, senha, confirmacao, criar, resposta, verificacao);
    }

    private VBox painelDeRecuperacao(GerenciadorDeLicenca g) {
        TextField email = campo("E-mail da conta");
        TextField codigo = campo("Código recebido por e-mail");
        PasswordField nova = senha("Nova senha (mínimo de 12 caracteres)");
        VBox resposta = new VBox();
        Button enviar = Ui.secundario("Enviar código", () -> executar(() -> {
            g.api().solicitarRecuperacao(email.getText().strip());
            return new GerenciadorDeLicenca.Resultado(true, "Se o e-mail estiver cadastrado, enviamos um código válido por 30 minutos.");
        }, r -> mostrarResposta(resposta, r.sucesso(), r.mensagem())));
        Button redefinir = Ui.primario("Redefinir senha", () -> {
            String s = nova.getText();
            nova.clear();
            String t = codigo.getText().strip();
            executar(() -> {
                g.api().redefinirSenha(t, s);
                return new GerenciadorDeLicenca.Resultado(true, "Senha alterada. Todas as sessões foram encerradas; entre com a nova senha.");
            }, r -> mostrarResposta(resposta, r.sucesso(), r.mensagem()));
        });
        return aba(email, enviar, codigo, nova, redefinir, resposta);
    }

    // ---- conta autenticada ----

    private void mostrarConta(GerenciadorDeLicenca g) {
        var conta = g.conta().orElseThrow();
        Label nome = new Label(conta.nome());
        nome.getStyleClass().add("titulo-cartao");
        Label selo = new Label(conta.papel() == Papel.MASTER ? "MASTER" : "CLIENTE");
        selo.getStyleClass().addAll("selo", conta.papel() == Papel.MASTER ? "selo-premium" : "selo-free");
        HBox topo = new HBox(10, nome, selo);
        topo.setAlignment(Pos.CENTER_LEFT);
        VBox identificacao = Ui.cartao(topo, Ui.suave(conta.email()));

        Label licencaTexto = Ui.suave(g.descricaoDaLicenca());
        Label resposta = Ui.pequeno("");
        VBox licenca = Ui.cartao(Ui.tituloDeCartao("Plano e licença"), licencaTexto,
                new HBox(10,
                        Ui.secundario("Atualizar licença", () -> executar(g::atualizarLicenca, r -> {
                            resposta.setText(r.mensagem());
                            licencaTexto.setText(g.descricaoDaLicenca());
                            ui.estadoMudou();
                        })),
                        Ui.secundario("Sair da conta", () -> executar(g::sair, r -> {
                            ui.notificador().info(r.mensagem());
                            ui.estadoMudou();
                            aoExibir();
                        }))),
                resposta,
                Ui.pequeno("A licença é validada neste computador por assinatura digital. Sem internet, ela continua valendo durante o período de "
                        + "tolerância. Seus dados locais nunca são bloqueados."));
        VBox assinatura = Ui.cartao(Ui.tituloDeCartao("Assinatura"), Ui.suave("Carregando…"));
        VBox dispositivos = Ui.cartao(Ui.tituloDeCartao("Dispositivos autorizados"), Ui.suave("Carregando…"));
        VBox sessoes = Ui.cartao(Ui.tituloDeCartao("Sessões ativas"), Ui.suave("Carregando…"));
        area.getChildren().setAll(identificacao, licenca, assinatura, dispositivos, sessoes);

        carregar(() -> g.api().assinatura(g.token().orElseThrow()), a -> preencherAssinatura(assinatura, a));
        carregar(() -> g.api().dispositivos(g.token().orElseThrow()), d -> preencherDispositivos(dispositivos, g, d));
        carregar(() -> g.api().sessoes(g.token().orElseThrow()), s -> preencherSessoes(sessoes, g, s));
    }

    private void preencherAssinatura(VBox cartao, EstadoDaAssinatura a) {
        cartao.getChildren().setAll(Ui.tituloDeCartao("Assinatura"),
                Ui.suave("Situação: " + traduzir(a.situacao()) + (a.validaAte() == null ? "" : "  •  válida até " + Formatos.data(a.validaAte()))),
                Ui.suave("Origem do direito: " + (a.origem() == null ? "nenhuma" : traduzirOrigem(a.origem()))));
    }

    private static String traduzir(String situacao) {
        return switch (situacao) {
            case "ATIVA" -> "ativa";
            case "CANCELADA" -> "cancelada (vale até o fim do período pago)";
            case "PAGAMENTO_PENDENTE" -> "pagamento pendente";
            case "REVOGADA" -> "revogada";
            case "SUSPENSA" -> "suspensa";
            case "SEM_ASSINATURA" -> "sem assinatura";
            default -> situacao.toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static String traduzirOrigem(String origem) {
        return switch (origem) {
            case "PAPEL_MASTER" -> "conta master";
            case "CONCESSAO_ADMINISTRATIVA" -> "concessão administrativa";
            case "ASSINATURA" -> "assinatura paga";
            default -> origem;
        };
    }

    private void preencherDispositivos(VBox cartao, GerenciadorDeLicenca g, List<DispositivoAutorizado> lista) {
        cartao.getChildren().setAll(Ui.tituloDeCartao("Dispositivos autorizados"));
        if (lista.isEmpty()) {
            cartao.getChildren().add(Ui.suave("Nenhum dispositivo ativado."));
        }
        for (DispositivoAutorizado d : lista) {
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            Button remover = Ui.link("Remover", () -> executar(() -> {
                g.api().removerDispositivo(g.token().orElseThrow(), d.id());
                return new GerenciadorDeLicenca.Resultado(true, "Dispositivo removido.");
            }, r -> {
                ui.notificador().sucesso(r.mensagem());
                aoExibir();
            }));
            HBox linha = new HBox(10, Ui.suave((d.nome() == null ? "Dispositivo" : d.nome()) + "  •  ativado em " + Formatos.data(d.ativadoEm())), espaco, remover);
            linha.setAlignment(Pos.CENTER_LEFT);
            cartao.getChildren().add(linha);
        }
    }

    private void preencherSessoes(VBox cartao, GerenciadorDeLicenca g, List<SessaoAtiva> lista) {
        cartao.getChildren().setAll(Ui.tituloDeCartao("Sessões ativas"));
        for (SessaoAtiva s : lista) {
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            HBox linha = new HBox(10, Ui.suave((s.nomeDoDispositivo() == null || s.nomeDoDispositivo().isBlank() ? "Sessão" : s.nomeDoDispositivo())
                    + "  •  último uso " + Formatos.dataHora(s.ultimoUso()) + (s.atual() ? "  •  esta sessão" : "")), espaco);
            linha.setAlignment(Pos.CENTER_LEFT);
            if (!s.atual()) {
                linha.getChildren().add(Ui.link("Encerrar", () -> executar(() -> {
                    g.api().revogarSessao(g.token().orElseThrow(), s.id());
                    return new GerenciadorDeLicenca.Resultado(true, "Sessão encerrada.");
                }, r -> {
                    ui.notificador().sucesso(r.mensagem());
                    aoExibir();
                })));
            }
            cartao.getChildren().add(linha);
        }
    }

    // ---- utilidades ----

    private <T> void carregar(Supplier<T> chamada, java.util.function.Consumer<T> aoReceber) {
        Tarefas.executar(p -> chamada.get(), aoReceber::accept, erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }

    private void executar(Supplier<GerenciadorDeLicenca.Resultado> trabalho, java.util.function.Consumer<GerenciadorDeLicenca.Resultado> aoConcluir) {
        Tarefas.executar(p -> trabalho.get(), aoConcluir::accept,
                erro -> aoConcluir.accept(new GerenciadorDeLicenca.Resultado(false, Mensagens.paraUsuario(erro))));
    }

    private static void mostrarResposta(VBox destino, boolean sucesso, String mensagem) {
        destino.getChildren().setAll(Ui.caixa(sucesso ? Ui.Tipo.INFO : Ui.Tipo.ERRO, mensagem));
    }

    private static TextField campo(String prompt) {
        TextField c = new TextField();
        c.setPromptText(prompt);
        c.setAccessibleText(prompt);
        c.setMaxWidth(420);
        return c;
    }

    private static PasswordField senha(String prompt) {
        PasswordField c = new PasswordField();
        c.setPromptText(prompt);
        c.setAccessibleText(prompt);
        c.setMaxWidth(420);
        return c;
    }

    private static VBox aba(Node... nos) {
        VBox v = new VBox(12, nos);
        v.setPadding(new javafx.geometry.Insets(18, 4, 4, 4));
        return v;
    }
}
