package com.quadmlabs.saphikuna.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El latido devuelve el token de seguridad vigente para que los formularios abiertos lo actualicen. */
class PingControllerTest {

    private final PingController controller = new PingController();

    @Test
    void devuelveElTokenVigenteYNoSeGuardaEnCache() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CsrfToken.class.getName(), new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "abc123"));

        ResponseEntity<Map<String, String>> response = controller.ping(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("_csrf", response.getBody().get("parameter"));
        assertEquals("abc123", response.getBody().get("token"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void sinTokenResponde200ConCuerpoVacio() {
        ResponseEntity<Map<String, String>> response = controller.ping(new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
    }
}
