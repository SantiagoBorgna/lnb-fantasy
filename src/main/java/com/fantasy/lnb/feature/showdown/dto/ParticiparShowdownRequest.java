package com.fantasy.lnb.feature.showdown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ParticiparShowdownRequest {
    
    @NotBlank
    private String nombre;
    
    @NotBlank
    private String apellido;
    
    @NotBlank
    private String uuidDispositivo;

    @NotNull
    private Long baseId;

    @NotNull
    private Long escoltaId;

    @NotNull
    private Long aleroId;

    @NotNull
    private Long alapivotId;

    @NotNull
    private Long pivotId;
}
