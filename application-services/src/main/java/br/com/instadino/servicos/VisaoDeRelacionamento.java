package br.com.instadino.servicos;

/** Listas exibidas na tela de relacionamentos. */
public enum VisaoDeRelacionamento {
    SEGUIDORES("Seguidores"),
    SEGUINDO("Seguindo"),
    MUTUOS("Mútuos"),
    NAO_SEGUEM_DE_VOLTA("Não seguem de volta"),
    VOCE_NAO_SEGUE("Você não segue de volta"),
    PENDENTES("Solicitações pendentes"),
    BLOQUEADOS("Bloqueados"),
    RESTRITOS("Restritos"),
    FAVORITOS("Amigos próximos");

    private final String rotulo;

    VisaoDeRelacionamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
