package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Lesson;
import com.tutorly.app.backend_api.repository.LessonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LessonService} - every method here is a thin
 * pass-through to {@link LessonRepository} (no business logic lives in this
 * service; pack auto-assignment/splitting lives in {@code PackService}
 * instead). These tests exist to pin the pass-through contract, not to
 * exercise complex behavior.
 */
@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @InjectMocks
    private LessonService lessonService;

    @Test
    void getAllLessons_delegatesToRepository() {
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findAll()).thenReturn(lessons);

        assertThat(lessonService.getAllLessons()).isSameAs(lessons);
    }

    @Test
    void getLessonById_delegatesToRepository() {
        Lesson lesson = new Lesson();
        when(lessonRepository.findById(5L)).thenReturn(Optional.of(lesson));

        assertThat(lessonService.getLessonById(5L)).contains(lesson);
    }

    @Test
    void getLessonsByTutor_delegatesToRepository() {
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByTutor_Id(3L)).thenReturn(lessons);

        assertThat(lessonService.getLessonsByTutor(3L)).isSameAs(lessons);
    }

    @Test
    void getLessonsByStudent_delegatesToRepository() {
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByStudent_Id(7L)).thenReturn(lessons);

        assertThat(lessonService.getLessonsByStudent(7L)).isSameAs(lessons);
    }

    @Test
    void getLessonsByTutorAndStudent_delegatesToRepository() {
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByTutor_IdAndStudent_Id(3L, 7L)).thenReturn(lessons);

        assertThat(lessonService.getLessonsByTutorAndStudent(3L, 7L)).isSameAs(lessons);
    }

    @Test
    void getLessonsByPack_delegatesToRepository() {
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByPack_Id(9L)).thenReturn(lessons);

        assertThat(lessonService.getLessonsByPack(9L)).isSameAs(lessons);
    }

    @Test
    void getLessonsWithNoPackAfter_delegatesToRepository() {
        LocalDateTime after = LocalDateTime.of(2026, 9, 1, 0, 0);
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByStudent_IdAndPackIsNullAndStartTimeAfter(7L, after)).thenReturn(lessons);

        assertThat(lessonService.getLessonsWithNoPackAfter(7L, after)).isSameAs(lessons);
    }

    @Test
    void getLessonsWithNoPackFrom_delegatesToRepository() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByStudent_IdAndPackIsNullAndStartTimeGreaterThanEqual(7L, from)).thenReturn(lessons);

        assertThat(lessonService.getLessonsWithNoPackFrom(7L, from)).isSameAs(lessons);
    }

    @Test
    void getLessonsByDateRange_delegatesToRepository() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 30, 23, 59);
        List<Lesson> lessons = List.of(new Lesson());
        when(lessonRepository.findByStartTimeBetween(start, end)).thenReturn(lessons);

        assertThat(lessonService.getLessonsByDateRange(start, end)).isSameAs(lessons);
    }

    @Test
    void saveLesson_delegatesToRepository() {
        Lesson lesson = new Lesson();
        when(lessonRepository.save(lesson)).thenReturn(lesson);

        assertThat(lessonService.saveLesson(lesson)).isSameAs(lesson);
    }

    @Test
    void deleteLesson_delegatesToRepository() {
        lessonService.deleteLesson(5L);

        verify(lessonRepository).deleteById(5L);
    }
}
