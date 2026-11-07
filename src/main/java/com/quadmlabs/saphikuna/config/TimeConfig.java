package com.quadmlabs.saphikuna.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /** Reloj de la aplicación (zona horaria del servidor). Se inyecta para poder probar fechas. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
