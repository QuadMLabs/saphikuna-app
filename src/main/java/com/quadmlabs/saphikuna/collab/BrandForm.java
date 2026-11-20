package com.quadmlabs.saphikuna.collab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos de la marca: solo el nombre y si se muestra en la tienda. */
@Getter
@Setter
public class BrandForm {

    @NotBlank(message = "{collab.brand.validation.name.required}")
    @Size(max = 80, message = "{collab.brand.validation.name.max}")
    private String brandName;

    private boolean visible = true;
}
