package br.com.instadino.desktop.util;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação consistente em português do Brasil para números, datas e tamanhos. */
public final class Formatos {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);

    private Formatos() {
    }

    public static String inteiro(long valor) {
        return NumberFormat.getIntegerInstance(PT_BR).format(valor);
    }

    public static String decimal(double valor) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(valor);
    }

    public static String dataHora(Instant instante) {
        return instante == null ? "—" : DATA_HORA.format(instante.atZone(ZoneId.systemDefault()));
    }

    public static String data(Instant instante) {
        return instante == null ? "—" : DATA.format(instante.atZone(ZoneId.systemDefault()));
    }

    public static String bytes(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        if (bytes < 1024) {
            return inteiro(bytes) + " B";
        }
        double valor = bytes;
        String[] unidades = {"KiB", "MiB", "GiB", "TiB"};
        int i = -1;
        while (valor >= 1024 && i < unidades.length - 1) {
            valor /= 1024;
            i++;
        }
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(1);
        return f.format(valor) + " " + unidades[i];
    }
}
