package com.colegio.api.recuperacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.type.PhoneNumber;

@Service
public class TwilioSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);
    private final TwilioProperties props;

    public TwilioSmsService(TwilioProperties props) {
        this.props = props;
    }

    @Override
    public boolean usaVerify() {
        return props.usaVerify();
    }

    @Override
    public void enviar(String destinoE164, String mensaje) {
        if (!props.usaSmsDirecto()) {
            log.warn("Twilio SMS directo no configurado: se omitió el envío de OTP a {}.", destinoE164);
            return;
        }

        try {
            Message.creator(
                    new PhoneNumber(destinoE164),
                    new PhoneNumber(props.fromNumber()),
                    mensaje).create();
        } catch (Exception ex) {
            log.error("Error enviando SMS de recuperación: {}", ex.getMessage());
        }
    }

    @Override
    public void enviarCodigoVerify(String destinoE164) {
        if (!props.usaVerify()) {
            log.warn("Twilio Verify no configurado: se omitió el envío de OTP a {}.", destinoE164);
            return;
        }

        try {
            Verification.creator(props.verifyServiceSid(), destinoE164, "sms").create();
        } catch (Exception ex) {
            log.error("Error enviando verificación SMS con Twilio Verify: {}", ex.getMessage());
        }
    }

    @Override
    public boolean verificarCodigoVerify(String destinoE164, String codigo) {
        if (!props.usaVerify()) {
            return false;
        }

        try {
            VerificationCheck check = VerificationCheck.creator(props.verifyServiceSid())
                    .setTo(destinoE164)
                    .setCode(codigo)
                    .create();
            return "approved".equalsIgnoreCase(check.getStatus());
        } catch (Exception ex) {
            log.warn("Error verificando código con Twilio Verify: {}", ex.getMessage());
            return false;
        }
    }
}
