package com.quadmlabs.saphikuna.security;

import com.quadmlabs.saphikuna.entity.Admin;
import com.quadmlabs.saphikuna.repository.AdminRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuentas de administrador: alta, edición, cambio de clave y bloqueo temporal por intentos
 * fallidos. También es quien carga al usuario para Spring Security.
 */
@Service
public class AdminAccountService implements UserDetailsService {

    /** Intentos fallidos seguidos antes de bloquear, y cuánto dura el bloqueo. */
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    /** Error de regla de negocio; {@code getMessage()} es la clave del mensaje. */
    public static class AccountException extends RuntimeException {
        private final String field;

        public AccountException(String field, String messageKey) {
            super(messageKey);
            this.field = field;
        }

        /** Campo del formulario al que pertenece el error; null si es general. */
        public String field() {
            return field;
        }
    }

    private final AdminRepository admins;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminAccountService(AdminRepository admins, PasswordEncoder encoder, Clock clock) {
        this.admins = admins;
        this.encoder = encoder;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Spring Security
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = admins.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("usuario no encontrado"));
        return AdminPrincipal.of(admin, now());
    }

    /** Cuenta un intento fallido; al llegar al máximo bloquea la cuenta por un rato. */
    @Transactional
    public void recordFailure(String username) {
        admins.findByUsername(normalize(username)).ifPresent(admin -> {
            int attempts = admin.getFailedAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                admin.setLockedUntil(now().plus(LOCK_TIME));
                attempts = 0;
            }
            admin.setFailedAttempts(attempts);
        });
    }

    @Transactional
    public void recordSuccess(String username) {
        admins.findByUsername(normalize(username)).ifPresent(admin -> {
            admin.setFailedAttempts(0);
            admin.setLockedUntil(null);
            admin.setLastLoginAt(now());
        });
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Admin> findAll() {
        return admins.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Admin> find(UUID id) {
        return admins.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return admins.count() == 0;
    }

    // ------------------------------------------------------------------
    // Alta y edición
    // ------------------------------------------------------------------

    @Transactional
    public Admin create(String username, String displayName, String rawPassword) {
        String name = normalize(username);
        if (admins.existsByUsername(name)) {
            throw new AccountException("username", "admins.validation.username.taken");
        }
        Admin admin = new Admin();
        admin.setAdminId(UUID.randomUUID());
        admin.setUsername(name);
        admin.setDisplayName(displayName.trim());
        admin.setPasswordHash(encoder.encode(rawPassword));
        admin.setEnabled(true);
        admin.setCreatedAt(now());
        return admins.save(admin);
    }

    /**
     * Cambia el nombre, activa o desactiva la cuenta y, si se indica, pone una clave nueva.
     * No se puede desactivar la propia cuenta ni dejar el panel sin ningún administrador activo.
     */
    @Transactional
    public Admin update(UUID id, String displayName, boolean enabled, String newPassword, UUID currentAdminId) {
        Admin admin = admins.findById(id).orElseThrow(() -> new AccountException(null, "admins.notfound"));

        if (admin.isEnabled() && !enabled) {
            if (id.equals(currentAdminId)) {
                throw new AccountException("enabled", "admins.validation.disable.self");
            }
            if (admins.countByEnabledTrue() <= 1) {
                throw new AccountException("enabled", "admins.validation.disable.last");
            }
        }
        admin.setDisplayName(displayName.trim());
        admin.setEnabled(enabled);
        if (newPassword != null && !newPassword.isBlank()) {
            admin.setPasswordHash(encoder.encode(newPassword));
            admin.setFailedAttempts(0);
            admin.setLockedUntil(null);
        }
        return admin;
    }

    /** Cambio de clave propio: exige la clave actual. */
    @Transactional
    public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
        Admin admin = admins.findById(id).orElseThrow(() -> new AccountException(null, "admins.notfound"));
        if (currentPassword == null || !encoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new AccountException("currentPassword", "profile.validation.current.wrong");
        }
        admin.setPasswordHash(encoder.encode(newPassword));
    }

    @Transactional
    public void changeOwnName(UUID id, String displayName) {
        Admin admin = admins.findById(id).orElseThrow(() -> new AccountException(null, "admins.notfound"));
        admin.setDisplayName(displayName.trim());
    }

    // ------------------------------------------------------------------

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
