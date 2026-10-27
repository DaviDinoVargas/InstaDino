package br.com.instadino.importacao;

/** Categorias de dados reconhecidas na exportação. {@link #sensivel()} exige confirmação antes do processamento. */
public enum CategoriaDeDados {
    SEGUIDORES("Seguidores", false, true),
    SEGUINDO("Contas seguidas", false, true),
    SOLICITACOES_PENDENTES("Solicitações de seguimento pendentes", false, true),
    BLOQUEADOS("Perfis bloqueados", false, true),
    RESTRITOS("Perfis restritos", false, true),
    FAVORITOS("Amigos próximos", false, true),
    DEIXEI_DE_SEGUIR("Contas que você deixou de seguir recentemente", false, true),
    SOLICITACOES_RECENTES("Solicitações de seguimento recentes", false, true),
    SOLICITACOES_RECEBIDAS("Solicitações de seguimento recebidas", false, true),
    RELACIONAMENTOS_OUTROS("Outras listas de relacionamentos", false, false),
    PERFIL("Perfil e configurações", true, false),
    PUBLICACOES("Publicações", false, false),
    STORIES("Stories e arquivados", false, false),
    REELS("Reels", false, false),
    COMENTARIOS("Comentários", false, false),
    CURTIDAS("Curtidas", false, false),
    SALVOS("Conteúdo salvo", false, false),
    PESQUISAS("Pesquisas recentes", false, false),
    MENSAGENS("Mensagens", true, false),
    SEGURANCA("Segurança e acesso", true, false),
    ANUNCIOS_E_PREFERENCIAS("Anúncios e preferências", false, false),
    MIDIA("Fotos e vídeos", false, false),
    DESCONHECIDA("Arquivos não reconhecidos", false, false);

    private final String rotulo;
    private final boolean sensivel;
    private final boolean processadaNestaVersao;

    CategoriaDeDados(String rotulo, boolean sensivel, boolean processadaNestaVersao) {
        this.rotulo = rotulo;
        this.sensivel = sensivel;
        this.processadaNestaVersao = processadaNestaVersao;
    }

    public String rotulo() {
        return rotulo;
    }

    public boolean sensivel() {
        return sensivel;
    }

    /** Verdadeiro se a versão atual do importador extrai registros desta categoria (as demais são apenas inventariadas). */
    public boolean processadaNestaVersao() {
        return processadaNestaVersao;
    }
}
