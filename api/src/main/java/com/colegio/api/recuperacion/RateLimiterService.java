package com.colegio.api.recuperacion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class RateLimiterService {

    private final Cache<String, Deque<Instant>> eventos;
    private final Duration ventana;
    private final RecuperacionProperties props;

    public RateLimiterService(RecuperacionProperties props) {
        this.props = props;
        this.ventana = Duration.ofMinutes(props.ventanaMinutos());
        this.eventos = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(props.ventanaMinutos() + 5L))
                .build();
    }

    public void verificarIp(String ip) {
        consumir("ip:" + ip, props.limitePorIp());
    }

    public void verificarCelular(String celular) {
        consumir("cel:" + celular, props.limitePorCelular());
    }

    private void consumir(String clave, int limite) {
        Instant ahora = Instant.now();
        Deque<Instant> cola = eventos.get(clave, k -> new ArrayDeque<>());

        synchronized (cola) {
            Instant corte = ahora.minus(ventana);
            while (!cola.isEmpty() && cola.peekFirst().isBefore(corte)) {
                cola.pollFirst();
            }

            if (cola.size() >= limite) {
                throw new RecuperacionException(429, "Demasiadas solicitudes. Inténtalo más tarde.", 60);
            }

            cola.addLast(ahora);
        }
    }
}
