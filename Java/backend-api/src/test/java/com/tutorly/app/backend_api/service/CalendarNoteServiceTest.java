package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.dto.CalendarNoteCreateDTO;
import com.tutorly.app.backend_api.entity.CalendarNote;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.CalendarNoteRepository;
import com.tutorly.app.backend_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CalendarNoteService}, which - unlike Lesson/Prenotation/
 * Test's services - has real logic: resolving creator/tutor IDs to entities
 * (throwing if any is missing) and deciding when to touch the {@code tutors}
 * set.
 */
@ExtendWith(MockitoExtension.class)
class CalendarNoteServiceTest {

    @Mock
    private CalendarNoteRepository calendarNoteRepository;

    @Mock
    private UserRepository tutorRepository;

    @InjectMocks
    private CalendarNoteService calendarNoteService;

    private User creator;
    private User tutorA;
    private User tutorB;

    @BeforeEach
    void setUp() {
        creator = new User("staff.member", "hashedPassword123", "ACTIVE", "STAFF");
        creator.setId(9L);
        tutorA = new User("tutor.a", "hashedPassword123", "ACTIVE", "GENERIC");
        tutorA.setId(1L);
        tutorB = new User("tutor.b", "hashedPassword123", "ACTIVE", "GENERIC");
        tutorB.setId(2L);
    }

    @Test
    void createCalendarNoteFromDTO_creatorNotFound_throws() {
        when(tutorRepository.findById(99L)).thenReturn(Optional.empty());
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 99L, null);

        assertThatThrownBy(() -> calendarNoteService.createCalendarNoteFromDTO(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createCalendarNoteFromDTO_noTutorIds_leavesTutorsEmpty() {
        when(tutorRepository.findById(9L)).thenReturn(Optional.of(creator));
        when(calendarNoteRepository.save(any(CalendarNote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, null);

        CalendarNote note = calendarNoteService.createCalendarNoteFromDTO(dto);

        assertThat(note.getTutors()).isEmpty();
        assertThat(note.getCreator()).isSameAs(creator);
    }

    @Test
    void createCalendarNoteFromDTO_withTutorIds_setsTutors() {
        when(tutorRepository.findById(9L)).thenReturn(Optional.of(creator));
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutorA));
        when(tutorRepository.findById(2L)).thenReturn(Optional.of(tutorB));
        when(calendarNoteRepository.save(any(CalendarNote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, List.of(1L, 2L));

        CalendarNote note = calendarNoteService.createCalendarNoteFromDTO(dto);

        assertThat(note.getTutors()).containsExactlyInAnyOrder(tutorA, tutorB);
    }

    @Test
    void createCalendarNoteFromDTO_tutorNotFound_throws() {
        when(tutorRepository.findById(9L)).thenReturn(Optional.of(creator));
        when(tutorRepository.findById(99L)).thenReturn(Optional.empty());
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, List.of(99L));

        assertThatThrownBy(() -> calendarNoteService.createCalendarNoteFromDTO(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateCalendarNoteFromDTO_noteNotFound_throws() {
        when(calendarNoteRepository.findById(99L)).thenReturn(Optional.empty());
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("Reminder", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 9L, null);

        assertThatThrownBy(() -> calendarNoteService.updateCalendarNoteFromDTO(99L, dto))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateCalendarNoteFromDTO_doesNotChangeCreator() {
        CalendarNote existing = new CalendarNote("Old description", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator);
        existing.setId(5L);
        when(calendarNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(calendarNoteRepository.save(any(CalendarNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // A different creatorId in the DTO must be ignored - update deliberately never touches creator
        User otherStaff = new User("other.staff", "hashedPassword123", "ACTIVE", "STAFF");
        otherStaff.setId(15L);
        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("New description", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 15L, null);

        CalendarNote updated = calendarNoteService.updateCalendarNoteFromDTO(5L, dto);

        assertThat(updated.getCreator()).isSameAs(creator);
        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    void updateCalendarNoteFromDTO_nullTutorIds_leavesExistingTutorsUnchanged() {
        CalendarNote existing = new CalendarNote("Old description", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator);
        existing.setId(5L);
        existing.getTutors().add(tutorA);
        when(calendarNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(calendarNoteRepository.save(any(CalendarNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("New description", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 9L, null);

        CalendarNote updated = calendarNoteService.updateCalendarNoteFromDTO(5L, dto);

        assertThat(updated.getTutors()).containsExactly(tutorA);
    }

    @Test
    void updateCalendarNoteFromDTO_emptyTutorIdsList_stillReplacesTutors() {
        // Unlike create, update has no isEmpty() check - an empty (non-null) list
        // still triggers a rebuild, clearing the existing tutors set.
        CalendarNote existing = new CalendarNote("Old description", LocalDateTime.now(), LocalDateTime.now().plusHours(1), creator);
        existing.setId(5L);
        existing.getTutors().add(tutorA);
        when(calendarNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(calendarNoteRepository.save(any(CalendarNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalendarNoteCreateDTO dto = new CalendarNoteCreateDTO("New description", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 9L, List.of());

        CalendarNote updated = calendarNoteService.updateCalendarNoteFromDTO(5L, dto);

        assertThat(updated.getTutors()).isEmpty();
    }
}
