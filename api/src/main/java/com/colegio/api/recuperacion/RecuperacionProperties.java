package com.colegio.api.recuperacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recuperacion")
public record RecuperacionProperties(
        String pepper,
        int otpMinutos,
        int tokenMinutos,
        int cooldownSegundos,
        int maxIntentos,
        int limitePorIp,
        int limitePorCelular,
        int ventanaMinutos) {
}
