package com.fantasy.lnb.scraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TirosEstatDto {

    @JsonProperty("Aciertos")
    private Integer aciertos;

    @JsonProperty("Fallados")
    private Integer fallados;

    @JsonProperty("Totales")
    private Integer totales;
}