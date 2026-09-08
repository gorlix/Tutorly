package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.dto.PushSubscriptionCreateDTO;
import com.tutorly.app.backend_api.entity.PushSubscription;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.service.PushSubscriptionService;
import com.tutorly.app.backend_api.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link PushSubscriptionController}. Note there is no
 * endpoint here for {@code PushSubscriptionService#deleteAllForUser} - that's
 * only ever called internally by {@code UserService#eraseUser}, not exposed
 * as its own route.
 */
@WebMvcTest(PushSubscriptionController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class PushSubscriptionControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @MockitoBean
    private UserService userService;

    private User user() {
        User u = new User("mario.rossi", "hashedPassword123", "ACTIVE", "GENERIC");
        u.setId(5L);
        return u;
    }

    private PushSubscriptionCreateDTO dto() {
        PushSubscriptionCreateDTO dto = new PushSubscriptionCreateDTO();
        dto.setEndpoint("https://fcm.googleapis.com/send/abc");
        dto.setP256dh("p256dh-key");
        dto.setAuth("auth-secret");
        dto.setUserAgent("Mozilla/5.0");
        dto.setUserId(5L);
        return dto;
    }

    @Test
    void getSubscriptionsForUser_returns200WithList() throws Exception {
        when(pushSubscriptionService.getSubscriptionsForUser(5L)).thenReturn(List.of(new PushSubscription()));

        mockMvc.perform(get("/api/push-subscriptions/user/5").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void upsertSubscription_userNotFound_returns400() throws Exception {
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        PushSubscriptionCreateDTO dto = dto();
        dto.setUserId(99L);

        mockMvc.perform(post("/api/push-subscriptions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User not found with ID: 99"));
    }

    @Test
    void upsertSubscription_valid_returns201() throws Exception {
        // Same User instance reused for both stubs: User has no equals() override, so
        // Mockito's default argument matching is by reference - a second, "equal-looking"
        // instance would not match the call the controller actually makes.
        User user = user();
        when(userService.getUserById(5L)).thenReturn(Optional.of(user));
        PushSubscription saved = new PushSubscription("https://fcm.googleapis.com/send/abc", "p256dh-key", "auth-secret", "Mozilla/5.0", user);
        saved.setId(1L);
        when(pushSubscriptionService.upsertSubscription("https://fcm.googleapis.com/send/abc", "p256dh-key", "auth-secret", "Mozilla/5.0", user))
                .thenReturn(saved);

        mockMvc.perform(post("/api/push-subscriptions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void upsertSubscription_serviceThrows_returns500() throws Exception {
        when(userService.getUserById(5L)).thenReturn(Optional.of(user()));
        when(pushSubscriptionService.upsertSubscription(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        mockMvc.perform(post("/api/push-subscriptions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto())))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteByEndpoint_isIdempotent_alwaysReturns204() throws Exception {
        mockMvc.perform(delete("/api/push-subscriptions/by-endpoint")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .param("endpoint", "https://fcm.googleapis.com/send/gone"))
                .andExpect(status().isNoContent());
    }
}
