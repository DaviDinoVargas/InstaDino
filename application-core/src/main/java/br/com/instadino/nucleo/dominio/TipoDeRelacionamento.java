package br.com.instadino.nucleo.dominio;

/** Tipos de lista de relacionamento reconhecidos na exportação. */
public enum TipoDeRelacionamento {
    SEGUIDOR("Seguidores"),
    SEGUINDO("Seguindo"),
    SOLICITACAO_PENDENTE("Solicitações de seguimento pendentes"),
    BLOQUEADO("Perfis bloqueados"),
    RESTRITO("Perfis restritos"),
    FAVORITO("Amigos próximos"),
    /** Contas que o PRÓPRIO USUÁRIO deixou de seguir recentemente (registro do Instagram, período recente). */
    DEIXEI_DE_SEGUIR_RECENTE("Contas que você deixou de seguir recentemente"),
    SOLICITACAO_RECENTE("Solicitações de seguimento recentes"),
    SOLICITACAO_RECEBIDA("Solicitações de seguimento recebidas");

    private final String rotulo;

    TipoDeRelacionamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
