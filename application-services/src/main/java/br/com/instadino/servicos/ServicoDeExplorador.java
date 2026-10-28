package br.com.instadino.servicos;

import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Explorador de dados exportados (Premium): mostra, para uma importação, o que havia no arquivo e o que o InstaDino fez
 * com cada categoria. Só usa os totais gravados na importação; não lê conteúdo nenhum.
 */
public final class ServicoDeExplorador {

    public enum Situacao {
        LIDA("Lida"),
        LIDA_SEM_REGISTROS("Lida, sem registros"),
        SENSIVEL_NAO_LIDA("Sensível: não lida"),
        CATALOGADA("Apenas catalogada nesta versão"),
        NAO_RECONHECIDA("Não reconhecida");

        private final String rotulo;

        Situacao(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    public record LinhaDeCategoria(CategoriaDeDados categoria, int arquivos, int registros, Situacao situacao, boolean incompleta) {
    }

    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final ServicoDeHistorico historico;
    private final SessaoLocal sessao;

    ServicoDeExplorador(PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes, ServicoDeHistorico historico, SessaoLocal sessao) {
        this.politica = politica;
        this.importacoes = importacoes;
        this.historico = historico;
        this.sessao = sessao;
    }

    /** Importações do perfil ativo, da mais recente para a mais antiga. */
    public List<ResumoDaImportacao> importacoes() {
        politica.exigir(Funcionalidade.EXPLORADOR_DE_DADOS);
        return importacoes.listar(sessao.perfilAtivo());
    }

    public List<LinhaDeCategoria> categorias(String importacaoId) {
        politica.exigir(Funcionalidade.EXPLORADOR_DE_DADOS);
        var qualidade = historico.qualidade(importacaoId);
        Set<String> incompletas = qualidade.categoriasIncompletas();
        List<LinhaDeCategoria> linhas = new ArrayList<>();
        qualidade.resumo().categorias().forEach((nome, v) -> {
            Optional<CategoriaDeDados> categoria = java.util.Arrays.stream(CategoriaDeDados.values()).filter(c -> c.name().equals(nome)).findFirst();
            categoria.ifPresent(c -> linhas.add(new LinhaDeCategoria(c, v[0], v[1], situacao(c, v[1]), incompletas.contains(nome))));
        });
        linhas.sort((a, b) -> Integer.compare(b.registros(), a.registros()) != 0 ? Integer.compare(b.registros(), a.registros())
                : a.categoria().rotulo().compareTo(b.categoria().rotulo()));
        return linhas;
    }

    static Situacao situacao(CategoriaDeDados c, int registros) {
        if (c == CategoriaDeDados.DESCONHECIDA) {
            return Situacao.NAO_RECONHECIDA;
        }
        if (c.processadaNestaVersao()) {
            return registros > 0 ? Situacao.LIDA : c.sensivel() ? Situacao.SENSIVEL_NAO_LIDA : Situacao.LIDA_SEM_REGISTROS;
        }
        return c.sensivel() ? Situacao.SENSIVEL_NAO_LIDA : Situacao.CATALOGADA;
    }
}
