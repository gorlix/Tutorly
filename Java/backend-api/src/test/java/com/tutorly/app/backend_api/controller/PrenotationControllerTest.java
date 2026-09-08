package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.dto.PrenotationCreateDTO;
import com.tutorly.app.backend_api.entity.Prenotation;
import com.tutorly.app.backend_api.entity.Student;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.service.PrenotationService;
import com.tutorly.app.backend_api.service.StudentService;
import com.tutorly.app.backend_api.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
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
 * Slice tests for {@link PrenotationController}. Covers the plain CRUD
 * endpoints plus the two shapes that aren't just the raw entity: the DTO
 * validation order (student -> tutor -> creator) on {@code /create}/update,
 * and {@code getPrenotationsByTutor}'s mapping to {@code PrenotationResponseDTO}
 * (adds studentName/studentSurname/studentClass, "Unknown"/"" fallback if the
 * student is null).
 */
@WebMvcTest(PrenotationController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class PrenotationControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrenotationService prenotationService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private UserService tutorService;

    private Student student() {
        Student s = new Student("Marco", "Rossi", "3A", null, "ACTIVE");
        s.setId(2L);
        return s;
    }

    private User tutor() {
        User t = new User("mario.rossi", "hashedPassword123", "ACTIVE", "GENERIC");
        t.setId(1L);
        return t;
    }

    private User staff() {
        User s = new User("staff.member", "hashedPassword123", "ACTIVE", "STAFF");
        s.setId(9L);
        return s;
    }

    @Test
    void getAllPrenotations_returns200WithList() throws Exception {
        when(prenotationService.getAllPrenotations()).thenReturn(List.of(new Prenotation()));

        mockMvc.perform(get("/api/prenotations").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void getPrenotationById_notFound_returns404() throws Exception {
        when(prenotationService.getPrenotationById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/prenotations/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPrenotationsByTutor_mapsToResponseDtoWithStudentDetails() throws Exception {
        Prenotation prenotation = new Prenotation(LocalDateTime.now(), LocalDateTime.now().plusHours(1), student(), tutor(), staff());
        prenotation.setId(20L);
        when(prenotationService.getPrenotationsByTutor(1L)).thenReturn(List.of(prenotation));

        mockMvc.perform(get("/api/prenotations/tutor/1").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].studentName").value("Marco"))
                .andExpect(jsonPath("$[0].studentSurname").value("Rossi"))
                .andExpect(jsonPath("$[0].studentClass").value("3A"));
    }

    @Test
    void createPrenotationFromDTO_studentNotFound_returns400BeforeCheckingTutorOrCreator() throws Exception {
        when(studentService.getStudentById(99L)).thenReturn(Optional.empty());

        PrenotationCreateDTO dto = new PrenotationCreateDTO(LocalDateTime.now(), LocalDateTime.now().plusHours(1), 99L, 1L, 9L);

        mockMvc.perform(post("/api/prenotations/create")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Student not found with ID: 99"));
    }

    @Test
    void createPrenotationFromDTO_valid_createsWithFlagDefaultingToFalse() throws Exception {
        when(studentService.getStudentById(2L)).thenReturn(Optional.of(student()));
        when(tutorService.getUserById(1L)).thenReturn(Optional.of(tutor()));
        when(tutorService.getUserById(9L)).thenReturn(Optional.of(staff()));
        when(prenotationService.savePrenotation(any(Prenotation.class))).thenAnswer(invocation -> {
            Prenotation p = invocation.getArgument(0);
            p.setId(30L);
            return p;
        });

        // flag intentionally omitted from the DTO - controller should default it to false
        PrenotationCreateDTO dto = new PrenotationCreateDTO(LocalDateTime.now(), LocalDateTime.now().plusHours(1), 2L, 1L, 9L);

        mockMvc.perform(post("/api/prenotations/create")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.flag").value(false));
    }

    @Test
    void updatePrenotationFromDTO_prenotationNotFound_returns404() throws Exception {
        when(prenotationService.getPrenotationById(99L)).thenReturn(Optional.empty());

        PrenotationCreateDTO dto = new PrenotationCreateDTO(LocalDateTime.now(), LocalDateTime.now().plusHours(1), 2L, 1L, 9L);

        mockMvc.perform(put("/api/prenotations/99")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePrenotation_found_returns204() throws Exception {
        Prenotation prenotation = new Prenotation(LocalDateTime.now(), LocalDateTime.now().plusHours(1), student(), tutor(), staff());
        prenotation.setId(20L);
        when(prenotationService.getPrenotationById(20L)).thenReturn(Optional.of(prenotation));

        mockMvc.perform(delete("/api/prenotations/20").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePrenotation_notFound_returns404() throws Exception {
        when(prenotationService.getPrenotationById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/prenotations/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }
}
