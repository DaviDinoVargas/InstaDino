package br.com.instadino.licenciamento;

import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.contratos.pagamento.ItemDoHistorico;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import java.util.List;
import java.util.UUID;

/** Contrato do serviço comercial. Todas as chamadas podem lançar {@link ErroDaApi} ou {@link FalhaDeRede}. */
public interface ApiComercial {

    void cadastrar(String email, String senha, String nome);

    void verificarEmail(String token);

    RespostaDeLogin entrar(String email, String senha, String idDoDispositivo, String nomeDoDispositivo);

    void sair(String token);

    ResumoDaConta conta(String token);

    List<SessaoAtiva> sessoes(String token);

    void revogarSessao(String token, UUID sessaoId);

    void solicitarRecuperacao(String email);

    void redefinirSenha(String tokenDeRecuperacao, String novaSenha);

    LicencaAssinada ativarLicenca(String token, String idDoDispositivo, String nomeDoDispositivo);

    EstadoDaAssinatura assinatura(String token);

    List<DispositivoAutorizado> dispositivos(String token);

    void removerDispositivo(String token, UUID dispositivoId);

    RespostaDeCatalogo catalogo();

    RespostaDeCheckout criarCheckout(String token, String planoId);

    List<ItemDoHistorico> historico(String token);

    /** Somente sandbox: gera um evento de pagamento de teste que passa pela mesma verificação de um webhook real. */
    void simularEventoDeSandbox(String token, UUID pedidoId, String tipoDeEvento);
}
