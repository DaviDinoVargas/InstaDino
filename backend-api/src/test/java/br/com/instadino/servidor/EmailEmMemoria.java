package br.com.instadino.servidor;

import br.com.instadino.servidor.email.EnviadorDeEmail;
import br.com.instadino.servidor.email.MensagemDeEmail;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captura e-mails em memória para que os testes leiam os tokens. */
public final class EmailEmMemoria implements EnviadorDeEmail {

    private final List<MensagemDeEmail> enviadas = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(MensagemDeEmail mensagem) {
        enviadas.add(mensagem);
    }

    public List<MensagemDeEmail> para(String email) {
        return enviadas.stream().filter(m -> m.para().equals(email)).toList();
    }

    public MensagemDeEmail ultima(String email, MensagemDeEmail.Tipo tipo) {
        return para(email).stream().filter(m -> m.tipo() == tipo).reduce((a, b) -> b).orElseThrow(
                () -> new AssertionError("Nenhum e-mail " + tipo + " para " + email));
    }

    public long quantos(String email, MensagemDeEmail.Tipo tipo) {
        return para(email).stream().filter(m -> m.tipo() == tipo).count();
    }
}
