package br.com.instadino.servicos;

import br.com.instadino.importacao.LimitesDeImportacao;
import br.com.instadino.nucleo.acesso.FonteDeLicenca;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
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
    private final RepositorioDeConsentimentos consentimentos;
    private final RepositorioDePreferencias preferencias;
    private final RepositorioDePerfis perfis;
    private boolean excluido;

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
        this.relacionamentos = new ServicoDeRelacionamentos(politica, repoImportacoes, sessao);
        Runnable aoAlterar = relacionamentos::invalidarCache;
        this.importacao = new ServicoDeImportacao(limites, politica, repoImportacoes, perfis, auditoria, aoAlterar);
        this.historico = new ServicoDeHistorico(politica, repoImportacoes, sessao, auditoria, aoAlterar);
        this.exportacao = new ServicoDeExportacao(politica, relacionamentos, auditoria);
        this.dados = new ServicoDeDados(politica, this, repoImportacoes, perfis, eventos);
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
