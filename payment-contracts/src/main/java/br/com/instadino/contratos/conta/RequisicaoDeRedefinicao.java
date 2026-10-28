package br.com.instadino.contratos.conta;

public record RequisicaoDeRedefinicao(String token, String novaSenha) {

    @Override
    public String toString() {
        return "RequisicaoDeRedefinicao[token=***, novaSenha=***]";
    }
}
