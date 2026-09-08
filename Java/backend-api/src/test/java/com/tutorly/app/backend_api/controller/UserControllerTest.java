package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.AdminCreatesUserRepository;
import com.tutorly.app.backend_api.service.AdminService;
import com.tutorly.app.backend_api.service.UserService;
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
 * Slice tests for {@code DELETE /api/users/{id}} - erase (anonymize), not
 * hard-delete. Covers the idempotency contract: 404 / 409 / 200, decided by
 * {@link UserController} before {@link UserService#eraseUser(User)} is ever
 * called.
 */
@WebMvcTest(UserController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class UserControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private AdminCreatesUserRepository adminCreatesUserRepository;

    @Test
    void eraseUser_userDoesNotExist_returns404AndNeverErases() throws Exception {
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/users/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());

        verify(userService, never()).eraseUser(any(User.class));
    }

    @Test
    void eraseUser_alreadyAnonymized_returns409AndDoesNotReScramble() throws Exception {
        User alreadyErased = new User("erased-user-5", "unusable", "DISCONTINUED", "STAFF");
        alreadyErased.setId(5L);
        alreadyErased.setAnonymizedAt(LocalDateTime.now().minusDays(1));
        when(userService.getUserById(5L)).thenReturn(Optional.of(alreadyErased));

        mockMvc.perform(delete("/api/users/5").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isConflict());

        verify(userService, never()).eraseUser(any(User.class));
    }

    @Test
    void eraseUser_existsAndNotYetAnonymized_erasesAndReturns200() throws Exception {
        User user = new User("mario.rossi", "hashedPassword123", "ACTIVE", "STAFF");
        user.setId(5L);
        when(userService.getUserById(5L)).thenReturn(Optional.of(user));

        User erased = new User("erased-user-5", "random-unusable", "DISCONTINUED", "STAFF");
        erased.setId(5L);
        erased.setAnonymizedAt(LocalDateTime.now());
        when(userService.eraseUser(user)).thenReturn(erased);

        mockMvc.perform(delete("/api/users/5").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("erased-user-5"))
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        verify(userService).eraseUser(user);
    }

    @Test
    void eraseUser_missingApiKey_isRejectedBeforeReachingTheController() throws Exception {
        mockMvc.perform(delete("/api/users/5"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserById(any());
    }
}
