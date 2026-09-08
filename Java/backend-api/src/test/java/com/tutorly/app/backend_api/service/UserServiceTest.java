package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#eraseUser(User)} - the GDPR-style
 * anonymize-in-place erasure, replacing the old hard delete.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PushSubscriptionService pushSubscriptionService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("mario.rossi", "hashedPassword123", "ACTIVE", "STAFF");
        user.setId(5L);
        user.setMail("mario.rossi@email.com");
        // save() is expected to persist and return the same (now-scrubbed) instance
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void eraseUser_scrubsIdentifyingFields() {
        User erased = userService.eraseUser(user);

        assertThat(erased.getUsername()).isEqualTo("erased-user-5");
        assertThat(erased.getMail()).isNull();
        assertThat(erased.getStatus()).isEqualTo("DISCONTINUED");
    }

    @Test
    void eraseUser_randomizesPasswordToAnUnusableValue() {
        String originalPassword = user.getPassword();

        User erased = userService.eraseUser(user);

        assertThat(erased.getPassword())
                .isNotEqualTo(originalPassword)
                .satisfies(password -> UUID.fromString(password)); // throws if not a valid UUID string
    }

    @Test
    void eraseUser_stampsAnonymizedAt() {
        LocalDateTime before = LocalDateTime.now();

        User erased = userService.eraseUser(user);

        assertThat(erased.getAnonymizedAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
    }

    @Test
    void eraseUser_leavesRoleUntouched() {
        User erased = userService.eraseUser(user);

        // role is deliberately preserved so role-based queries (e.g. GET /users/role/STAFF)
        // don't silently lose an erased account
        assertThat(erased.getRole()).isEqualTo("STAFF");
    }

    @Test
    void eraseUser_savesTheEntity() {
        userService.eraseUser(user);

        verify(userRepository).save(user);
    }

    @Test
    void eraseUser_hardDeletesPushSubscriptionsForThatUser() {
        userService.eraseUser(user);

        // Push subscriptions hold device-identifying data with no retention purpose -
        // they're hard-deleted, not anonymized, unlike the account fields above
        verify(pushSubscriptionService).deleteAllForUser(5L);
    }
}
