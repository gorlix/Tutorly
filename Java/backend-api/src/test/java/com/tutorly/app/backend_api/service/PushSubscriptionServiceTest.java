package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.PushSubscription;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushSubscriptionService#deleteAllForUser(Long)} - the
 * hard-delete side effect of {@code UserService#eraseUser(User)}: subscriptions
 * hold device-identifying data (endpoint URL, crypto keys) with no retention
 * purpose, so they're removed outright rather than anonymized.
 */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @InjectMocks
    private PushSubscriptionService pushSubscriptionService;

    @Test
    void deleteAllForUser_deletesEverySubscriptionBelongingToThatUser() {
        User user = new User("mario.rossi", "hashedPassword123", "ACTIVE", "GENERIC");
        user.setId(5L);

        PushSubscription phone = new PushSubscription("https://fcm.googleapis.com/send/phone", "p256dh-1", "auth-1", "Mozilla/5.0 (phone)", user);
        PushSubscription laptop = new PushSubscription("https://fcm.googleapis.com/send/laptop", "p256dh-2", "auth-2", "Mozilla/5.0 (laptop)", user);
        List<PushSubscription> subscriptions = List.of(phone, laptop);

        when(pushSubscriptionRepository.findByUser_Id(5L)).thenReturn(subscriptions);

        pushSubscriptionService.deleteAllForUser(5L);

        verify(pushSubscriptionRepository).deleteAll(subscriptions);
    }

    @Test
    void deleteAllForUser_userWithNoSubscriptions_isANoOp() {
        when(pushSubscriptionRepository.findByUser_Id(99L)).thenReturn(Collections.emptyList());

        pushSubscriptionService.deleteAllForUser(99L);

        verify(pushSubscriptionRepository).deleteAll(Collections.emptyList());
    }
}
