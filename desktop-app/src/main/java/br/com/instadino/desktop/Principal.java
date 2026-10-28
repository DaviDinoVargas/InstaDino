package br.com.instadino.desktop;

import javafx.application.Application;

/** Ponto de entrada. Não estende Application para permitir a execução fora do sistema de módulos. */
public final class Principal {

    private Principal() {
    }

    public static void main(String[] args) {
        Application.launch(AplicativoInstaDino.class, args);
    }
}
