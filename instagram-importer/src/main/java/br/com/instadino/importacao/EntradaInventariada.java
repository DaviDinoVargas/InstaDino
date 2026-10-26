package br.com.instadino.importacao;

/** Uma entrada do ZIP já validada e classificada. Os tamanhos vêm do diretório central e podem não ser confiáveis. */
public record EntradaInventariada(String caminho, String nomeOriginal, CategoriaDeDados categoria, FormatoDeArquivo formato,
                                  long tamanhoDeclarado, long tamanhoComprimido) {
}
