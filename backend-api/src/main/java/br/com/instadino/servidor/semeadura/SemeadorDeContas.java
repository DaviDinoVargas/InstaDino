package br.com.instadino.servidor.semeadura;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.config.PropriedadesDoServidor;
import br.com.instadino.servidor.dominio.Concessao;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeConcessoes;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import br.com.instadino.servidor.seguranca.PoliticaDeSenha;
import br.com.instadino.servidor.servico.ServicoDeAuditoria;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOMENTE DESENVOLVIMENTO/SANDBOX. Cria três contas de demonstração: master, cliente Premium e cliente Free.
 *
 * <p>Nenhuma senha existe no código ou no repositório (que é público). Cada senha vem da variável de ambiente
 * {@code INSTADINO_SENHA_MASTER}, {@code INSTADINO_SENHA_PREMIUM} ou {@code INSTADINO_SENHA_FREE} ou, na falta dela, é
 * gerada aleatoriamente. As senhas das contas criadas são gravadas em {@code <dados>/contas-semeadas.txt} (somente o dono
 * lê; ignorado pelo Git) e NUNCA são escritas no log. A semeadura é recusada em produção.
 */
@Component
@Profile("desenvolvimento")
class SemeadorDeContas implements ApplicationRunner {

    private static final System.Logger LOG = System.getLogger(SemeadorDeContas.class.getName());
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_!?";

    record ContaDeDemonstracao(String chave, String email, String nome, Papel papel, boolean premiumPorConcessao, String descricao) {
    }

    static final List<ContaDeDemonstracao> CONTAS = List.of(
            new ContaDeDemonstracao("MASTER", "master@instadino.test", "Conta Master (demonstração)", Papel.MASTER, false, "acesso total: administração e Premium"),
            new ContaDeDemonstracao("PREMIUM", "premium@instadino.test", "Cliente Premium (demonstração)", Papel.CLIENTE, true, "cliente com Premium"),
            new ContaDeDemonstracao("FREE", "free@instadino.test", "Cliente Free (demonstração)", Papel.CLIENTE, false, "cliente do plano Free"));

    private final PropriedadesDoServidor propriedades;
    private final Environment ambiente;
    private final RepositorioDeContas contas;
    private final RepositorioDeConcessoes concessoes;
    private final RepositorioDePlanos planos;
    private final PasswordEncoder codificador;
    private final ServicoDeAuditoria auditoria;
    private final Clock relogio;
    private final SecureRandom aleatorio = new SecureRandom();

    SemeadorDeContas(PropriedadesDoServidor propriedades, Environment ambiente, RepositorioDeContas contas, RepositorioDeConcessoes concessoes,
                     RepositorioDePlanos planos, PasswordEncoder codificador, ServicoDeAuditoria auditoria, Clock relogio) {
        this.propriedades = propriedades;
        this.ambiente = ambiente;
        this.contas = contas;
        this.concessoes = concessoes;
        this.planos = planos;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (!propriedades.semeadura().habilitada()) {
            return;
        }
        semear();
    }

    @Transactional
    void semear() {
        boolean redefinir = "true".equals(ambiente.getProperty("INSTADINO_SEMEADURA_REDEFINIR"));
        Instant agora = relogio.instant();
        List<String> linhas = new ArrayList<>();
        for (ContaDeDemonstracao d : CONTAS) {
            var existente = contas.findByEmailNormalizado(d.email());
            if (existente.isPresent() && !redefinir) {
                continue;
            }
            String senha = senhaPara(d);
            Conta conta;
            if (existente.isPresent()) {
                conta = existente.get();
                conta.definirHashSenha(codificador.encode(senha), agora);
                // salvar explicitamente: semear() é chamado de dentro de run(), então @Transactional não se aplica e a alteração
                // da entidade não seria gravada (o arquivo mostraria uma senha que o servidor não aceita)
                contas.save(conta);
                auditoria.sistema("SEMEADURA_SENHA_REDEFINIDA", conta.getId().toString(), d.chave());
            } else {
                conta = contas.save(new Conta(d.email(), d.email(), d.nome(), codificador.encode(senha), d.papel(), true, agora));
                auditoria.sistema("SEMEADURA_CONTA_CRIADA", conta.getId().toString(), d.chave());
                if (d.premiumPorConcessao()) {
                    concessoes.save(new Concessao(conta.getId(), "CONCESSAO_ADMINISTRATIVA", agora, null, null,
                            "Conta de demonstração do sandbox: cliente Premium"));
                }
            }
            linhas.add(String.format("%-8s | %-24s | %s | %s", d.chave().toLowerCase(java.util.Locale.ROOT), d.email(), senha, d.descricao()));
        }
        garantirPlanoDeTeste();
        if (!linhas.isEmpty()) {
            gravarArquivo(linhas);
            LOG.log(System.Logger.Level.INFO, "Contas de demonstração criadas ({0}). As credenciais foram gravadas em {1} (não são exibidas no log).",
                    linhas.size(), arquivo());
        }
    }

    private void garantirPlanoDeTeste() {
        if (planos.findById("premium-mensal-sandbox").isEmpty()) {
            planos.save(new Plano("premium-mensal-sandbox", "Premium mensal (PREÇO DE TESTE)",
                    "Plano de demonstração do sandbox. O preço é fictício e nenhuma cobrança real é feita.", "MENSAL", 1990, "BRL", true));
        }
    }

    private String senhaPara(ContaDeDemonstracao d) {
        String definida = ambiente.getProperty("INSTADINO_SENHA_" + d.chave());
        if (definida != null && !definida.isBlank()) {
            PoliticaDeSenha.validar(definida, d.email()).ifPresent(m -> {
                throw new IllegalStateException("A senha informada para a conta " + d.chave() + " não atende à política: " + m);
            });
            return definida;
        }
        return gerarSenha();
    }

    String gerarSenha() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private Path arquivo() {
        return propriedades.dados().resolve("contas-semeadas.txt");
    }

    private void gravarArquivo(List<String> linhas) {
        try {
            Files.createDirectories(propriedades.dados());
            String cabecalho = "Contas de demonstração do ambiente de DESENVOLVIMENTO/SANDBOX. NÃO use estas senhas em produção.\n"
                    + "Este arquivo não deve ser versionado nem compartilhado.\n\n";
            Path alvo = arquivo();
            boolean novo = !Files.exists(alvo);
            Files.writeString(alvo, (novo ? cabecalho : "") + String.join("\n", linhas) + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            try {
                Files.setPosixFilePermissions(alvo, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                // Windows: depende das permissões da pasta do usuário
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar o arquivo de contas de demonstração.");
        }
    }
}
