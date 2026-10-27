package br.com.instadino.servicos;

import br.com.instadino.importacao.LimitesDeImportacao;
import br.com.instadino.nucleo.acesso.FonteDeLicenca;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.BackupComSenha;
import br.com.instadino.persistencia.BackupLocal;
import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade;
import br.com.instadino.persistencia.repositorio.RepositorioDeConteudo;
import br.com.instadino.persistencia.repositorio.RepositorioDeConsentimentos;
import br.com.instadino.persistencia.repositorio.RepositorioDeEventosDeSeguranca;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDePerfis;
import br.com.instadino.persistencia.repositorio.RepositorioDePreferencias;
import br.com.instadino.persistencia.seguranca.ArmazemDeChave;
import br.com.instadino.persistencia.seguranca.ProtetorDeSegredo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Raiz de composição do núcleo local: abre o banco cifrado e expõe os serviços. A interface gráfica só conversa
 * com os serviços; nunca com o banco.
 */
public final class ContextoLocal implements AutoCloseable {

    private static final String ARQUIVO_BANCO = "instadino.db";
    private static final String ARQUIVO_CHAVE = "chave-do-banco.protegida";

    private final Path diretorio;
    private final ProtetorDeSegredo protetor;
    private final BancoLocal banco;
    private final PoliticaDeAcesso politica;
    private final SessaoLocal sessao;
    private final ServicoDeImportacao importacao;
    private final ServicoDeRelacionamentos relacionamentos;
    private final ServicoDeHistorico historico;
    private final ServicoDeExportacao exportacao;
    private final ServicoDeDados dados;
    private final ServicoDeAnotacoes anotacoes;
    private final ServicoDePerfis servicoDePerfis;
    private final ServicoDeRelatorios relatorios;
    private final ServicoDeAtividade servicoDeAtividade;
    private final ServicoDeConteudo servicoDeConteudo;
    private final RepositorioDeConsentimentos consentimentos;
    private final RepositorioDePreferencias preferencias;
    private final RepositorioDePerfis perfis;
    private boolean excluido;
    private boolean restaurado;

    private ContextoLocal(Path diretorio, ProtetorDeSegredo protetor, FonteDeLicenca licenca, LimitesDeImportacao limites) {
        this.diretorio = diretorio;
        this.protetor = protetor;
        ArmazemDeChave armazem = new ArmazemDeChave(diretorio.resolve(ARQUIVO_CHAVE), protetor);
        byte[] chave = armazem.obterOuCriar();
        try {
            this.banco = BancoLocal.abrir(diretorio.resolve(ARQUIVO_BANCO), chave, CarregadorDeMigracoes.doClasspath());
        } finally {
            Arrays.fill(chave, (byte) 0);
        }
        this.consentimentos = new RepositorioDeConsentimentos(banco);
        this.preferencias = new RepositorioDePreferencias(banco);
        this.perfis = new RepositorioDePerfis(banco);
        RepositorioDeImportacoes repoImportacoes = new RepositorioDeImportacoes(banco);
        RepositorioDeEventosDeSeguranca eventos = new RepositorioDeEventosDeSeguranca(banco);
        this.politica = new PoliticaDeAcesso(licenca, consentimentos);
        this.sessao = new SessaoLocal(perfis.obterOuCriarPadrao().id());
        RegistroDeAuditoriaLocal auditoria = new RegistroDeAuditoriaLocal(eventos);
        RepositorioDeAnotacoes repoAnotacoes = new RepositorioDeAnotacoes(banco);
        this.relacionamentos = new ServicoDeRelacionamentos(politica, repoImportacoes, sessao, repoAnotacoes);
        Runnable aoAlterar = relacionamentos::invalidarCache;
        this.anotacoes = new ServicoDeAnotacoes(politica, repoAnotacoes, sessao);
        java.time.ZoneId fuso = java.time.ZoneId.systemDefault();
        this.servicoDeAtividade = new ServicoDeAtividade(politica, repoImportacoes, new RepositorioDeAtividade(banco), relacionamentos, sessao, fuso);
        this.servicoDeConteudo = new ServicoDeConteudo(politica, repoImportacoes, new RepositorioDeConteudo(banco), sessao, fuso);
        this.servicoDePerfis = new ServicoDePerfis(politica, perfis, sessao, auditoria, aoAlterar);
        this.importacao = new ServicoDeImportacao(limites, politica, repoImportacoes, perfis, auditoria, aoAlterar);
        this.historico = new ServicoDeHistorico(politica, repoImportacoes, sessao, auditoria, aoAlterar);
        this.exportacao = new ServicoDeExportacao(politica, relacionamentos, auditoria);
        this.relatorios = new ServicoDeRelatorios(politica, relacionamentos, historico, anotacoes, servicoDePerfis, auditoria,
                servicoDeAtividade, servicoDeConteudo);
        this.dados = new ServicoDeDados(politica, this, repoImportacoes, perfis, eventos, auditoria);
    }

    public static ContextoLocal abrir(Path diretorioDeDados, ProtetorDeSegredo protetor, FonteDeLicenca licenca) {
        return abrir(diretorioDeDados, protetor, licenca, LimitesDeImportacao.padrao());
    }

    public static ContextoLocal abrir(Path diretorioDeDados, ProtetorDeSegredo protetor, FonteDeLicenca licenca, LimitesDeImportacao limites) {
        try {
            Files.createDirectories(diretorioDeDados);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Não foi possível criar o diretório de dados.");
        }
        return new ContextoLocal(diretorioDeDados.toAbsolutePath(), protetor, licenca, limites);
    }

    BancoLocal banco() {
        return banco;
    }

    ProtetorDeSegredo protetor() {
        return protetor;
    }

    synchronized void excluirTudo() {
        banco.close();
        BancoLocal.excluirArquivos(diretorio.resolve(ARQUIVO_BANCO));
        try {
            Path chave = diretorio.resolve(ARQUIVO_CHAVE);
            if (Files.isRegularFile(chave)) {
                long tamanho = Files.size(chave);
                Files.write(chave, new byte[(int) tamanho]);
                Files.delete(chave);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Não foi possível excluir a chave local.");
        }
        excluido = true;
    }

    public boolean excluido() {
        return excluido;
    }

    /** Cria um backup portátil protegido por senha (a senha é zerada). */
    synchronized void criarBackupComSenha(Path destino, char[] senha) {
        BackupComSenha.criar(banco, destino, senha);
    }

    /**
     * Restaura um backup protegido por senha. Senha errada ou arquivo inválido falham ANTES de qualquer alteração e o
     * contexto continua utilizável. Em caso de sucesso o banco foi substituído e o contexto fica inutilizável: o
     * aplicativo deve ser reiniciado ({@link #reinicioNecessario()}). O banco anterior é preservado como {@code .antes}.
     */
    synchronized void restaurarBackupComSenha(Path arquivo, char[] senha) {
        byte[] chave = new ArmazemDeChave(diretorio.resolve(ARQUIVO_CHAVE), protetor).obterOuCriar();
        Path pronto = null;
        try {
            pronto = BackupComSenha.prepararRestauracao(arquivo, senha, chave, diretorio);
            banco.close();
            restaurado = true; // a partir daqui o banco aberto não vale mais, qualquer que seja o desfecho
            BackupLocal.restaurar(pronto, diretorio.resolve(ARQUIVO_BANCO), chave);
        } finally {
            Arrays.fill(chave, (byte) 0);
            if (pronto != null) {
                BancoLocal.excluirArquivos(pronto);
            }
        }
    }

    /** Verdadeiro depois de uma restauração: os dados foram trocados e o aplicativo precisa ser reiniciado. */
    public boolean reinicioNecessario() {
        return restaurado;
    }

    public Path diretorioDeDados() {
        return diretorio;
    }

    public PoliticaDeAcesso politica() {
        return politica;
    }

    public SessaoLocal sessao() {
        return sessao;
    }

    public ServicoDeImportacao importacao() {
        return importacao;
    }

    public ServicoDeRelacionamentos relacionamentos() {
        return relacionamentos;
    }

    public ServicoDeHistorico historico() {
        return historico;
    }

    public ServicoDeExportacao exportacao() {
        return exportacao;
    }

    public ServicoDeAtividade atividade() {
        return servicoDeAtividade;
    }

    public ServicoDeConteudo conteudo() {
        return servicoDeConteudo;
    }

    public ServicoDeRelatorios relatorios() {
        return relatorios;
    }

    public ServicoDeAnotacoes anotacoes() {
        return anotacoes;
    }

    public ServicoDePerfis servicoDePerfis() {
        return servicoDePerfis;
    }

    public ServicoDeDados dados() {
        return dados;
    }

    public RepositorioDeConsentimentos consentimentos() {
        return consentimentos;
    }

    public RepositorioDePreferencias preferencias() {
        return preferencias;
    }

    public RepositorioDePerfis perfis() {
        return perfis;
    }

    @Override
    public void close() {
        if (!excluido) {
            banco.close();
        }
    }
}
