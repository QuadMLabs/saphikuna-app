package com.quadmlabs.saphikuna.security;

import com.quadmlabs.saphikuna.entity.Admin;
import com.quadmlabs.saphikuna.repository.AdminRepository;
import com.quadmlabs.saphikuna.security.AdminAccountService.AccountException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de las cuentas: bloqueo por intentos, último administrador, claves con BCrypt. */
class AdminAccountServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneOffset.UTC);

    private AdminRepository repo;
    private PasswordEncoder encoder;
    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        repo = mock(AdminRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new AdminAccountService(repo, encoder, CLOCK);
        when(repo.save(any(Admin.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Admin admin(String username) {
        Admin a = new Admin();
        a.setAdminId(UUID.randomUUID());
        a.setUsername(username);
        a.setDisplayName(username);
        a.setPasswordHash(encoder.encode("clave-vieja"));
        a.setEnabled(true);
        when(repo.findByUsername(username)).thenReturn(Optional.of(a));
        when(repo.findById(a.getAdminId())).thenReturn(Optional.of(a));
        return a;
    }

    // ---------------- alta ----------------

    @Test
    void createStoresLowercaseUsernameAndABcryptHash() {
        Admin created = service.create("  Maria.Lopez ", " María ", "clave-segura");

        assertThat(created.getUsername()).isEqualTo("maria.lopez");
        assertThat(created.getDisplayName()).isEqualTo("María");
        assertThat(created.getPasswordHash()).startsWith("$2").isNotEqualTo("clave-segura");
        assertThat(encoder.matches("clave-segura", created.getPasswordHash())).isTrue();
        assertThat(created.isEnabled()).isTrue();
    }

    @Test
    void createRejectsAnExistingUsernameIgnoringCase() {
        when(repo.existsByUsername("maria")).thenReturn(true);

        assertThatThrownBy(() -> service.create("MARIA", "Otra", "clave-segura"))
                .isInstanceOf(AccountException.class)
                .hasMessage("admins.validation.username.taken")
                .extracting("field").isEqualTo("username");
    }

    // ---------------- inicio de sesión ----------------

    @Test
    void loadsAnEnabledAdminWithTheAdminRoleOnly() {
        admin("ana");

        UserDetails details = service.loadUserByUsername("Ana");

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void unknownUserIsNotFound() {
        when(repo.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nadie")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void aTemporarilyLockedAccountIsReportedAsLocked() {
        Admin a = admin("ana");
        a.setLockedUntil(OffsetDateTime.now(CLOCK).plusMinutes(5));

        assertThat(service.loadUserByUsername("ana").isAccountNonLocked()).isFalse();
    }

    @Test
    void anExpiredLockNoLongerBlocks() {
        Admin a = admin("ana");
        a.setLockedUntil(OffsetDateTime.now(CLOCK).minusMinutes(1));

        assertThat(service.loadUserByUsername("ana").isAccountNonLocked()).isTrue();
    }

    @Test
    void failuresAreCountedAndTheFifthLocksTheAccount() {
        Admin a = admin("ana");

        for (int i = 1; i < AdminAccountService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ana");
            assertThat(a.getFailedAttempts()).isEqualTo(i);
            assertThat(a.getLockedUntil()).isNull();
        }
        service.recordFailure("ana");

        assertThat(a.getLockedUntil()).isEqualTo(OffsetDateTime.now(CLOCK).plus(AdminAccountService.LOCK_TIME));
        assertThat(a.getFailedAttempts()).isZero();
    }

    @Test
    void aGoodLoginClearsTheCounterAndRecordsTheDate() {
        Admin a = admin("ana");
        a.setFailedAttempts(3);

        service.recordSuccess("ana");

        assertThat(a.getFailedAttempts()).isZero();
        assertThat(a.getLastLoginAt()).isEqualTo(OffsetDateTime.now(CLOCK));
    }

    @Test
    void failureForAnUnknownUserIsIgnored() {
        when(repo.findByUsername("nadie")).thenReturn(Optional.empty());

        service.recordFailure("nadie"); // no lanza
    }

    // ---------------- edición ----------------

    @Test
    void youCannotDisableYourOwnAccount() {
        Admin a = admin("ana");

        assertThatThrownBy(() -> service.update(a.getAdminId(), "Ana", false, null, a.getAdminId()))
                .isInstanceOf(AccountException.class)
                .hasMessage("admins.validation.disable.self");
        assertThat(a.isEnabled()).isTrue();
    }

    @Test
    void theLastActiveAdminCannotBeDisabled() {
        Admin ana = admin("ana");
        Admin beto = admin("beto");
        when(repo.countByEnabledTrue()).thenReturn(1L);

        assertThatThrownBy(() -> service.update(beto.getAdminId(), "Beto", false, null, ana.getAdminId()))
                .isInstanceOf(AccountException.class)
                .hasMessage("admins.validation.disable.last");
    }

    @Test
    void anotherAdminCanBeDisabledWhenThereAreMore() {
        Admin ana = admin("ana");
        Admin beto = admin("beto");
        when(repo.countByEnabledTrue()).thenReturn(2L);

        service.update(beto.getAdminId(), "Beto", false, null, ana.getAdminId());

        assertThat(beto.isEnabled()).isFalse();
    }

    @Test
    void aNewPasswordIsHashedAndUnlocksTheAccount() {
        Admin ana = admin("ana");
        ana.setLockedUntil(OffsetDateTime.now(CLOCK).plusMinutes(10));

        service.update(ana.getAdminId(), "Ana", true, "clave-nueva-1", ana.getAdminId());

        assertThat(encoder.matches("clave-nueva-1", ana.getPasswordHash())).isTrue();
        assertThat(ana.getLockedUntil()).isNull();
    }

    @Test
    void blankPasswordKeepsTheCurrentOne() {
        Admin ana = admin("ana");
        String before = ana.getPasswordHash();

        service.update(ana.getAdminId(), "Ana Maria", true, "  ", ana.getAdminId());

        assertThat(ana.getPasswordHash()).isEqualTo(before);
        assertThat(ana.getDisplayName()).isEqualTo("Ana Maria");
    }

    // ---------------- mi perfil ----------------

    @Test
    void ownPasswordChangeNeedsTheCurrentPassword() {
        Admin ana = admin("ana");

        assertThatThrownBy(() -> service.changeOwnPassword(ana.getAdminId(), "equivocada", "clave-nueva-1"))
                .isInstanceOf(AccountException.class)
                .hasMessage("profile.validation.current.wrong");

        service.changeOwnPassword(ana.getAdminId(), "clave-vieja", "clave-nueva-1");
        assertThat(encoder.matches("clave-nueva-1", ana.getPasswordHash())).isTrue();
    }

    @Test
    void isEmptyReflectsTheTable() {
        when(repo.count()).thenReturn(0L);
        assertThat(service.isEmpty()).isTrue();
        verify(repo).count();
    }
}
