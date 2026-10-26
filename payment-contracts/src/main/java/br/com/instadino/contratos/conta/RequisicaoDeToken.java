package br.com.instadino.contratos.conta;

/** Verificação de e-mail: o token enviado por e-mail. */
public record RequisicaoDeToken(String token) {

    @Override
    public String toString() {
        return "RequisicaoDeToken[token=***]";
    }
}
