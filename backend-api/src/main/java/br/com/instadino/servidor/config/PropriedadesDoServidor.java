package br.com.instadino.servidor.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Propriedades {@code instadino.*}. Nenhum valor secreto tem padrão no código. */
@ConfigurationProperties(prefix = "instadino")
public record PropriedadesDoServidor(
        @DefaultValue("./dados-backend") Path dados,
        @DefaultValue Sessao sessao,
        @DefaultValue Licenca licenca,
        @DefaultValue Seguranca seguranca,
        @DefaultValue Semeadura semeadura) {

    public record Sessao(@DefaultValue("14") int validadeEmDias) {
    }

    /**
     * @param chavePrivadaArquivo arquivo PEM (PKCS#8) com a chave Ed25519 de assinatura; fica FORA do repositório
     * @param kid identificador da chave pública correspondente embutida no desktop
     */
    public record Licenca(@DefaultValue("7") int validadeEmDias, @DefaultValue("7") int toleranciaOfflineEmDias,
                          @DefaultValue("3") int limiteDeDispositivos, Path chavePrivadaArquivo, @DefaultValue("k1") String kid) {
    }

    /**
     * @param confiarEmProxy usar X-Forwarded-For para identificar o cliente (somente atrás de proxy confiável)
     * @param tlsTerminadoNoProxy reconhece que o TLS é terminado por um proxy confiável (exigido em produção)
     */
    public record Seguranca(@DefaultValue("false") boolean confiarEmProxy, @DefaultValue("false") boolean tlsTerminadoNoProxy,
                            @DefaultValue("30") int limiteDeRequisicoesPorJanela, @DefaultValue("5") int janelaEmMinutos,
                            @DefaultValue("5") int falhasAteBloquear, @DefaultValue("15") int bloqueioBaseEmMinutos,
                            @DefaultValue("false") boolean emailRealConfigurado) {
    }

    /** Semeadura de contas de demonstração (somente fora de produção). */
    public record Semeadura(@DefaultValue("false") boolean habilitada) {
    }
}
