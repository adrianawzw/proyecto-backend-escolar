package com.colegio.api.recuperacion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EnviarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El formato del correo es inválido.")
        String correo,
        @NotBlank(message = "El celular es obligatorio.")
        String celular) {
}
