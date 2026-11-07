package com.quadmlabs.saphikuna.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Crea el primer administrador si la tabla está vacía, para no quedar fuera del panel.
 * Si no se configuró una clave, genera una al azar y la muestra UNA vez en el log.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AdminAccountService accounts;
    private final String username;
    private final String password;

    public AdminBootstrap(
            AdminAccountService accounts,
            @Value("${app.bootstrap.username:admin}") String username,
            @Value("${app.bootstrap.password:}") String password) {
        this.accounts = accounts;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!accounts.isEmpty()) {
                return;
            }
            boolean generated = password == null || password.isBlank();
            String raw = generated ? randomPassword() : password;

            accounts.create(username, "Administrador", raw);

            if (generated) {
                log.warn("""

                        ================================================================
                         Se creó el primer administrador.
                           Usuario: {}
                           Clave temporal: {}
                         Entra y cámbiala en "Mi perfil". Esta clave no se vuelve a mostrar.
                        ================================================================""", username, raw);
            } else {
                log.info("Se creó el primer administrador '{}' con la clave configurada.", username);
            }
        } catch (RuntimeException e) {
            // Lo más probable: aún no se ejecutó db/006_admins.sql
            log.error("No se pudo revisar ni crear el administrador inicial (¿se ejecutó db/006_admins.sql?): {}",
                    e.getMessage());
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
