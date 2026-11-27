package com.colegio.api.recuperacion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colegio.api.recuperacion.dto.EnviarCodigoRequest;
import com.colegio.api.recuperacion.dto.MensajeResponse;
import com.colegio.api.recuperacion.dto.RestablecerRequest;
import com.colegio.api.recuperacion.dto.TokenTemporalResponse;
import com.colegio.api.recuperacion.dto.VerificarCodigoRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/recuperacion")
public class RecuperacionController {

    private static final String MENSAJE_GENERICO =
            "Si los datos coinciden con una cuenta activa, recibirás un código por SMS en unos segundos.";

    private final RecuperacionService servicio;
    private final RecuperacionProperties props;

    public RecuperacionController(RecuperacionService servicio, RecuperacionProperties props) {
        this.servicio = servicio;
        this.props = props;
    }

    @PostMapping("/enviar-codigo")
    public ResponseEntity<MensajeResponse> enviarCodigo(
            @Valid @RequestBody EnviarCodigoRequest body,
            HttpServletRequest req) {
        servicio.enviarCodigo(body.correo(), body.celular(), req.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MensajeResponse(MENSAJE_GENERICO));
    }

    @PostMapping("/verificar-codigo")
    public ResponseEntity<TokenTemporalResponse> verificarCodigo(
            @Valid @RequestBody VerificarCodigoRequest body,
            HttpServletRequest req) {
        String token = servicio.verificarCodigo(body.celular(), body.codigo(), req.getRemoteAddr());
        return ResponseEntity.ok(new TokenTemporalResponse(token, props.tokenMinutos() * 60));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<MensajeResponse> restablecer(@Valid @RequestBody RestablecerRequest body) {
        servicio.restablecer(body.token(), body.nuevaContrasena());
        return ResponseEntity.ok(new MensajeResponse("Contraseña actualizada. Ya puedes iniciar sesión."));
    }
}
