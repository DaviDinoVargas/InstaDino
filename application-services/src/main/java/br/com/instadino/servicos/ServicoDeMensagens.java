package br.com.instadino.servicos;

import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDeConsentimentos;
import br.com.instadino.persistencia.repositorio.RepositorioDeConversas;
import br.com.instadino.persistencia.repositorio.RepositorioDeConversas.Conversa;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Módulo opcional de mensagens (Premium + consentimento explícito). Trabalha somente com agregados — quantidade por
 * conversa e por mês, mídias e datas. O texto das mensagens nunca é lido para o banco, e este serviço não oferece
 * nenhuma operação que o devolva, nem para relatórios.
 *
 * <p>Ler exige plano e consentimento. Revogar o consentimento e apagar os dados do módulo é sempre permitido,
 * inclusive depois de o plano expirar.
 */
public final class ServicoDeMensagens {

    public static final String LIMITACAO = "O InstaDino guarda apenas contagens e datas por conversa. O texto das mensagens não é lido para o banco "
            + "local, não aparece em relatórios e não é enviado a lugar nenhum.";

    /** Mensagens de um mês; {@code enviadas} é nulo quando o titular da conta não pôde ser identificado. */
    public record Mes(YearMonth mes, int total, Integer enviadas) {
    }

    public record Contato(String titulo, boolean grupo, int total, Integer enviadas, int midias, Instant primeira, Instant ultima) {
    }

    public record Resumo(int conversas, int grupos, long mensagens, Long enviadas, long midias, Instant primeira, Instant ultima,
                         int mesesComMensagens) {
        public Long recebidas() {
            return enviadas == null ? null : mensagens - enviadas;
        }
    }

    private final PoliticaDeAcesso politica;
    private final RepositorioDeConsentimentos consentimentos;
    private final RepositorioDeImportacoes importacoes;
    private final RepositorioDeConversas conversas;
    private final SessaoLocal sessao;
    private final RegistroDeAuditoriaLocal auditoria;

    ServicoDeMensagens(PoliticaDeAcesso politica, RepositorioDeConsentimentos consentimentos, RepositorioDeImportacoes importacoes,
                       RepositorioDeConversas conversas, SessaoLocal sessao, RegistroDeAuditoriaLocal auditoria) {
        this.politica = politica;
        this.consentimentos = consentimentos;
        this.importacoes = importacoes;
        this.conversas = conversas;
        this.sessao = sessao;
        this.auditoria = auditoria;
    }

    public boolean consentimentoConcedido() {
        return consentimentos.concedido(Funcionalidade.ARQUIVO_DE_MENSAGENS);
    }

    /** O módulo pode ser lido agora (plano Premium e consentimento). */
    public boolean disponivel() {
        return politica.permite(Funcionalidade.ARQUIVO_DE_MENSAGENS);
    }

    /** Registra o consentimento. Só o Premium pode habilitar o módulo. */
    public void conceder() {
        if (politica.planoEfetivo() != Plano.PREMIUM) {
            throw new AcessoNegadoException(Funcionalidade.ARQUIVO_DE_MENSAGENS, AcessoNegadoException.Motivo.PLANO_INSUFICIENTE);
        }
        consentimentos.conceder(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        auditoria.registrar("CONSENTIMENTO_MENSAGENS_CONCEDIDO", "módulo habilitado");
    }

    /** Revoga o consentimento e apaga os agregados de mensagens de todas as importações. Devolve quantas conversas foram apagadas. */
    public int revogarEApagar() {
        consentimentos.revogar(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        int apagadas = conversas.excluirTodas();
        auditoria.registrar("CONSENTIMENTO_MENSAGENS_REVOGADO", apagadas + " conversas apagadas");
        return apagadas;
    }

    /** Apaga os dados sem revogar o consentimento (por exemplo, antes de uma nova importação). */
    public int apagarDados() {
        int apagadas = conversas.excluirTodas();
        auditoria.registrar("MENSAGENS_APAGADAS", apagadas + " conversas apagadas");
        return apagadas;
    }

    public boolean temDados() {
        return disponivel() && ultima().map(i -> conversas.contar(i.id()) > 0).orElse(false);
    }

    private Optional<ResumoDaImportacao> ultima() {
        politica.exigir(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        return importacoes.maisRecente(sessao.perfilAtivo());
    }

    public Optional<Resumo> resumo() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return Optional.empty();
        }
        List<Conversa> todas = conversas.listar(i.get().id(), RepositorioDeConversas.TAMANHO_MAXIMO);
        if (todas.isEmpty()) {
            return Optional.empty();
        }
        long total = 0;
        long enviadas = 0;
        long midias = 0;
        int grupos = 0;
        boolean titular = true;
        Long primeira = null;
        Long ultima = null;
        for (Conversa c : todas) {
            total += c.total();
            midias += c.midias();
            grupos += c.grupo() ? 1 : 0;
            titular &= c.titularIdentificado();
            enviadas += Math.max(c.enviadas(), 0);
            primeira = c.primeiro() == null ? primeira : primeira == null ? c.primeiro() : Math.min(primeira, c.primeiro());
            ultima = c.ultimo() == null ? ultima : ultima == null ? c.ultimo() : Math.max(ultima, c.ultimo());
        }
        // a lista é limitada às 1000 maiores conversas; os totais vêm do mês, que cobre todas
        List<Mes> meses = porMes();
        long totalGeral = meses.stream().mapToLong(Mes::total).sum();
        Long enviadasGeral = meses.stream().allMatch(m -> m.enviadas() != null) ? meses.stream().mapToLong(Mes::enviadas).sum() : null;
        return Optional.of(new Resumo(conversas.contar(i.get().id()), grupos, totalGeral == 0 ? total : totalGeral, titular ? enviadasGeral : null,
                midias, primeira == null ? null : Instant.ofEpochSecond(primeira), ultima == null ? null : Instant.ofEpochSecond(ultima),
                meses.size()));
    }

    /** Conversas mais movimentadas. Grupos podem ser omitidos para listar só contatos individuais. */
    public List<Contato> ranking(int limite, boolean incluirGrupos) {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        return conversas.listar(i.get().id(), RepositorioDeConversas.TAMANHO_MAXIMO).stream()
                .filter(c -> incluirGrupos || !c.grupo())
                .limit(Math.max(1, limite))
                .map(c -> new Contato(c.titulo(), c.grupo(), c.total(), c.titularIdentificado() ? c.enviadas() : null, c.midias(),
                        c.primeiro() == null ? null : Instant.ofEpochSecond(c.primeiro()),
                        c.ultimo() == null ? null : Instant.ofEpochSecond(c.ultimo())))
                .toList();
    }

    public List<Mes> porMes() {
        Optional<ResumoDaImportacao> i = ultima();
        if (i.isEmpty()) {
            return List.of();
        }
        return conversas.porMes(i.get().id()).stream()
                .map(m -> new Mes(YearMonth.parse(m.mes()), m.total(), m.enviadas() < 0 ? null : m.enviadas()))
                .toList();
    }
}
