package br.com.instadino.desktop.navegacao;

/** Telas navegáveis do aplicativo. */
public enum Destino {
    BOAS_VINDAS("Boas-vindas"),
    PAINEL("Painel"),
    IMPORTACAO("Importar"),
    RELACIONAMENTOS("Relacionamentos"),
    HISTORICO("Histórico"),
    RELATORIOS("Relatórios"),
    PREMIUM("Premium"),
    SEGURANCA("Segurança e privacidade"),
    CONFIGURACOES("Configurações");

    private final String titulo;

    Destino(String titulo) {
        this.titulo = titulo;
    }

    public String titulo() {
        return titulo;
    }
}
