package com.fantasy.lnb.feature.mercado.dto;

import lombok.Data;

@Data
public class WaiverClaimRequest {
    private Long torneoId;
    private Long jugadorEntranteId;
    private Long jugadorSalienteId;
    private Long dtEntranteId;
    private Long dtSalienteId;
}
