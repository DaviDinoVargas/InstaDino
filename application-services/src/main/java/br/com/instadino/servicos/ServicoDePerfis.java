package br.com.instadino.servicos;

import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDePerfis;
import br.com.instadino.persistencia.repositorio.RepositorioDePerfis.Perfil;
import java.util.List;

/**
 * Perfis locais: cada um agrupa importações, favoritos, etiquetas e notas de uma conta analisada. O plano Free tem um
 * perfil; criar outros exige Premium. Selecionar, renomear e excluir perfis existentes nunca depende do plano.
 */
public final class ServicoDePerfis {

    private final PoliticaDeAcesso politica;
    private final RepositorioDePerfis perfis;
    private final SessaoLocal sessao;
    private final RegistroDeAuditoriaLocal auditoria;
    private final Runnable aoAlterarDados;

    ServicoDePerfis(PoliticaDeAcesso politica, RepositorioDePerfis perfis, SessaoLocal sessao, RegistroDeAuditoriaLocal auditoria,
                    Runnable aoAlterarDados) {
        this.politica = politica;
        this.perfis = perfis;
        this.sessao = sessao;
        this.auditoria = auditoria;
        this.aoAlterarDados = aoAlterarDados;
    }

    public List<Perfil> listar() {
        return perfis.listar();
    }

    public Perfil ativo() {
        return perfis.obter(sessao.perfilAtivo()).orElseThrow(() -> new IllegalStateException("Perfil ativo inexistente."));
    }

    public void selecionar(long id) {
        if (perfis.obter(id).isEmpty()) {
            throw new IllegalArgumentException("Perfil inexistente.");
        }
        sessao.selecionarPerfil(id);
        aoAlterarDados.run();
    }

    /** Cria um perfil (a partir do segundo, exige Premium) e o torna o perfil ativo. */
    public Perfil criar(String nome) {
        if (!perfis.listar().isEmpty()) {
            politica.exigir(Funcionalidade.MULTIPLOS_PERFIS);
        }
        Perfil novo = perfis.criar(nome, null);
        sessao.selecionarPerfil(novo.id());
        aoAlterarDados.run();
        auditoria.registrar("PERFIL_CRIADO", null);
        return novo;
    }

    public void renomear(long id, String novoNome) {
        perfis.renomear(id, novoNome);
    }

    /** Exclui o perfil e TODOS os dados dele. O último perfil não pode ser excluído (use a exclusão total de dados). */
    public void excluir(long id) {
        List<Perfil> todos = perfis.listar();
        if (todos.size() <= 1) {
            throw new IllegalStateException("O único perfil não pode ser excluído. Para apagar tudo, use \"Excluir todos os dados\".");
        }
        if (todos.stream().noneMatch(p -> p.id() == id)) {
            throw new IllegalArgumentException("Perfil inexistente.");
        }
        perfis.excluir(id);
        if (sessao.perfilAtivo() == id) {
            sessao.selecionarPerfil(todos.stream().filter(p -> p.id() != id).findFirst().orElseThrow().id());
        }
        aoAlterarDados.run();
        auditoria.registrar("PERFIL_EXCLUIDO", null);
    }
}
