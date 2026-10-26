package br.com.instadino.servicos;

import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;

/** Dados do painel social calculados sobre a importação mais recente. */
public record ResumoDoPainel(ResumoDaImportacao importacao, int seguidores, int seguindo, int mutuos, int naoSeguemDeVolta,
                             int voceNaoSegue, Double proporcao, boolean comparacaoDisponivel) {
}
