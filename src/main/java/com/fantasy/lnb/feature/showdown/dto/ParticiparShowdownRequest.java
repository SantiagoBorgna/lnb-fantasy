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
    @jakarta.validation.constraints.Email
    private String email;
    
    @NotNull
    private Long capitanId;
    
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
