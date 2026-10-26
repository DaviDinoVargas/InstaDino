package br.com.instadino.contratos.pagamento;

import java.time.Instant;

/** Situação comercial da conta, calculada no servidor. */
public record EstadoDaAssinatura(String situacao, String planoId, Instant validaAte, boolean premiumVigente, String origem) {
}
