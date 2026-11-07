package com.quadmlabs.saphikuna.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

/**
 * Seguridad: todo el panel pide sesión de administrador. Solo son públicos el inicio de
 * sesión, el catálogo (/catalogo y /catalogo.pdf, que se comparte con los clientes) y los
 * archivos estáticos.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Cuánto dura la sesión recordada ("Recordarme"). */
    static final Duration REMEMBER_FOR = Duration.ofDays(30);

    @Bean
    static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminAccountService accounts,
            @Value("${app.security.remember-key}") String rememberKey) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/catalogo", "/catalogo.pdf",
                                "/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico")
                        .permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", false)
                        .failureUrl("/login?error"))
                .rememberMe(remember -> remember
                        .key(rememberKey)
                        .userDetailsService(accounts)
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds((int) REMEMBER_FOR.toSeconds()))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID", "remember-me"));

        return http.build();
    }
}
