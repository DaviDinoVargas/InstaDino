package br.com.instadino.licenciamento;

import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Valida uma licença recebida do servidor ANTES de interpretar qualquer campo: algoritmo, chave conhecida, assinatura
 * Ed25519 sobre os bytes exatos do payload e, só então, produto, plano, titular, dispositivo, datas e relógio.
 * Qualquer falha resulta em plano Free; nunca em exceção que possa ser ignorada.
 */
public final class VerificadorDeLicenca {

    public enum Motivo {
        OK, FORMATO_INVALIDO, ALGORITMO_NAO_SUPORTADO, CHAVE_DESCONHECIDA, ASSINATURA_INVALIDA, VERSAO_NAO_SUPORTADA, PRODUTO_INCORRETO,
        PLANO_INVALIDO, TITULAR_DIFERENTE, DISPOSITIVO_DIFERENTE, EMITIDA_NO_FUTURO, RELOGIO_RETROCEDEU, EXPIRADA
    }

    public record Resultado(EstadoDaLicenca estado, PayloadDeLicenca payload, Motivo motivo) {
    }

    private static final Duration FOLGA_DO_RELOGIO = Duration.ofMinutes(5);
    private static final int TAMANHO_MAXIMO_DO_PAYLOAD = 4096;

    private final ChavesConfiaveis chaves;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public VerificadorDeLicenca(ChavesConfiaveis chaves) {
        this.chaves = chaves;
    }

    /**
     * @param ultimoInstanteVisto maior instante já observado neste computador (para detectar retrocesso do relógio); pode ser nulo
     */
    public Resultado verificar(LicencaAssinada licenca, UUID titularEsperado, String dispositivoEsperado, Instant agora, Instant ultimoInstanteVisto) {
        if (licenca == null || licenca.payload() == null || licenca.assinatura() == null || licenca.kid() == null || licenca.algoritmo() == null) {
            return falha(Motivo.FORMATO_INVALIDO);
        }
        if (!LicencaAssinada.ALGORITMO.equals(licenca.algoritmo())) {
            return falha(Motivo.ALGORITMO_NAO_SUPORTADO);
        }
        PublicKey chave = chaves.chave(licenca.kid()).orElse(null);
        if (chave == null) {
            return falha(Motivo.CHAVE_DESCONHECIDA);
        }
        byte[] bytesDoPayload;
        byte[] assinatura;
        try {
            if (licenca.payload().length() > TAMANHO_MAXIMO_DO_PAYLOAD * 2) {
                return falha(Motivo.FORMATO_INVALIDO);
            }
            bytesDoPayload = Base64.getUrlDecoder().decode(licenca.payload());
            assinatura = Base64.getUrlDecoder().decode(licenca.assinatura());
        } catch (IllegalArgumentException e) {
            return falha(Motivo.FORMATO_INVALIDO);
        }
        if (bytesDoPayload.length == 0 || bytesDoPayload.length > TAMANHO_MAXIMO_DO_PAYLOAD) {
            return falha(Motivo.FORMATO_INVALIDO);
        }
        try {
            Signature verificador = Signature.getInstance("Ed25519");
            verificador.initVerify(chave);
            verificador.update(bytesDoPayload);
            if (!verificador.verify(assinatura)) {
                return falha(Motivo.ASSINATURA_INVALIDA);
            }
        } catch (GeneralSecurityException e) {
            return falha(Motivo.ASSINATURA_INVALIDA);
        }
        PayloadDeLicenca p;
        try {
            p = json.readValue(new String(bytesDoPayload, StandardCharsets.UTF_8), PayloadDeLicenca.class);
        } catch (java.io.IOException | RuntimeException e) {
            return falha(Motivo.FORMATO_INVALIDO);
        }
        if (p.licencaId() == null || p.titularId() == null || p.dispositivoId() == null || p.emitidaEm() == null || p.validaAte() == null
                || p.toleranciaOfflineAte() == null || p.plano() == null || p.produto() == null) {
            return falha(Motivo.FORMATO_INVALIDO);
        }
        if (p.versao() != PayloadDeLicenca.VERSAO_ATUAL) {
            return comPayload(Motivo.VERSAO_NAO_SUPORTADA, p);
        }
        if (!PayloadDeLicenca.PRODUTO.equals(p.produto())) {
            return comPayload(Motivo.PRODUTO_INCORRETO, p);
        }
        if (!"PREMIUM".equals(p.plano())) {
            return comPayload(Motivo.PLANO_INVALIDO, p);
        }
        if (!p.titularId().equals(titularEsperado)) {
            return comPayload(Motivo.TITULAR_DIFERENTE, p);
        }
        if (!p.dispositivoId().equals(dispositivoEsperado)) {
            return comPayload(Motivo.DISPOSITIVO_DIFERENTE, p);
        }
        if (!p.validaAte().isAfter(p.emitidaEm()) || p.toleranciaOfflineAte().isBefore(p.validaAte())) {
            return comPayload(Motivo.FORMATO_INVALIDO, p);
        }
        if (p.emitidaEm().isAfter(agora.plus(FOLGA_DO_RELOGIO))) {
            return comPayload(Motivo.EMITIDA_NO_FUTURO, p);
        }
        if (ultimoInstanteVisto != null && agora.isBefore(ultimoInstanteVisto.minus(FOLGA_DO_RELOGIO))) {
            return comPayload(Motivo.RELOGIO_RETROCEDEU, p);
        }
        if (agora.isBefore(p.validaAte())) {
            return new Resultado(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA), p, Motivo.OK);
        }
        if (agora.isBefore(p.toleranciaOfflineAte())) {
            return new Resultado(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.TOLERANCIA_OFFLINE), p, Motivo.OK);
        }
        return new Resultado(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.EXPIRADA), p, Motivo.EXPIRADA);
    }

    private static Resultado falha(Motivo motivo) {
        return new Resultado(new EstadoDaLicenca(Plano.FREE, SituacaoDaLicenca.INVALIDA), null, motivo);
    }

    private static Resultado comPayload(Motivo motivo, PayloadDeLicenca p) {
        return new Resultado(new EstadoDaLicenca(Plano.FREE, SituacaoDaLicenca.INVALIDA), p, motivo);
    }
}
