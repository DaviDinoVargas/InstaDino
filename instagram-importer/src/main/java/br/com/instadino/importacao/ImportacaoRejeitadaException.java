package br.com.instadino.importacao;

/**
 * O arquivo foi recusado pelas regras de segurança ou não é uma exportação utilizável. A mensagem é acionável e
 * segura para exibição: não inclui caminhos do sistema nem conteúdo do arquivo.
 */
public final class ImportacaoRejeitadaException extends RuntimeException {

    public enum Motivo {
        ARQUIVO_INEXISTENTE("O arquivo selecionado não foi encontrado."),
        NAO_E_ZIP("O arquivo não é um ZIP válido ou está incompleto. Baixe a exportação novamente."),
        ZIP_VAZIO("O arquivo ZIP está vazio."),
        ZIP_GRANDE_DEMAIS("O arquivo é maior que o limite permitido."),
        ENTRADAS_DEMAIS("O ZIP contém entradas demais para ser processado com segurança."),
        TAMANHO_DECLARADO_EXCESSIVO("O conteúdo descompactado do ZIP excede o limite de segurança."),
        COMPRESSAO_SUSPEITA("O ZIP tem uma taxa de compressão suspeita e foi recusado por segurança."),
        CAMINHO_PERIGOSO("O ZIP contém caminhos inseguros e foi recusado por segurança."),
        ENTRADA_DUPLICADA("O ZIP contém entradas duplicadas e foi recusado por segurança."),
        ENTRADA_CRIPTOGRAFADA("O ZIP contém entradas protegidas por senha, que não são suportadas."),
        SEM_DADOS_RECONHECIDOS("Nenhum dado da exportação do Instagram foi reconhecido neste arquivo."),
        TEMPO_ESGOTADO("A importação excedeu o tempo máximo permitido."),
        CANCELADA("Importação cancelada.");

        private final String mensagem;

        Motivo(String mensagem) {
            this.mensagem = mensagem;
        }

        public String mensagem() {
            return mensagem;
        }
    }

    private static final long serialVersionUID = 1L;

    private final Motivo motivo;

    public ImportacaoRejeitadaException(Motivo motivo) {
        super(motivo.mensagem());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
