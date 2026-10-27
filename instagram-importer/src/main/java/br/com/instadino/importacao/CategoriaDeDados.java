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
    PUBLICACOES("Publicações", false, true),
    STORIES("Stories e arquivados", false, true),
    REELS("Reels", false, true),
    COMENTARIOS("Comentários", false, true),
    CURTIDAS("Curtidas", false, true),
    SALVOS("Conteúdo salvo", false, true),
    PESQUISAS("Pesquisas recentes", false, true),
    INTERACOES_STORIES("Interações com stories", false, true),
    VISUALIZACOES("Visualizações de posts e vídeos", false, true),
    MENSAGENS("Mensagens", true, true),
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
