package com.colegio.api.recuperacion;

import java.util.regex.Pattern;

public final class TelefonoUtil {

    private static final Pattern E164_PE = Pattern.compile("^\\+519\\d{8}$");

    private TelefonoUtil() {
    }

    public static String normalizarPeru(String entrada) {
        if (entrada == null) {
            throw new RecuperacionException(400, "Ingresa un celular peruano válido de 9 dígitos que empiece con 9.");
        }

        String digitos = entrada.replaceAll("[\\s\\-()]", "");
        if (digitos.startsWith("+")) {
            digitos = digitos.substring(1);
        } else if (digitos.startsWith("00")) {
            digitos = digitos.substring(2);
        }

        if (digitos.length() == 9) {
            digitos = "51" + digitos;
        }

        String e164 = "+" + digitos;
        if (!E164_PE.matcher(e164).matches()) {
            throw new RecuperacionException(400, "Ingresa un celular peruano válido de 9 dígitos que empiece con 9.");
        }

        return e164;
    }
}
