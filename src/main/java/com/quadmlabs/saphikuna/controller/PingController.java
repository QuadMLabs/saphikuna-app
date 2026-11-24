package com.quadmlabs.saphikuna.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Latido del panel. Los formularios lo llaman cada pocos minutos mientras están abiertos
 * (form-saving.js) por dos motivos, ambos del plan gratis:
 *  - el servidor se duerme tras 15 minutos sin visitas: así no se duerme mientras alguien llena un formulario;
 *  - si el servidor se reinició, la sesión nueva trae un token de seguridad distinto y el formulario
 *    abierto fallaría al guardar: se devuelve el vigente para que la página lo reemplace.
 * Requiere haber iniciado sesión como cualquier otra página de /admin.
 */
@RestController
@RequestMapping("/admin")
public class PingController {

    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> ping(HttpServletRequest request) {
        Object attribute = request.getAttribute(CsrfToken.class.getName());
        Map<String, String> body = attribute instanceof CsrfToken csrf
                ? Map.of("parameter", csrf.getParameterName(), "token", csrf.getToken())
                : Map.of();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
