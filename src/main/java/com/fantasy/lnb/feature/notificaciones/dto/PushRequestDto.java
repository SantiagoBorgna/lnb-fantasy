package com.fantasy.lnb.feature.notificaciones.dto;

import lombok.Data;

@Data
public class PushRequestDto {
    private String endpoint;
    private Keys keys;

    @Data
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}