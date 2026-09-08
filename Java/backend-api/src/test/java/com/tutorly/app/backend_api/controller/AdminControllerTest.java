package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.entity.Admin;
import com.tutorly.app.backend_api.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@code DELETE /api/admins/{id}} - erase (anonymize), not
 * hard-delete. Covers the idempotency contract: 404 / 409 / 200, decided by
 * {@link AdminController} before {@link AdminService#eraseAdmin(Admin)} is
 * ever called.
 */
@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class AdminControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @Test
    void eraseAdmin_adminDoesNotExist_returns404AndNeverErases() throws Exception {
        when(adminService.getAdminById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/admins/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());

        verify(adminService, never()).eraseAdmin(any(Admin.class));
    }

    @Test
    void eraseAdmin_alreadyAnonymized_returns409AndDoesNotReScramble() throws Exception {
        Admin alreadyErased = new Admin("erased-admin-3@erased.invalid", "unusable", "erased-admin-3");
        alreadyErased.setId(3L);
        alreadyErased.setAnonymizedAt(LocalDateTime.now().minusDays(1));
        when(adminService.getAdminById(3L)).thenReturn(Optional.of(alreadyErased));

        mockMvc.perform(delete("/api/admins/3").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isConflict());

        verify(adminService, never()).eraseAdmin(any(Admin.class));
    }

    @Test
    void eraseAdmin_existsAndNotYetAnonymized_erasesAndReturns200() throws Exception {
        Admin admin = new Admin("admin@tutorly.com", "hashedPassword123", "admin1");
        admin.setId(3L);
        when(adminService.getAdminById(3L)).thenReturn(Optional.of(admin));

        Admin erased = new Admin("erased-admin-3@erased.invalid", "random-unusable", "erased-admin-3");
        erased.setId(3L);
        erased.setAnonymizedAt(LocalDateTime.now());
        when(adminService.eraseAdmin(admin)).thenReturn(erased);

        mockMvc.perform(delete("/api/admins/3").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mail").value("erased-admin-3@erased.invalid"))
                .andExpect(jsonPath("$.username").value("erased-admin-3"));

        verify(adminService).eraseAdmin(admin);
    }

    @Test
    void eraseAdmin_missingApiKey_isRejectedBeforeReachingTheController() throws Exception {
        mockMvc.perform(delete("/api/admins/3"))
                .andExpect(status().isUnauthorized());

        verify(adminService, never()).getAdminById(any());
    }
}
