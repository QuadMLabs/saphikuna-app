package com.quadmlabs.saphikuna.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** Lleva la cuenta de intentos fallidos para el bloqueo temporal. */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final AdminAccountService accounts;

    public LoginAttemptListener(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        try {
            accounts.recordFailure(username);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el intento fallido: {}", e.getMessage());
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        // Solo el inicio de sesión con usuario y clave (no otros tipos de autenticación)
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            try {
                accounts.recordSuccess(event.getAuthentication().getName());
            } catch (RuntimeException e) {
                log.warn("No se pudo registrar el ingreso: {}", e.getMessage());
            }
        }
    }
}
