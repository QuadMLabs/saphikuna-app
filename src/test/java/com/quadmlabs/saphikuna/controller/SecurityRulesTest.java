package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.security.AdminAccountService;
import com.quadmlabs.saphikuna.security.LoginController;
import com.quadmlabs.saphikuna.security.SecurityConfig;
import com.quadmlabs.saphikuna.service.SettingsService;
import com.quadmlabs.saphikuna.service.SystemStatusService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quién puede entrar a qué: con la seguridad REAL (SecurityConfig). */
@WebMvcTest(controllers = { LoginController.class, SettingsController.class },
        properties = "app.security.remember-key=clave-solo-para-pruebas")
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminAccountService accounts;
    @MockitoBean
    SettingsService settings;
    @MockitoBean
    SystemStatusService systemStatus;

    @BeforeEach
    void setUp() {
        StoreInfoForm store = new StoreInfoForm();
        store.setStoreName("Saphikuna");
        when(settings.newProductDays()).thenReturn(15);
        when(settings.storeInfo()).thenReturn(store);
        when(systemStatus.check()).thenReturn(new SystemStatusService.SystemStatus(
                true, 12, true, 230, 20, 75, "America/Guayaquil", LocalDate.of(2026, 11, 7)));
    }

    @Test
    void panelWithoutSessionGoesToLogin() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rootWithoutSessionGoesToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsPublicAndCarriesTheCsrfToken() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"remember-me\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPageShowsTheErrorAndLogoutMessages() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Usuario o clave incorrectos")));
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(containsString("Cerraste sesión")));
    }

    @Test
    void thereIsNoPublicCatalogAnymore() throws Exception {
        // El catálogo ahora se arma y se descarga solo desde el panel: sin sesión, todo lleva al login
        mvc.perform(get("/catalogo")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/catalogo.pdf")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminCanSeeThePanel() throws Exception {
        mvc.perform(get("/admin/settings").with(user("ana").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void otherRolesCannotSeeThePanel() throws Exception {
        mvc.perform(get("/admin/settings").with(user("beto").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedInUserVisitingLoginGoesToThePanel() throws Exception {
        mvc.perform(get("/login").with(user("ana").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/settings/new-days").param("newProductDays", "10")
                .with(user("ana").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfTokenGoesThrough() throws Exception {
        mvc.perform(post("/admin/settings/new-days").param("newProductDays", "10")
                .with(user("ana").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void logoutNeedsPostAndEndsTheSession() throws Exception {
        mvc.perform(post("/logout").with(user("ana").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }
}
