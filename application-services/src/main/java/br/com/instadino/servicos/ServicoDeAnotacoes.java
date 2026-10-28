package br.com.instadino.servicos;

import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Anotacao;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Etiqueta;
import java.util.List;
import java.util.Map;

/**
 * Favoritos, etiquetas e notas privadas sobre contas do perfil ativo. Criar e alterar exige Premium; <strong>ler e
 * remover</strong> nunca depende do plano, para que perder o Premium não prenda o usuário aos próprios dados.
 */
public final class ServicoDeAnotacoes {

    private final PoliticaDeAcesso politica;
    private final RepositorioDeAnotacoes repositorio;
    private final SessaoLocal sessao;

    ServicoDeAnotacoes(PoliticaDeAcesso politica, RepositorioDeAnotacoes repositorio, SessaoLocal sessao) {
        this.politica = politica;
        this.repositorio = repositorio;
        this.sessao = sessao;
    }

    public Map<String, Anotacao> doPerfilAtivo() {
        return repositorio.doPerfil(sessao.perfilAtivo());
    }

    public List<Etiqueta> etiquetas() {
        return repositorio.etiquetas(sessao.perfilAtivo());
    }

    public void favoritar(String usuario, boolean favorito) {
        if (favorito) {
            politica.exigir(Funcionalidade.FAVORITOS);
        }
        repositorio.definirFavorito(sessao.perfilAtivo(), usuario, favorito);
    }

    /** Define a nota privada; texto em branco remove a nota (permitido em qualquer plano). */
    public void definirNota(String usuario, String nota) {
        if (nota != null && !nota.isBlank()) {
            politica.exigir(Funcionalidade.NOTAS_PRIVADAS);
        }
        repositorio.definirNota(sessao.perfilAtivo(), usuario, nota);
    }

    public Etiqueta criarEtiqueta(String nome) {
        politica.exigir(Funcionalidade.ETIQUETAS);
        return repositorio.criarEtiqueta(sessao.perfilAtivo(), nome);
    }

    public void renomearEtiqueta(long etiquetaId, String novoNome) {
        politica.exigir(Funcionalidade.ETIQUETAS);
        repositorio.renomearEtiqueta(sessao.perfilAtivo(), etiquetaId, novoNome);
    }

    public void aplicarEtiqueta(String usuario, long etiquetaId) {
        politica.exigir(Funcionalidade.ETIQUETAS);
        repositorio.aplicarEtiqueta(sessao.perfilAtivo(), usuario, etiquetaId);
    }

    public void removerEtiqueta(String usuario, long etiquetaId) {
        repositorio.removerEtiqueta(sessao.perfilAtivo(), usuario, etiquetaId);
    }

    public void excluirEtiqueta(long etiquetaId) {
        repositorio.excluirEtiqueta(sessao.perfilAtivo(), etiquetaId);
    }
}
