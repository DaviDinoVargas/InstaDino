package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import br.com.instadino.servidor.config.PropriedadesDoServidor;
import br.com.instadino.servidor.dominio.Dispositivo;
import br.com.instadino.servidor.dominio.LicencaEmitida;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeDispositivos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeLicencas;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emissão e renovação de licenças assinadas e gestão de dispositivos autorizados. */
@Service
public class ServicoDeLicencas {

    private static final Pattern DISPOSITIVO = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final ServicoDeDireitos direitos;
    private final AssinadorDeLicencas assinador;
    private final RepositorioDeDispositivos dispositivos;
    private final RepositorioDeLicencas licencas;
    private final ServicoDeAuditoria auditoria;
    private final PropriedadesDoServidor propriedades;
    private final Clock relogio;

    ServicoDeLicencas(ServicoDeDireitos direitos, AssinadorDeLicencas assinador, RepositorioDeDispositivos dispositivos,
                      RepositorioDeLicencas licencas, ServicoDeAuditoria auditoria, PropriedadesDoServidor propriedades, Clock relogio) {
        this.direitos = direitos;
        this.assinador = assinador;
        this.dispositivos = dispositivos;
        this.licencas = licencas;
        this.auditoria = auditoria;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * Ativa o dispositivo (se necessário) e emite uma licença nova. Serve também para renovar. O direito é decidido aqui,
     * a partir do estado da conta no servidor.
     */
    @Transactional
    public LicencaAssinada ativarOuRenovar(UUID contaId, String idDoDispositivo, String nomeDoDispositivo) {
        if (idDoDispositivo == null || !DISPOSITIVO.matcher(idDoDispositivo).matches()) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "DISPOSITIVO_INVALIDO", "Identificador de dispositivo inválido.");
        }
        Instant agora = relogio.instant();
        var direito = direitos.direitoDa(contaId);
        if (!direito.premium()) {
            auditoria.conta(contaId, "LICENCA_NEGADA", idDoDispositivo.substring(0, Math.min(8, idDoDispositivo.length())), direito.situacao());
            throw new ErroDeNegocio(HttpStatus.FORBIDDEN, "LICENCA_INDISPONIVEL", "Esta conta não possui uma assinatura Premium ativa.");
        }
        String nome = nomeDoDispositivo == null ? null : nomeDoDispositivo.strip();
        if (nome != null && nome.length() > 100) {
            nome = nome.substring(0, 100);
        }
        Dispositivo dispositivo = dispositivos.findByContaIdAndIdentificador(contaId, idDoDispositivo).orElse(null);
        if (dispositivo == null || !dispositivo.ativo()) {
            long ativos = dispositivos.findByContaIdAndRevogadoEmIsNull(contaId).size();
            if (ativos >= propriedades.licenca().limiteDeDispositivos()) {
                throw new ErroDeNegocio(HttpStatus.CONFLICT, "LIMITE_DE_DISPOSITIVOS",
                        "Você atingiu o limite de " + propriedades.licenca().limiteDeDispositivos()
                                + " dispositivos. Remova um dispositivo antigo para ativar este.");
            }
            if (dispositivo == null) {
                dispositivo = dispositivos.save(new Dispositivo(contaId, idDoDispositivo, nome, agora));
            } else {
                dispositivo.reativar(nome, agora);
            }
            auditoria.conta(contaId, "DISPOSITIVO_ATIVADO", dispositivo.getId().toString(), null);
        }
        Instant validaAte = agora.plus(Duration.ofDays(propriedades.licenca().validadeEmDias()));
        if (direito.fim() != null && direito.fim().isBefore(validaAte)) {
            validaAte = direito.fim();
        }
        Instant tolerancia = validaAte.plus(Duration.ofDays(propriedades.licenca().toleranciaOfflineEmDias()));
        LicencaEmitida emitida = licencas.save(new LicencaEmitida(contaId, dispositivo.getId(), agora, validaAte, tolerancia, direito.origem()));
        auditoria.conta(contaId, "LICENCA_EMITIDA", emitida.getId().toString(), direito.origem());
        return assinador.assinar(new PayloadDeLicenca(PayloadDeLicenca.VERSAO_ATUAL, emitida.getId(), PayloadDeLicenca.PRODUTO, "PREMIUM",
                contaId, idDoDispositivo, agora, validaAte, tolerancia, direito.origem()));
    }

    @Transactional(readOnly = true)
    public List<DispositivoAutorizado> dispositivosDa(UUID contaId) {
        return dispositivos.findByContaIdAndRevogadoEmIsNull(contaId).stream()
                .map(d -> new DispositivoAutorizado(d.getId(), d.getNome(), d.getAtivadoEm())).toList();
    }

    /** Remove um dispositivo da PRÓPRIA conta; o de outra conta é tratado como inexistente. */
    @Transactional
    public void removerDispositivo(UUID contaId, UUID dispositivoId) {
        Dispositivo d = dispositivos.findByIdAndContaId(dispositivoId, contaId).filter(Dispositivo::ativo)
                .orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Dispositivo não encontrado."));
        d.revogar(relogio.instant());
        auditoria.conta(contaId, "DISPOSITIVO_REMOVIDO", dispositivoId.toString(), null);
    }
}
