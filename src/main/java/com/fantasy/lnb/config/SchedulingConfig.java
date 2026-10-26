package com.fantasy.lnb.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Los cron jobs se pueden apagar con APP_SCHEDULING_ENABLED=false (staging),
// así el servicio no genera tráfico propio y Railway lo puede dormir.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
