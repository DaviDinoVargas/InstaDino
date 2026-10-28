package br.com.instadino.importacao;

import java.util.List;

/**
 * Uma publicação, reel ou story do catálogo de conteúdo. As mídias permanecem no ZIP original: o aplicativo guarda apenas
 * o caminho, o tipo e o tamanho delas.
 */
public record Publicacao(CategoriaDeDados categoria, String legenda, Long instanteEmSegundos, List<MidiaDePublicacao> midias) {

    public static final int TAMANHO_MAXIMO_DA_LEGENDA = 2200;

    public Publicacao {
        if (legenda != null && legenda.length() > TAMANHO_MAXIMO_DA_LEGENDA) {
            legenda = legenda.substring(0, TAMANHO_MAXIMO_DA_LEGENDA);
        }
        midias = List.copyOf(midias);
    }

    /** @param caminho caminho do arquivo dentro do ZIP, sem a pasta raiz da exportação */
    public record MidiaDePublicacao(String caminho, TipoDeMidia tipo, Long tamanhoEmBytes) {
    }

    public enum TipoDeMidia {
        FOTO, VIDEO, OUTRO;

        public static TipoDeMidia deCaminho(String caminho) {
            String c = caminho.toLowerCase(java.util.Locale.ROOT);
            int ponto = c.lastIndexOf('.');
            String ext = ponto < 0 ? "" : c.substring(ponto + 1);
            return switch (ext) {
                case "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp" -> FOTO;
                case "mp4", "mov", "m4v", "webm", "avi", "3gp" -> VIDEO;
                default -> OUTRO;
            };
        }
    }
}
