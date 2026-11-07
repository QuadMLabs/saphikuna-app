package com.quadmlabs.saphikuna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Alta y edición de un administrador. Las claves se validan aparte (dependen de si es alta o edición). */
@Getter
@Setter
public class AdminForm {

    public static final int MIN_PASSWORD = 8;

    /** Solo en el alta; después no se cambia. */
    @NotBlank(message = "{admins.validation.username.required}")
    @Size(min = 3, max = 30, message = "{admins.validation.username.size}")
    @Pattern(regexp = "[A-Za-z0-9._-]*", message = "{admins.validation.username.pattern}")
    private String username;

    @NotBlank(message = "{admins.validation.name.required}")
    @Size(max = 60, message = "{admins.validation.name.size}")
    private String displayName;

    private String password;
    private String confirmPassword;

    private boolean enabled = true;
}
