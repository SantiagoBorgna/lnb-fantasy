package com.fantasy.lnb.feature.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ── Request: paso 1 del onboarding ───────────────────────────────────────────
// El usuario elige su equipo favorito y el nombre de su equipo virtual
@Data
public class CompletarPerfilRequest {

    @NotNull(message = "El equipo favorito es obligatorio.")
    private Long equipoFavoritoId;

    @NotBlank(message = "El nombre del equipo virtual es obligatorio.")
    @Size(min = 3, max = 30, message = "El nombre debe tener entre 3 y 30 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ-]+$", message = "El nombre del equipo contiene caracteres no permitidos")
    private String nombreEquipoVirtual;
}