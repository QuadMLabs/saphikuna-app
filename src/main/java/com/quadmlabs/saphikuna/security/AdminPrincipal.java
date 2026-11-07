package com.quadmlabs.saphikuna.security;

import com.quadmlabs.saphikuna.entity.Admin;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** El administrador que inició sesión. Su único rol es ADMIN. */
public record AdminPrincipal(
        UUID adminId,
        String username,
        String displayName,
        String passwordHash,
        boolean enabled,
        boolean locked) implements UserDetails {

    public static AdminPrincipal of(Admin admin, OffsetDateTime now) {
        return new AdminPrincipal(
                admin.getAdminId(),
                admin.getUsername(),
                admin.getDisplayName(),
                admin.getPasswordHash(),
                admin.isEnabled(),
                admin.isLockedAt(now));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
