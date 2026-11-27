package com.colegio.api.recuperacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(
        String accountSid,
        String authToken,
        String fromNumber,
        String verifyServiceSid) {

    public boolean tieneCredenciales() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank();
    }

    public boolean usaVerify() {
        return tieneCredenciales() && verifyServiceSid != null && !verifyServiceSid.isBlank();
    }

    public boolean usaSmsDirecto() {
        return tieneCredenciales() && fromNumber != null && !fromNumber.isBlank();
    }

    public boolean estaConfigurado() {
        return usaVerify() || usaSmsDirecto();
    }
}
