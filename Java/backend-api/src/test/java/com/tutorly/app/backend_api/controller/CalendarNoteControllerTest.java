package com.tutorly.app.backend_api.controller;

import com.tutorly.app.backend_api.dto.CalendarNoteCreateDTO;
import com.tutorly.app.backend_api.entity.CalendarNote;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.service.CalendarNoteService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CalendarNoteController}. Only one dependency
 * ({@link CalendarNoteService}) - unlike Lesson/Prenotation/Test, tutor and
 * creator existence checks live entirely in the service, so the controller
 * only maps the service's {@code RuntimeException} to a status code: 400 on
 * create (empty body), 500 on update (with a message body) - deliberately
 * inconsistent between the two, per the real controller code.
 */
@WebMvcTest(CalendarNoteController.class)
@TestPropertySource(properties = "api.security.keys=test-api-key")
class CalendarNoteControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CalendarNoteService calendarNoteService;

    private User creator() {
        User u = new User("staff.member", "hashedPassword123", "ACTIVE", "STAFF");
        u.setId(9L);
        return u;
    }

    @Test
    void getAllCalendarNotes_returns200WithList() throws Exception {
        when(calendarNoteService.getAllCalendarNotes()).thenReturn(List.of(new CalendarNote()));

        mockMvc.perform(get("/api/calendar-notes").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void getCalendarNoteById_notFound_returns404() throws Exception {
        when(calendarNoteService.getCalendarNoteById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/calendar-notes/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCalendarNote_valid_returns201() throws Exception {
        CalendarNote saved = new CalendarNote("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator());
        saved.setId(5L);
        when(calendarNoteService.createCalendarNoteFromDTO(any(CalendarNoteCreateDTO.class))).thenReturn(saved);

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, null);

        mockMvc.perform(post("/api/calendar-notes")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createCalendarNote_creatorNotFound_returns400WithEmptyBody() throws Exception {
        when(calendarNoteService.createCalendarNoteFromDTO(any(CalendarNoteCreateDTO.class)))
                .thenThrow(new RuntimeException("Creator tutor not found with ID: 99"));

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 99L, null);

        mockMvc.perform(post("/api/calendar-notes")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void updateCalendarNote_noteNotFound_returns404() throws Exception {
        when(calendarNoteService.getCalendarNoteById(99L)).thenReturn(Optional.empty());

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, null);

        mockMvc.perform(put("/api/calendar-notes/99")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCalendarNote_valid_returns200() throws Exception {
        CalendarNote existing = new CalendarNote("Old", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator());
        existing.setId(5L);
        when(calendarNoteService.getCalendarNoteById(5L)).thenReturn(Optional.of(existing));

        CalendarNote updated = new CalendarNote("New", LocalDateTime.now(), LocalDateTime.now().plusHours(2), creator());
        updated.setId(5L);
        when(calendarNoteService.updateCalendarNoteFromDTO(eq(5L), any(CalendarNoteCreateDTO.class))).thenReturn(updated);

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("New", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 9L, null);

        mockMvc.perform(put("/api/calendar-notes/5")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New"));
    }

    @Test
    void updateCalendarNote_tutorNotFoundDuringUpdate_returns500WithMessageBody() throws Exception {
        CalendarNote existing = new CalendarNote("Old", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator());
        existing.setId(5L);
        when(calendarNoteService.getCalendarNoteById(5L)).thenReturn(Optional.of(existing));
        when(calendarNoteService.updateCalendarNoteFromDTO(eq(5L), any(CalendarNoteCreateDTO.class)))
                .thenThrow(new RuntimeException("Tutor not found with ID: 99"));

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("New", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 9L, List.of(99L));

        mockMvc.perform(put("/api/calendar-notes/5")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error updating calendar note: Tutor not found with ID: 99"));
    }

    @Test
    void deleteCalendarNote_found_returns204() throws Exception {
        CalendarNote existing = new CalendarNote("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator());
        existing.setId(5L);
        when(calendarNoteService.getCalendarNoteById(5L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/calendar-notes/5").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCalendarNote_notFound_returns404() throws Exception {
        when(calendarNoteService.getCalendarNoteById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/calendar-notes/99").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }
}
