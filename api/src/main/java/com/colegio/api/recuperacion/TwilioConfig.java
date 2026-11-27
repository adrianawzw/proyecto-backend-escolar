package com.colegio.api.recuperacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.twilio.Twilio;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties({ TwilioProperties.class, RecuperacionProperties.class })
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);
    private final TwilioProperties props;

    public TwilioConfig(TwilioProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.estaConfigurado()) {
            Twilio.init(props.accountSid(), props.authToken());
            log.info("Twilio inicializado en modo {}.", props.usaVerify() ? "Verify" : "SMS directo");
        } else {
            log.warn("Twilio SMS no configurado: define TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_VERIFY_SERVICE_SID (o TWILIO_FROM_NUMBER) para enviar OTP reales.");
        }
    }
}
