package br.com.instadino.desktop.navegacao;

import javafx.scene.Node;

/** Uma tela do aplicativo. {@link #aoExibir()} é chamado sempre que ela se torna visível, para atualizar os dados. */
public interface Tela {

    Node conteudo();

    default void aoExibir() {
    }
}
