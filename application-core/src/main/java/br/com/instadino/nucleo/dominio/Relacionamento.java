package br.com.instadino.nucleo.dominio;

import java.util.Objects;

/**
 * Uma conta em uma lista de relacionamentos. {@code instanteEmSegundos} é o carimbo de tempo presente na
 * exportação (pode ser nulo, por exemplo em arquivos HTML).
 */
public record Relacionamento(TipoDeRelacionamento tipo, NomeDeUsuario usuario, Long instanteEmSegundos) {

    public Relacionamento {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(usuario, "usuario");
    }
}
