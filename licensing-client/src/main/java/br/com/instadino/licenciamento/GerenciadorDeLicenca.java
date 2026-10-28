package br.com.instadino.licenciamento;

import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.FonteDeLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Mantém a sessão comercial e fornece o {@link EstadoDaLicenca} ao restante do aplicativo.
 *
 * <p>O plano Premium só existe se houver uma licença assinada pelo servidor que passe em
 * {@link VerificadorDeLicenca}. Nada local (arquivo, preferência, variável) concede Premium. Sem rede, a última
 * licença válida continua valendo até o fim da validade e da tolerância offline; depois disso o plano volta a Free
 * sem apagar nenhum dado local.
 */
public final class GerenciadorDeLicenca implements FonteDeLicenca {

    /** Resultado de uma operação de rede mostrado ao usuário. */
    public record Resultado(boolean sucesso, String mensagem) {
    }

    private static final Duration INTERVALO_DE_PERSISTENCIA = Duration.ofMinutes(30);

    private final ApiComercial api;
    private final ArmazemDeConta armazem;
    private final VerificadorDeLicenca verificador;
    private final Clock relogio;
    private final String idDoDispositivo;
    private final String nomeDoDispositivo;
    private DadosDaConta dados;
    private Instant ultimaPersistencia = Instant.MIN;

    /** @param api nula quando o serviço comercial não está configurado: o aplicativo funciona só com o plano Free */
    public GerenciadorDeLicenca(ApiComercial api, ArmazemDeConta armazem, VerificadorDeLicenca verificador, Clock relogio,
                                String idDoDispositivo, String nomeDoDispositivo) {
        this.api = api;
        this.armazem = armazem;
        this.verificador = verificador;
        this.relogio = relogio;
        this.idDoDispositivo = idDoDispositivo;
        this.nomeDoDispositivo = nomeDoDispositivo;
        this.dados = armazem.ler().orElse(null);
    }

    public boolean servicoConfigurado() {
        return api != null;
    }

    public ApiComercial api() {
        return api;
    }

    public synchronized Optional<ResumoDaConta> conta() {
        return Optional.ofNullable(dados).map(DadosDaConta::conta);
    }

    public synchronized boolean autenticado() {
        return dados != null && dados.tokenExpiraEm() != null && dados.tokenExpiraEm().isAfter(relogio.instant());
    }

    /** Token da sessão atual para chamadas autenticadas; vazio se não houver sessão válida. */
    public synchronized Optional<String> token() {
        return autenticado() ? Optional.of(dados.token()) : Optional.empty();
    }

    // ---- FonteDeLicenca ----

    @Override
    public synchronized EstadoDaLicenca estadoAtual() {
        if (dados == null || dados.licenca() == null || dados.conta() == null) {
            return EstadoDaLicenca.GRATUITO;
        }
        Instant agora = relogio.instant();
        var r = verificador.verificar(dados.licenca(), dados.conta().id(), idDoDispositivo, agora, dados.ultimoInstanteVisto());
        if (r.motivo() == VerificadorDeLicenca.Motivo.OK || r.motivo() == VerificadorDeLicenca.Motivo.EXPIRADA) {
            registrarInstante(agora);
        }
        return r.estado();
    }

    private void registrarInstante(Instant agora) {
        if (dados.ultimoInstanteVisto() == null || agora.isAfter(dados.ultimoInstanteVisto())) {
            dados = dados.comVisto(agora);
            if (Duration.between(ultimaPersistencia, agora).compareTo(INTERVALO_DE_PERSISTENCIA) > 0) {
                armazem.gravar(dados);
                ultimaPersistencia = agora;
            }
        }
    }

    // ---- operações ----

    public synchronized Resultado entrar(String email, String senha) {
        exigirServico();
        try {
            var r = api.entrar(email, senha, idDoDispositivo, nomeDoDispositivo);
            dados = new DadosDaConta(r.token(), r.expiraEm(), r.conta(), null, relogio.instant());
            armazem.gravar(dados);
            ultimaPersistencia = relogio.instant();
            return atualizarLicencaInterno("Você entrou na sua conta.");
        } catch (ErroDaApi e) {
            return new Resultado(false, e.getMessage());
        } catch (FalhaDeRede e) {
            return new Resultado(false, e.getMessage());
        }
    }

    /** Pede uma licença nova ao servidor. Sem rede, mantém a licença atual até o fim da tolerância offline. */
    public synchronized Resultado atualizarLicenca() {
        exigirServico();
        if (!autenticado()) {
            return new Resultado(false, "Entre na sua conta para atualizar a licença.");
        }
        return atualizarLicencaInterno("Licença atualizada.");
    }

    private Resultado atualizarLicencaInterno(String mensagemDeSucesso) {
        try {
            LicencaAssinada nova = api.ativarLicenca(dados.token(), idDoDispositivo, nomeDoDispositivo);
            Instant agora = relogio.instant();
            // ao validar uma licença emitida agora pelo servidor, o relógio de referência é reiniciado
            var r = verificador.verificar(nova, dados.conta().id(), idDoDispositivo, agora, null);
            if (r.motivo() != VerificadorDeLicenca.Motivo.OK) {
                dados = dados.comLicenca(null, agora);
                armazem.gravar(dados);
                return new Resultado(false, "A licença recebida não pôde ser validada neste computador (" + r.motivo() + ").");
            }
            Instant referencia = r.payload().emitidaEm().isAfter(agora) ? r.payload().emitidaEm() : agora;
            dados = dados.comLicenca(nova, referencia);
            armazem.gravar(dados);
            ultimaPersistencia = agora;
            return new Resultado(true, mensagemDeSucesso + " Plano Premium ativo neste dispositivo.");
        } catch (ErroDaApi e) {
            if (e.codigo().equals("LICENCA_INDISPONIVEL")) {
                dados = dados.comLicenca(null, relogio.instant());
                armazem.gravar(dados);
                return new Resultado(true, mensagemDeSucesso + " Esta conta usa o plano Free.");
            }
            if (e.status() == 401) {
                limparSessao();
                return new Resultado(false, "Sua sessão expirou. Entre novamente.");
            }
            return new Resultado(false, e.getMessage());
        } catch (FalhaDeRede e) {
            return new Resultado(false, e.getMessage() + " A licença atual continua valendo até o fim do período de tolerância.");
        }
    }

    public synchronized Resultado sair() {
        if (dados != null && api != null && autenticado()) {
            try {
                api.sair(dados.token());
            } catch (ErroDaApi | FalhaDeRede ignorada) {
                // a sessão local é removida de qualquer forma
            }
        }
        limparSessao();
        return new Resultado(true, "Você saiu da conta. Seus dados locais foram mantidos.");
    }

    /** Persiste o último instante visto; chamado ao encerrar o aplicativo. */
    public synchronized void encerrar() {
        if (dados != null) {
            armazem.gravar(dados);
        }
    }

    private void limparSessao() {
        dados = null;
        armazem.apagar();
    }

    private void exigirServico() {
        if (api == null) {
            throw new IllegalStateException("O serviço comercial não está configurado.");
        }
    }

    /** Descreve a situação da licença para a interface. */
    public synchronized String descricaoDaLicenca() {
        EstadoDaLicenca estado = estadoAtual();
        if (estado.plano() == Plano.PREMIUM && estado.situacao() == SituacaoDaLicenca.ATIVA) {
            return "Licença Premium ativa neste dispositivo.";
        }
        if (estado.situacao() == SituacaoDaLicenca.TOLERANCIA_OFFLINE) {
            return "Licença Premium em período de tolerância offline. Conecte-se à internet para renová-la.";
        }
        if (estado.situacao() == SituacaoDaLicenca.EXPIRADA) {
            return "A licença Premium expirou. Conecte-se e atualize a licença. Seus dados locais continuam acessíveis.";
        }
        if (estado.situacao() == SituacaoDaLicenca.INVALIDA) {
            return "A licença guardada neste computador não é válida e foi ignorada. Atualize a licença com a internet.";
        }
        return "Nenhuma licença Premium neste dispositivo (plano Free).";
    }
}
