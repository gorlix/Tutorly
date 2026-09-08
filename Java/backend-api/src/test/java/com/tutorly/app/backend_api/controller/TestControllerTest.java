package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.dto.TestCreateDTO;
import com.tutorly.app.backend_api.entity.Student;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.service.StudentService;
import com.tutorly.app.backend_api.service.TestService;
import com.tutorly.app.backend_api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link TestController} (the student evaluation/mark
 * entity, not a JUnit test class). {@code createTest} is wrapped in a
 * try/catch (500 on any exception); {@code updateTest} is not - see
 * {@link com.tutorly.app.backend_api.entity.Test} for entity details.
 */
@WebMvcTest(TestController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class TestControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TestService testService;

    @MockitoBean
    private UserService tutorService;

    @MockitoBean
    private StudentService studentService;

    private User tutor() {
        User t = new User("mario.rossi", "hashedPassword123", "ACTIVE", "GENERIC");
        t.setId(1L);
        return t;
    }

    private Student student() {
        Student s = new Student("Marco", "Rossi", "3A", null, "ACTIVE");
        s.setId(2L);
        return s;
    }

    @org.junit.jupiter.api.Test
    void getAllTests_returns200WithList() throws Exception {
        com.tutorly.app.backend_api.entity.Test test = new com.tutorly.app.backend_api.entity.Test(
                LocalDate.now(), "Algebra test", 8.5, "Matematica", tutor(), student());
        when(testService.getAllTests()).thenReturn(List.of(test));

        mockMvc.perform(get("/api/tests").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mark").value(8.5));
    }

    @org.junit.jupiter.api.Test
    void getTestById_notFound_returns404() throws Exception {
        when(testService.getTestById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tests/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.api.Test
    void getTestsByMinMark_returns200() throws Exception {
        when(testService.getTestsByMinMark(6.0)).thenReturn(List.of());

        mockMvc.perform(get("/api/tests/min-mark/6.0").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    void createTest_tutorNotFound_returns400() throws Exception {
        when(tutorService.getUserById(99L)).thenReturn(Optional.empty());

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "Algebra test", 8.5, "Matematica", 99L, 2L);

        mockMvc.perform(post("/api/tests")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Tutor not found with ID: 99"));
    }

    @org.junit.jupiter.api.Test
    void createTest_studentNotFound_returns400() throws Exception {
        when(tutorService.getUserById(1L)).thenReturn(Optional.of(tutor()));
        when(studentService.getStudentById(99L)).thenReturn(Optional.empty());

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "Algebra test", 8.5, "Matematica", 1L, 99L);

        mockMvc.perform(post("/api/tests")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Student not found with ID: 99"));
    }

    @org.junit.jupiter.api.Test
    void createTest_valid_returns201() throws Exception {
        when(tutorService.getUserById(1L)).thenReturn(Optional.of(tutor()));
        when(studentService.getStudentById(2L)).thenReturn(Optional.of(student()));
        when(testService.saveTest(any(com.tutorly.app.backend_api.entity.Test.class))).thenAnswer(invocation -> {
            com.tutorly.app.backend_api.entity.Test t = invocation.getArgument(0);
            t.setId(50L);
            return t;
        });

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "Algebra test", 8.5, "Matematica", 1L, 2L);

        mockMvc.perform(post("/api/tests")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50));
    }

    @org.junit.jupiter.api.Test
    void createTest_serviceThrows_returns500() throws Exception {
        when(tutorService.getUserById(1L)).thenReturn(Optional.of(tutor()));
        when(studentService.getStudentById(2L)).thenReturn(Optional.of(student()));
        when(testService.saveTest(any(com.tutorly.app.backend_api.entity.Test.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "Algebra test", 8.5, "Matematica", 1L, 2L);

        mockMvc.perform(post("/api/tests")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error creating test: DB unavailable"));
    }

    @org.junit.jupiter.api.Test
    void updateTest_testNotFound_returns404() throws Exception {
        when(testService.getTestById(99L)).thenReturn(Optional.empty());

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "Algebra test", 8.5, "Matematica", 1L, 2L);

        mockMvc.perform(put("/api/tests/99")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.api.Test
    void updateTest_valid_returns200() throws Exception {
        com.tutorly.app.backend_api.entity.Test existing = new com.tutorly.app.backend_api.entity.Test(
                LocalDate.now(), "Old", 6.0, "Matematica", tutor(), student());
        existing.setId(50L);
        when(testService.getTestById(50L)).thenReturn(Optional.of(existing));
        when(tutorService.getUserById(1L)).thenReturn(Optional.of(tutor()));
        when(studentService.getStudentById(2L)).thenReturn(Optional.of(student()));
        when(testService.saveTest(any(com.tutorly.app.backend_api.entity.Test.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TestCreateDTO dto = new TestCreateDTO(LocalDate.now(), "New description", 9.0, "Matematica", 1L, 2L);

        mockMvc.perform(put("/api/tests/50")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mark").value(9.0));
    }

    @org.junit.jupiter.api.Test
    void deleteTest_found_returns204() throws Exception {
        com.tutorly.app.backend_api.entity.Test existing = new com.tutorly.app.backend_api.entity.Test(
                LocalDate.now(), "Algebra test", 8.5, "Matematica", tutor(), student());
        existing.setId(50L);
        when(testService.getTestById(50L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/tests/50").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNoContent());
    }

    @org.junit.jupiter.api.Test
    void deleteTest_notFound_returns404() throws Exception {
        when(testService.getTestById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/tests/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }
}
