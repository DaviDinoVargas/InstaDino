package br.com.instadino.servidor.email;

/** Mensagem transacional. {@code token} é o segredo de uso único incluído no link; nunca deve ir para logs. */
public record MensagemDeEmail(String para, Tipo tipo, String assunto, String corpo, String token) {

    public enum Tipo { VERIFICACAO_EMAIL, RECUPERACAO_SENHA, AVISO_DE_CADASTRO_EXISTENTE }

    @Override
    public String toString() {
        return "MensagemDeEmail[tipo=" + tipo + ", token=***]";
    }
}
