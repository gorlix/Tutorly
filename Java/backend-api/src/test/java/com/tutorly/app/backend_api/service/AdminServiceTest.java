package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Admin;
import com.tutorly.app.backend_api.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminService#eraseAdmin(Admin)} - the GDPR-style
 * anonymize-in-place erasure, replacing the old hard delete.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    // Same regex as the admin.mail_format CHECK constraint in Database/init.sql -
    // the placeholder email must keep satisfying it.
    private static final Pattern MAIL_FORMAT = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminService adminService;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = new Admin("admin@tutorly.com", "hashedPassword123", "admin1");
        admin.setId(3L);

        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void eraseAdmin_scrubsIdentifyingFields() {
        Admin erased = adminService.eraseAdmin(admin);

        assertThat(erased.getMail()).isEqualTo("erased-admin-3@erased.invalid");
        assertThat(erased.getUsername()).isEqualTo("erased-admin-3");
    }

    @Test
    void eraseAdmin_placeholderMailSatisfiesTheDatabaseMailFormatCheck() {
        Admin erased = adminService.eraseAdmin(admin);

        assertThat(MAIL_FORMAT.matcher(erased.getMail()).matches()).isTrue();
    }

    @Test
    void eraseAdmin_randomizesPasswordToAnUnusableValue() {
        String originalPassword = admin.getPassword();

        Admin erased = adminService.eraseAdmin(admin);

        assertThat(erased.getPassword())
                .isNotEqualTo(originalPassword)
                .satisfies(password -> UUID.fromString(password)); // throws if not a valid UUID string
    }

    @Test
    void eraseAdmin_stampsAnonymizedAt() {
        LocalDateTime before = LocalDateTime.now();

        Admin erased = adminService.eraseAdmin(admin);

        assertThat(erased.getAnonymizedAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
    }

    @Test
    void eraseAdmin_savesTheEntity() {
        adminService.eraseAdmin(admin);

        verify(adminRepository).save(admin);
    }
}
