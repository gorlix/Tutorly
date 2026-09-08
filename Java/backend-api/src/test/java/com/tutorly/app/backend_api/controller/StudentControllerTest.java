package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.entity.Student;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.service.StudentService;
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
 * Slice tests for {@code DELETE /api/students/{id}} - erase (anonymize), not
 * hard-delete. Covers the idempotency contract: 404 / 409 / 200, decided by
 * {@link StudentController} before {@link StudentService#eraseStudent(Student)}
 * is ever called.
 */
@WebMvcTest(StudentController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class StudentControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private UserService userService;

    @Test
    void eraseStudent_studentDoesNotExist_returns404AndNeverErases() throws Exception {
        when(studentService.getStudentById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/students/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());

        verify(studentService, never()).eraseStudent(any(Student.class));
    }

    @Test
    void eraseStudent_alreadyAnonymized_returns409AndDoesNotReScramble() throws Exception {
        Student alreadyErased = new Student("Erased", "Student 7", "3A", null, "BLOCKED");
        alreadyErased.setId(7L);
        alreadyErased.setAnonymizedAt(LocalDateTime.now().minusDays(1));
        when(studentService.getStudentById(7L)).thenReturn(Optional.of(alreadyErased));

        mockMvc.perform(delete("/api/students/7").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isConflict());

        verify(studentService, never()).eraseStudent(any(Student.class));
    }

    @Test
    void eraseStudent_existsAndNotYetAnonymized_erasesAndReturns200() throws Exception {
        Student student = new Student("Marco", "Rossi", "3A", "Excellent in mathematics", "ACTIVE");
        student.setId(7L);
        User guest = new User("guest.parent", "hashedPassword123", "ACTIVE", "GUEST");
        guest.setId(42L);
        student.setUser(guest);
        when(studentService.getStudentById(7L)).thenReturn(Optional.of(student));

        Student erased = new Student("Erased", "Student 7", "3A", null, "BLOCKED");
        erased.setId(7L);
        erased.setUser(guest);
        erased.setAnonymizedAt(LocalDateTime.now());
        when(studentService.eraseStudent(student)).thenReturn(erased);

        mockMvc.perform(delete("/api/students/7").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Erased"))
                .andExpect(jsonPath("$.surname").value("Student 7"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.userId").value(42));

        verify(studentService).eraseStudent(student);
    }

    @Test
    void eraseStudent_missingApiKey_isRejectedBeforeReachingTheController() throws Exception {
        mockMvc.perform(delete("/api/students/7"))
                .andExpect(status().isUnauthorized());

        verify(studentService, never()).getStudentById(any());
    }
}
