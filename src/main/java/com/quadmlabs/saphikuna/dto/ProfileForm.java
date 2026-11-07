package com.quadmlabs.saphikuna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** "Mi perfil": nombre visible y cambio de clave propio. */
@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "{admins.validation.name.required}")
    @Size(max = 60, message = "{admins.validation.name.size}")
    private String displayName;

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
}
