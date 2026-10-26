package br.com.instadino.desktop.telas;

import br.com.instadino.importacao.ImportacaoRejeitadaException;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.persistencia.BancoException;

/** Converte exceções em textos claros para o usuário, sem detalhes internos, caminhos ou dados. */
final class Mensagens {

    private static final System.Logger LOG = System.getLogger(Mensagens.class.getName());

    private Mensagens() {
    }

    /**
     * Mensagens das operações de backup. As exceções de {@code BackupComSenha} já trazem textos seguros em português
     * (sem caminhos nem detalhes internos), então podem ser exibidas como estão.
     */
    static String paraBackup(Throwable t) {
        if (t instanceof BancoException || t instanceof IllegalArgumentException) {
            return t.getMessage();
        }
        return paraUsuario(t);
    }

    static String paraUsuario(Throwable t) {
        if (t instanceof ImportacaoRejeitadaException e) {
            return e.getMessage();
        }
        if (t instanceof AcessoNegadoException e) {
            return e.getMessage();
        }
        if (t instanceof br.com.instadino.licenciamento.ErroDaApi || t instanceof br.com.instadino.licenciamento.FalhaDeRede) {
            return t.getMessage();
        }
        if (t instanceof BancoException) {
            LOG.log(System.Logger.Level.WARNING, "Falha de banco ({0}).", t.getClass().getSimpleName());
            return "Não foi possível acessar os dados locais. Reinicie o aplicativo e tente novamente.";
        }
        if (t instanceof java.io.IOException) {
            return "Não foi possível ler ou gravar o arquivo. Verifique as permissões e o espaço em disco.";
        }
        LOG.log(System.Logger.Level.ERROR, "Erro inesperado ({0}).", t == null ? "?" : t.getClass().getName());
        return "Ocorreu um erro inesperado. Tente novamente.";
    }
}
