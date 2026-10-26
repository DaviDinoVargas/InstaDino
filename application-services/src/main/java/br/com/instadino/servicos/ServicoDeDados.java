package br.com.instadino.servicos;

import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.repositorio.RepositorioDeEventosDeSeguranca;
import br.com.instadino.persistencia.repositorio.RepositorioDeEventosDeSeguranca.Evento;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDePerfis;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.nio.file.Files;
import java.util.List;

/** Informações sobre os dados armazenados, estado da proteção local e exclusão. */
public final class ServicoDeDados {

    /** Situação exibida na tela de segurança e privacidade. */
    public record SituacaoDaProtecao(String mecanismoDeChave, boolean seguroParaProducao, boolean cifragemAtiva,
                                     boolean integridadeOk, long tamanhoDoBancoEmBytes, int importacoes, int perfis) {
    }

    private final PoliticaDeAcesso politica;
    private final ContextoLocal contexto;
    private final RepositorioDeImportacoes importacoes;
    private final RepositorioDePerfis perfis;
    private final RepositorioDeEventosDeSeguranca eventos;
    private final RegistroDeAuditoriaLocal auditoria;

    ServicoDeDados(PoliticaDeAcesso politica, ContextoLocal contexto, RepositorioDeImportacoes importacoes,
                   RepositorioDePerfis perfis, RepositorioDeEventosDeSeguranca eventos, RegistroDeAuditoriaLocal auditoria) {
        this.auditoria = auditoria;
        this.politica = politica;
        this.contexto = contexto;
        this.importacoes = importacoes;
        this.perfis = perfis;
        this.eventos = eventos;
    }

    public SituacaoDaProtecao situacao() {
        BancoLocal banco = contexto.banco();
        boolean cifrado;
        try {
            banco.verificarCifragemAtiva();
            cifrado = true;
        } catch (RuntimeException e) {
            cifrado = false;
        }
        boolean integro = banco.verificarIntegridade().isEmpty();
        long tamanho;
        try {
            tamanho = Files.size(banco.arquivo());
        } catch (IOException e) {
            tamanho = -1;
        }
        int total = perfis.listar().stream().mapToInt(p -> importacoes.listar(p.id()).size()).sum();
        return new SituacaoDaProtecao(contexto.protetor().nome(), contexto.protetor().seguroParaProducao(), cifrado, integro,
                tamanho, total, perfis.listar().size());
    }

    public List<Evento> eventosDeSeguranca(int limite) {
        return eventos.recentes(limite);
    }

    /**
     * Exclui TODOS os dados locais: banco, chave e arquivos auxiliares. Irreversível. Depois desta chamada o contexto
     * fica inutilizável e o aplicativo deve ser reiniciado.
     */
    public void excluirTodosOsDados() {
        politica.exigir(Funcionalidade.EXCLUSAO_DE_DADOS);
        contexto.excluirTudo();
    }

    /**
     * Cria um backup portátil protegido por senha (Premium). O arquivo só abre com a senha, em qualquer computador;
     * sem ela ninguém recupera os dados, nem mesmo o aplicativo. A senha informada é zerada.
     */
    public void criarBackup(Path destino, char[] senha) {
        try {
            politica.exigir(Funcionalidade.BACKUP_PROTEGIDO);
        } catch (RuntimeException e) {
            Arrays.fill(senha, '\0');
            throw e;
        }
        contexto.criarBackupComSenha(destino, senha);
        auditoria.registrar("BACKUP_CRIADO", null);
    }

    /**
     * Restaura um backup com senha, substituindo TODOS os dados locais. Não exige Premium: recuperar os próprios dados
     * nunca é bloqueado por plano. Depois do sucesso o aplicativo precisa ser reiniciado.
     */
    public void restaurarBackup(Path arquivo, char[] senha) {
        contexto.restaurarBackupComSenha(arquivo, senha);
    }
}
