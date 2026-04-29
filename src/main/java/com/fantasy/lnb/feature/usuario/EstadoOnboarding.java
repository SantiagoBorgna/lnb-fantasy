package com.fantasy.lnb.feature.usuario;

public enum EstadoOnboarding {
    NUEVO, // Recién registrado — debe completar perfil
    PERFIL_COMPLETO, // Eligió equipo favorito y nombre de equipo virtual
    ACTIVO // Armó su primer plantel — acceso completo a la app
}