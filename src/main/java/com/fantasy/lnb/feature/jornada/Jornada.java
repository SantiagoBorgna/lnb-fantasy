package com.fantasy.lnb.feature.jornada;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Jornada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // También vacía. En el Módulo 4 le pondremos las fechas de inicio, fin y
    // estado.
}