package com.colegio.api.recuperacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colegio.api.models.Usuario;
import com.colegio.api.repositories.UsuarioRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class RecuperacionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final RateLimiterService rateLimiter;
    private final SmsService sms;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacionProperties props;
    private final Cache<String, RegistroOtp> codigos;
    private final Cache<String, RegistroToken> tokens;

    public RecuperacionService(
            UsuarioRepository usuarios,
            RateLimiterService rateLimiter,
            SmsService sms,
            PasswordEncoder passwordEncoder,
            RecuperacionProperties props) {
        this.usuarios = usuarios;
        this.rateLimiter = rateLimiter;
        this.sms = sms;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.codigos = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(Math.max(props.otpMinutos(), 1) + 5L))
                .build();
        this.tokens = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(Math.max(props.tokenMinutos(), 1) + 5L))
                .build();
    }

    @Transactional(readOnly = true)
    public void enviarCodigo(String correoEntrada, String celularEntrada, String ip) {
        rateLimiter.verificarIp(ip);
        String correo = correoEntrada.trim().toLowerCase(Locale.ROOT);
        String celular = TelefonoUtil.normalizarPeru(celularEntrada);
        rateLimiter.verificarCelular(celular);

        Instant ahora = Instant.now();
        RegistroOtp previo = codigos.getIfPresent(celular);
        if (previo != null) {
            long transcurrido = Duration.between(previo.creadoEn(), ahora).toSeconds();
            if (transcurrido < props.cooldownSegundos()) {
                long restante = props.cooldownSegundos() - transcurrido;
                throw new RecuperacionException(
                        429,
                        "Espera " + restante + " s para reenviar el código.",
                        restante);
            }
        }

        Optional<Usuario> usuario = usuarios.findByEmailIgnoreCase(correo)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()));
        if (usuario.isEmpty()) {
            return;
        }

        UUID usuarioId = usuario.get().getId();
        codigos.asMap().values().removeIf(registro -> registro.usuarioId().equals(usuarioId));

        if (sms.usaVerify()) {
            RegistroOtp registro = new RegistroOtp(
                    usuarioId,
                    celular,
                    "",
                    0,
                    false,
                    ahora,
                    ahora.plus(Duration.ofMinutes(props.otpMinutos())));
            codigos.put(celular, registro);
            sms.enviarCodigoVerify(celular);
            return;
        }

        String otp = generarOtp();
        RegistroOtp registro = new RegistroOtp(
                usuarioId,
                celular,
                hmac(otp + ":" + celular),
                0,
                false,
                ahora,
                ahora.plus(Duration.ofMinutes(props.otpMinutos())));
        codigos.put(celular, registro);

        sms.enviar(
                celular,
                "Liceo Santo Toribio: tu código de recuperación es " + otp
                        + ". Vence en " + props.otpMinutos() + " minutos. No lo compartas.");
    }

    public String verificarCodigo(String celularEntrada, String codigo, String ip) {
        rateLimiter.verificarIp(ip);
        String celular = TelefonoUtil.normalizarPeru(celularEntrada);
        Instant ahora = Instant.now();

        boolean aprobadoVerify = false;
        String codigoHashEsperado = null;

        if (sms.usaVerify()) {
            RegistroOtp previo = codigos.getIfPresent(celular);
            if (previo == null || previo.consumido() || !previo.expiraEn().isAfter(ahora)
                    || previo.intentos() >= props.maxIntentos()) {
                throw new RecuperacionException(401, "Código inválido o expirado.");
            }
            aprobadoVerify = sms.verificarCodigoVerify(celular, codigo);
        } else {
            codigoHashEsperado = hmac(codigo + ":" + celular);
        }

        final boolean usaVerify = sms.usaVerify();
        final boolean verificadoConTwilio = aprobadoVerify;
        final String hashEsperado = codigoHashEsperado;
        AtomicReference<UUID> usuarioVerificado = new AtomicReference<>();

        codigos.asMap().compute(celular, (clave, actual) -> {
            if (actual == null || actual.consumido() || !actual.expiraEn().isAfter(ahora)) {
                return actual;
            }

            if (actual.intentos() >= props.maxIntentos()) {
                return new RegistroOtp(
                        actual.usuarioId(),
                        actual.celular(),
                        actual.codigoHash(),
                        actual.intentos(),
                        true,
                        actual.creadoEn(),
                        actual.expiraEn());
            }

            boolean coincide = usaVerify
                    ? verificadoConTwilio
                    : MessageDigest.isEqual(
                            actual.codigoHash().getBytes(StandardCharsets.UTF_8),
                            hashEsperado.getBytes(StandardCharsets.UTF_8));

            if (!coincide) {
                int nuevosIntentos = actual.intentos() + 1;
                boolean agotado = nuevosIntentos >= props.maxIntentos();
                return new RegistroOtp(
                        actual.usuarioId(),
                        actual.celular(),
                        actual.codigoHash(),
                        nuevosIntentos,
                        agotado,
                        actual.creadoEn(),
                        actual.expiraEn());
            }

            usuarioVerificado.set(actual.usuarioId());
            return new RegistroOtp(
                    actual.usuarioId(),
                    actual.celular(),
                    actual.codigoHash(),
                    actual.intentos(),
                    true,
                    actual.creadoEn(),
                    actual.expiraEn());
        });

        UUID usuarioId = usuarioVerificado.get();
        if (usuarioId == null) {
            throw new RecuperacionException(401, "Código inválido o expirado.");
        }

        String tokenPlano = generarToken();
        RegistroToken token = new RegistroToken(
                usuarioId,
                celular,
                ahora.plus(Duration.ofMinutes(props.tokenMinutos())));
        tokens.put(sha256(tokenPlano), token);

        return tokenPlano;
    }

    @Transactional
    public void restablecer(String tokenPlano, String nuevaContrasena) {
        RegistroToken registro = tokens.asMap().remove(sha256(tokenPlano));
        if (registro == null || registro.expiraEn().isBefore(Instant.now())) {
            throw new RecuperacionException(401, "El enlace de restablecimiento no es válido o expiró.");
        }

        Usuario usuario = usuarios.buscarPorIdParaActualizar(registro.usuarioId())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new RecuperacionException(
                        401, "El enlace de restablecimiento no es válido o expiró."));

        usuario.setPassword(passwordEncoder.encode(nuevaContrasena));
        usuario.setVersionSesion(UUID.randomUUID());
        usuarios.save(usuario);

        codigos.asMap().values().removeIf(item -> item.usuarioId().equals(usuario.getId()));
    }

    private String generarOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hmac(String dato) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.pepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dato.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular HMAC", ex);
        }
    }

    private String sha256(String dato) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(dato.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular SHA-256", ex);
        }
    }

    private record RegistroOtp(
            UUID usuarioId,
            String celular,
            String codigoHash,
            int intentos,
            boolean consumido,
            Instant creadoEn,
            Instant expiraEn) {
    }

    private record RegistroToken(
            UUID usuarioId,
            String celular,
            Instant expiraEn) {
    }
}
