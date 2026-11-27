package com.colegio.api.recuperacion;

public interface SmsService {
    boolean usaVerify();

    void enviar(String destinoE164, String mensaje);

    void enviarCodigoVerify(String destinoE164);

    boolean verificarCodigoVerify(String destinoE164, String codigo);
}
