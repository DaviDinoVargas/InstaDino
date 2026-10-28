package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.conta.RequisicaoDeCadastro;
import br.com.instadino.contratos.conta.RequisicaoDeLogin;
import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.servidor.config.PropriedadesDoServidor;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeSessoes;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeTokens;
import br.com.instadino.servidor.dominio.Sessao;
import br.com.instadino.servidor.dominio.TokenDeUsoUnico;
import br.com.instadino.servidor.dominio.TokenDeUsoUnico.Finalidade;
import br.com.instadino.servidor.email.EnviadorDeEmail;
import br.com.instadino.servidor.email.MensagemDeEmail;
import br.com.instadino.servidor.seguranca.CriptoDeTokens;
import br.com.instadino.servidor.seguranca.PoliticaDeSenha;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro, verificação de e-mail, login, sessões e recuperação de senha. */
@Service
public class ServicoDeContas {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}\\.[^@\\s.]{2,}$");
    private static final Pattern DISPOSITIVO = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    static final String MENSAGEM_CREDENCIAIS = "E-mail ou senha incorretos, ou conta temporariamente indisponível.";

    private final RepositorioDeContas contas;
    private final RepositorioDeSessoes sessoes;
    private final RepositorioDeTokens tokens;
    private final PasswordEncoder codificador;
    private final EnviadorDeEmail email;
    private final ServicoDeAuditoria auditoria;
    private final Clock relogio;
    private final PropriedadesDoServidor propriedades;
    private final String hashDeFachada;

    ServicoDeContas(RepositorioDeContas contas, RepositorioDeSessoes sessoes, RepositorioDeTokens tokens, PasswordEncoder codificador,
                    EnviadorDeEmail email, ServicoDeAuditoria auditoria, Clock relogio, PropriedadesDoServidor propriedades) {
        this.contas = contas;
        this.sessoes = sessoes;
        this.tokens = tokens;
        this.codificador = codificador;
        this.email = email;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.propriedades = propriedades;
        // usado para igualar o tempo de resposta quando a conta não existe
        this.hashDeFachada = codificador.encode(CriptoDeTokens.novoToken());
    }

    public static String normalizarEmail(String bruto) {
        if (bruto == null) {
            return null;
        }
        String e = Normalizer.normalize(bruto.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        for (int i = 0; i < e.length(); i++) {
            if (Character.isISOControl(e.charAt(i))) {
                return null;
            }
        }
        return e.length() <= 254 && EMAIL.matcher(e).matches() ? e : null;
    }

    // ---- cadastro ----

    /** Resposta sempre igual, exista ou não a conta, para não permitir enumeração. */
    @Transactional
    public void cadastrar(RequisicaoDeCadastro req) {
        String emailNormalizado = normalizarEmail(req == null ? null : req.email());
        if (emailNormalizado == null) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "EMAIL_INVALIDO", "Informe um e-mail válido.");
        }
        String nome = req.nome() == null ? "" : req.nome().strip();
        if (nome.isEmpty() || nome.length() > 100 || nome.chars().anyMatch(Character::isISOControl)) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "NOME_INVALIDO", "Informe um nome com até 100 caracteres.");
        }
        PoliticaDeSenha.validar(req.senha(), emailNormalizado).ifPresent(m -> {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "SENHA_FRACA", m);
        });
        Instant agora = relogio.instant();
        var existente = contas.findByEmailNormalizado(emailNormalizado);
        if (existente.isPresent()) {
            codificador.encode(req.senha()); // equaliza o custo de processamento
            email.enviar(new MensagemDeEmail(emailNormalizado, MensagemDeEmail.Tipo.AVISO_DE_CADASTRO_EXISTENTE,
                    "Tentativa de cadastro no InstaDino",
                    "Alguém tentou criar uma conta com este e-mail, mas ele já está cadastrado. Se foi você, use a recuperação de senha.", null));
            auditoria.sistema("CADASTRO_DUPLICADO", existente.get().getId().toString(), null);
            return;
        }
        Conta conta = contas.save(new Conta(req.email().strip(), emailNormalizado, nome, codificador.encode(req.senha()), Papel.CLIENTE, false, agora));
        String token = emitirToken(conta.getId(), Finalidade.VERIFICACAO_EMAIL, Duration.ofHours(24), agora);
        email.enviar(new MensagemDeEmail(emailNormalizado, MensagemDeEmail.Tipo.VERIFICACAO_EMAIL, "Confirme seu e-mail no InstaDino",
                "Use o código a seguir no aplicativo para confirmar seu e-mail. Ele vale por 24 horas e só pode ser usado uma vez.", token));
        auditoria.conta(conta.getId(), "CONTA_CRIADA", conta.getId().toString(), null);
    }

    @Transactional
    public void verificarEmail(String token) {
        Instant agora = relogio.instant();
        String hash = CriptoDeTokens.hash(token == null ? "" : token);
        var registro = tokens.findByHashToken(hash);
        if (registro.isEmpty() || registro.get().getFinalidade() != Finalidade.VERIFICACAO_EMAIL
                || tokens.consumir(hash, Finalidade.VERIFICACAO_EMAIL, agora) != 1) {
            throw tokenInvalido();
        }
        Conta conta = contas.findById(registro.get().getContaId()).orElseThrow(ServicoDeContas::tokenInvalido);
        conta.verificarEmail(agora);
        auditoria.conta(conta.getId(), "EMAIL_VERIFICADO", conta.getId().toString(), null);
    }

    // ---- login e sessões ----

    @Transactional(noRollbackFor = ErroDeNegocio.class)
    public RespostaDeLogin login(RequisicaoDeLogin req) {
        String emailNormalizado = normalizarEmail(req == null ? null : req.email());
        if (emailNormalizado == null || req.senha() == null || req.senha().isEmpty() || req.senha().length() > PoliticaDeSenha.MAXIMO
                || req.idDoDispositivo() == null || !DISPOSITIVO.matcher(req.idDoDispositivo()).matches()) {
            codificador.matches("x", hashDeFachada);
            throw credenciaisInvalidas();
        }
        Instant agora = relogio.instant();
        Conta conta = contas.findByEmailNormalizado(emailNormalizado).orElse(null);
        if (conta == null || conta.bloqueada(agora)) {
            codificador.matches(req.senha(), hashDeFachada);
            if (conta != null) {
                auditoria.conta(conta.getId(), "LOGIN_BLOQUEADO", conta.getId().toString(), null);
            }
            throw credenciaisInvalidas();
        }
        if (!codificador.matches(req.senha(), conta.getHashSenha())) {
            var seg = propriedades.seguranca();
            conta.registrarFalha(seg.falhasAteBloquear(), Duration.ofMinutes(seg.bloqueioBaseEmMinutos()), agora);
            auditoria.conta(conta.getId(), "LOGIN_FALHOU", conta.getId().toString(), "tentativas=" + conta.getTentativasFalhas());
            throw credenciaisInvalidas();
        }
        if (!conta.isAtiva()) {
            throw credenciaisInvalidas();
        }
        if (!conta.isEmailVerificado()) {
            auditoria.conta(conta.getId(), "LOGIN_EMAIL_NAO_VERIFICADO", conta.getId().toString(), null);
            throw new ErroDeNegocio(HttpStatus.FORBIDDEN, "EMAIL_NAO_VERIFICADO", "Confirme seu e-mail para entrar.");
        }
        conta.registrarSucesso(agora);
        String token = CriptoDeTokens.novoToken();
        String nomeDoDispositivo = req.nomeDoDispositivo() == null ? null : limpar(req.nomeDoDispositivo(), 100);
        Instant expira = agora.plus(Duration.ofDays(propriedades.sessao().validadeEmDias()));
        sessoes.save(new Sessao(conta.getId(), CriptoDeTokens.hash(token), req.idDoDispositivo(), nomeDoDispositivo, agora, expira));
        auditoria.conta(conta.getId(), "LOGIN", conta.getId().toString(), null);
        return new RespostaDeLogin(token, expira, resumo(conta));
    }

    @Transactional
    public void sair(UUID contaId, UUID sessaoId) {
        sessoes.findByIdAndContaId(sessaoId, contaId).ifPresent(s -> {
            s.revogar(relogio.instant());
            auditoria.conta(contaId, "LOGOUT", sessaoId.toString(), null);
        });
    }

    @Transactional(readOnly = true)
    public List<SessaoAtiva> listarSessoes(UUID contaId, UUID sessaoAtual) {
        Instant agora = relogio.instant();
        return sessoes.findByContaIdOrderByCriadaEmDesc(contaId).stream().filter(s -> s.valida(agora))
                .map(s -> new SessaoAtiva(s.getId(), s.getNomeDispositivo(), s.getCriadaEm(), s.getUltimoUso(), s.getExpiraEm(),
                        s.getId().equals(sessaoAtual))).toList();
    }

    /** Revoga uma sessão da PRÓPRIA conta; sessões de outras contas são tratadas como inexistentes. */
    @Transactional
    public void revogarSessao(UUID contaId, UUID sessaoId) {
        Sessao s = sessoes.findByIdAndContaId(sessaoId, contaId)
                .orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Sessão não encontrada."));
        s.revogar(relogio.instant());
        auditoria.conta(contaId, "SESSAO_REVOGADA", sessaoId.toString(), null);
    }

    @Transactional(readOnly = true)
    public ResumoDaConta resumoDaConta(UUID contaId) {
        return resumo(contas.findById(contaId).orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Conta não encontrada.")));
    }

    // ---- recuperação de senha ----

    /** Resposta sempre igual, exista ou não a conta. */
    @Transactional
    public void solicitarRecuperacao(String emailBruto) {
        String emailNormalizado = normalizarEmail(emailBruto);
        if (emailNormalizado == null) {
            return;
        }
        contas.findByEmailNormalizado(emailNormalizado).filter(Conta::isAtiva).ifPresent(conta -> {
            Instant agora = relogio.instant();
            String token = emitirToken(conta.getId(), Finalidade.RECUPERACAO_SENHA, Duration.ofMinutes(30), agora);
            email.enviar(new MensagemDeEmail(emailNormalizado, MensagemDeEmail.Tipo.RECUPERACAO_SENHA, "Recuperação de senha do InstaDino",
                    "Use o código a seguir no aplicativo para criar uma nova senha. Ele vale por 30 minutos e só pode ser usado uma vez. "
                            + "Se você não pediu, ignore esta mensagem.", token));
            auditoria.conta(conta.getId(), "RECUPERACAO_SOLICITADA", conta.getId().toString(), null);
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        Instant agora = relogio.instant();
        String hash = CriptoDeTokens.hash(token == null ? "" : token);
        TokenDeUsoUnico registro = tokens.findByHashToken(hash).filter(t -> t.getFinalidade() == Finalidade.RECUPERACAO_SENHA)
                .orElseThrow(ServicoDeContas::tokenInvalido);
        Conta conta = contas.findById(registro.getContaId()).orElseThrow(ServicoDeContas::tokenInvalido);
        PoliticaDeSenha.validar(novaSenha, conta.getEmailNormalizado()).ifPresent(m -> {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "SENHA_FRACA", m);
        });
        if (tokens.consumir(hash, Finalidade.RECUPERACAO_SENHA, agora) != 1) {
            throw tokenInvalido();
        }
        conta.definirHashSenha(codificador.encode(novaSenha), agora);
        int revogadas = sessoes.revogarTodasDaConta(conta.getId(), agora);
        auditoria.conta(conta.getId(), "SENHA_REDEFINIDA", conta.getId().toString(), "sessoes_revogadas=" + revogadas);
    }

    // ---- utilitários ----

    private String emitirToken(UUID contaId, Finalidade finalidade, Duration validade, Instant agora) {
        String token = CriptoDeTokens.novoToken();
        tokens.save(new TokenDeUsoUnico(contaId, finalidade, CriptoDeTokens.hash(token), agora, agora.plus(validade)));
        return token;
    }

    static ResumoDaConta resumo(Conta c) {
        return new ResumoDaConta(c.getId(), c.getEmail(), c.getNome(), c.getPapel(), c.isEmailVerificado());
    }

    private static String limpar(String texto, int limite) {
        String t = texto.strip();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length() && sb.length() < limite; i++) {
            if (!Character.isISOControl(t.charAt(i))) {
                sb.append(t.charAt(i));
            }
        }
        return sb.toString();
    }

    static ErroDeNegocio credenciaisInvalidas() {
        return new ErroDeNegocio(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", MENSAGEM_CREDENCIAIS);
    }

    static ErroDeNegocio tokenInvalido() {
        return new ErroDeNegocio(HttpStatus.BAD_REQUEST, "TOKEN_INVALIDO", "O código é inválido, expirou ou já foi usado.");
    }
}
