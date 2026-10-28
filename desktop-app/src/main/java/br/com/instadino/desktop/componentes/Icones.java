package br.com.instadino.desktop.componentes;

import java.util.Map;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

/** Ícones vetoriais simples (silhuetas preenchidas), coloridos pelo CSS. */
public final class Icones {

    private static final Map<String, String> CAMINHOS = Map.ofEntries(
            Map.entry("painel", "M3 3h8v8H3zM13 3h8v5h-8zM13 10h8v11h-8zM3 13h8v8H3z"),
            Map.entry("importar", "M12 3l6 6h-4v6h-4V9H6zM4 17h16v4H4z"),
            Map.entry("usuarios", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM9 13c-4 0-8 2-8 5v3h16v-3c0-3-4-5-8-5zM17 11a3.5 3.5 0 1 0-1-6.8 6 6 0 0 1 0 6.3c.3.3.7.5 1 .5zM18 13c2.5.8 5 2.3 5 5v3h-4v-3c0-2-.7-3.6-1.8-4.8z"),
            Map.entry("historico", "M4 20V10h4v10zM10 20V4h4v16zM16 20v-7h4v7z"),
            Map.entry("relatorio", "M6 2h9l5 5v15H6zM14 3.5V8h4.5zM9 12h8v1.5H9zM9 15h8v1.5H9zM9 18h5v1.5H9z"),
            Map.entry("biblioteca", "M3 4h18v16H3zM5 6v12h14V6zM7 16l3.5-5 2.5 3 2-2.5 3 4.5z"),
            Map.entry("mensagens", "M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"),
            Map.entry("explorador", "M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 14 15.5l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z"),
            Map.entry("atividade", "M3 13h4l2.5-7 4 13 2.5-6H21v-2h-3.8l-3.2 8.5-4-13-1.7 4.5H3z"),
            Map.entry("estrela", "M12 2l3 6.5 7 .9-5.2 4.8 1.4 7L12 17.6 5.8 21.2l1.4-7L2 9.4l7-.9z"),
            Map.entry("ajustes", "M3 5h12v2H3zM3 11h18v2H3zM3 17h8v2H3z"),
            Map.entry("escudo", "M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5z"),
            Map.entry("usuario", "M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 14c-5 0-9 2.5-9 6v2h18v-2c0-3.5-4-6-9-6z"),
            Map.entry("busca", "M10 2a8 8 0 1 0 4.9 14.3l5.4 5.4 1.7-1.7-5.4-5.4A8 8 0 0 0 10 2zm0 3a5 5 0 1 1 0 10 5 5 0 0 1 0-10z"),
            Map.entry("cadeado", "M6 10V7a6 6 0 1 1 12 0v3h2v12H4V10zm3 0h6V7a3 3 0 1 0-6 0z"));

    private Icones() {
    }

    public static Region de(String nome) {
        String caminho = CAMINHOS.get(nome);
        if (caminho == null) {
            throw new IllegalArgumentException("Ícone desconhecido: " + nome);
        }
        Region regiao = new Region();
        regiao.getStyleClass().add("icone");
        SVGPath forma = new SVGPath();
        forma.setContent(caminho);
        regiao.setShape(forma);
        regiao.setScaleShape(true);
        regiao.setFocusTraversable(false);
        regiao.setMouseTransparent(true);
        return regiao;
    }
}
