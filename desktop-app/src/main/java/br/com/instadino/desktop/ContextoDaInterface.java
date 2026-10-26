package br.com.instadino.desktop;

import br.com.instadino.desktop.componentes.Notificador;
import br.com.instadino.desktop.navegacao.Roteador;
import br.com.instadino.servicos.ContextoLocal;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.stage.Window;

/** Dependências compartilhadas pelas telas: serviços locais, navegação, notificações e ações da janela. */
public final class ContextoDaInterface {

    private final ContextoLocal local;
    private final Notificador notificador;
    private final Supplier<Window> janela;
    private final Consumer<String> navegador;
    private Roteador roteador;
    private Runnable aoMudarEstado = () -> { };
    private Consumer<String> buscaGlobal = texto -> { };

    public ContextoDaInterface(ContextoLocal local, Notificador notificador, Supplier<Window> janela, Consumer<String> navegador) {
        this.local = local;
        this.notificador = notificador;
        this.janela = janela;
        this.navegador = navegador;
    }

    public ContextoLocal local() {
        return local;
    }

    public Notificador notificador() {
        return notificador;
    }

    public Window janela() {
        return janela.get();
    }

    public Roteador roteador() {
        return roteador;
    }

    void definirRoteador(Roteador roteador) {
        this.roteador = roteador;
    }

    /** Abre um perfil do Instagram no navegador, somente se o endereço tiver o formato esperado. */
    public void abrirPerfil(String url) {
        if (UrlsSeguras.perfilDoInstagram(url)) {
            navegador.accept(url);
        } else {
            notificador.erro("Não foi possível abrir este endereço.");
        }
    }

    public boolean reduzirAnimacoes() {
        return local.preferencias().obter("reduzir_animacoes").map(Boolean::parseBoolean).orElse(false);
    }

    /** Chamado pelas telas quando dados, plano ou configurações mudam, para atualizar o cabeçalho. */
    public void estadoMudou() {
        aoMudarEstado.run();
    }

    void aoMudarEstado(Runnable acao) {
        this.aoMudarEstado = acao;
    }

    public void buscarNaListaDeContas(String texto) {
        buscaGlobal.accept(texto);
    }

    void aoBuscar(Consumer<String> acao) {
        this.buscaGlobal = acao;
    }
}
