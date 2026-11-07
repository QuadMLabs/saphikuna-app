package com.quadmlabs.saphikuna.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos de la tienda (marca y contacto): los usan la tienda en línea y el catálogo. */
@Getter
@Setter
public class StoreInfoForm {

    @NotBlank(message = "{settings.validation.store.name.required}")
    @Size(max = 80, message = "{settings.validation.store.name.max}")
    private String storeName;

    @Size(max = 120, message = "{settings.validation.store.tagline.max}")
    private String tagline;

    /** Con código de país, por ejemplo +593 99 123 4567. */
    @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,25}$", message = "{settings.validation.phone.invalid}")
    private String whatsapp;

    @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,25}$", message = "{settings.validation.phone.invalid}")
    private String phone;

    @Email(message = "{settings.validation.email.invalid}")
    @Size(max = 120, message = "{settings.validation.email.max}")
    private String email;

    /** Usuario (@saphikuna) o enlace completo. */
    @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,60}$|^https?://\\S{3,190}$", message = "{settings.validation.social.invalid}")
    private String instagram;

    /** Nombre de la página o enlace completo. */
    @Pattern(regexp = "^$|^@?[A-Za-z0-9._-]{1,60}$|^https?://\\S{3,190}$", message = "{settings.validation.social.invalid}")
    private String facebook;

    @Size(max = 200, message = "{settings.validation.address.max}")
    private String address;
}
