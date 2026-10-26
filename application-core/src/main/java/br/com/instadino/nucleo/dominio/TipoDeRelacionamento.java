package br.com.instadino.nucleo.dominio;

/** Tipos de lista de relacionamento reconhecidos na exportação. */
public enum TipoDeRelacionamento {
    SEGUIDOR("Seguidores"),
    SEGUINDO("Seguindo"),
    SOLICITACAO_PENDENTE("Solicitações de seguimento pendentes"),
    BLOQUEADO("Perfis bloqueados"),
    RESTRITO("Perfis restritos"),
    FAVORITO("Amigos próximos");

    private final String rotulo;

    TipoDeRelacionamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
