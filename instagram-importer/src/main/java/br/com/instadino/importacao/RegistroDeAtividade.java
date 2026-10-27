package br.com.instadino.importacao;

/**
 * Um registro de atividade da exportação (curtida, comentário, item salvo, pesquisa, visualização ou interação com story).
 *
 * @param subtipo nome do arquivo de origem sem extensão (por exemplo, {@code liked_posts}); não contém dados pessoais
 * @param conta nome de usuário da conta envolvida (dono do conteúdo, perfil buscado), normalizado; nulo se não houver
 * @param texto texto livre (comentário, termo de busca); nulo nas categorias em que não se aplica
 * @param referencia endereço do Instagram associado (publicação, perfil), apenas {@code https://*.instagram.com}
 * @param instanteEmSegundos carimbo de tempo UTC em segundos, ou nulo
 */
public record RegistroDeAtividade(CategoriaDeDados categoria, String subtipo, String conta, String texto, String referencia,
                                  Long instanteEmSegundos) {

    public static final int TAMANHO_MAXIMO_DO_TEXTO = 1000;

    public RegistroDeAtividade {
        if (texto != null && texto.length() > TAMANHO_MAXIMO_DO_TEXTO) {
            texto = texto.substring(0, TAMANHO_MAXIMO_DO_TEXTO);
        }
        if (referencia != null && referencia.length() > 500) {
            referencia = null;
        }
    }
}
