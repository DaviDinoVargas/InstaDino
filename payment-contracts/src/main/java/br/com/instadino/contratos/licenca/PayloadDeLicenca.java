package br.com.instadino.contratos.licenca;

import java.time.Instant;
import java.util.UUID;

/**
 * Conteúdo assinado de uma licença. O servidor assina os bytes exatos deste JSON; o cliente verifica a assinatura
 * antes de interpretar qualquer campo.
 *
 * @param produto sempre {@value #PRODUTO}
 * @param titularId conta dona da licença
 * @param dispositivoId dispositivo ao qual a licença está vinculada
 * @param validaAte fim da validade normal; depois dele só vale a tolerância offline
 * @param toleranciaOfflineAte fim absoluto do uso sem conexão
 * @param origem como o direito foi obtido (ASSINATURA, CONCESSAO_ADMINISTRATIVA, PAPEL_MASTER)
 */
public record PayloadDeLicenca(int versao, UUID licencaId, String produto, String plano, UUID titularId, String dispositivoId,
                               Instant emitidaEm, Instant validaAte, Instant toleranciaOfflineAte, String origem) {

    public static final String PRODUTO = "instadino-desktop";
    public static final int VERSAO_ATUAL = 1;
}
