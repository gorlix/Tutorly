package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Lesson;
import com.tutorly.app.backend_api.entity.Pack;
import com.tutorly.app.backend_api.entity.Student;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.PackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PackService} - the one service in the codebase with
 * real business logic: drawing lessons from a prepaid pack, splitting a
 * lesson that only partially fits the remaining hours, and picking which of
 * a student's packs a new lesson should be drawn from.
 *
 * All of these depend on {@link LessonService} (not {@link com.tutorly.app.backend_api.repository.LessonRepository}
 * directly - PackService never touches lesson persistence itself).
 */
@ExtendWith(MockitoExtension.class)
class PackServiceTest {

    @Mock
    private PackRepository packRepository;

    @Mock
    private LessonService lessonService;

    @InjectMocks
    private PackService packService;

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

    private Lesson lesson(LocalDateTime start, LocalDateTime end) {
        Lesson l = new Lesson("Algebra", start, end, tutor(), student());
        return l;
    }

    // --- getUsedHours ---

    @Test
    void getUsedHours_sumsLessonDurations() {
        Pack pack = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 10.0, null, student());
        pack.setId(7L);
        Lesson l1 = lesson(LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 11, 0)); // 1h
        Lesson l2 = lesson(LocalDateTime.of(2026, 9, 3, 10, 0), LocalDateTime.of(2026, 9, 3, 11, 30)); // 1.5h
        when(lessonService.getLessonsByPack(7L)).thenReturn(List.of(l1, l2));

        assertThat(packService.getUsedHours(pack)).isEqualTo(2.5);
    }

    @Test
    void getUsedHours_noLessons_returnsZero() {
        Pack pack = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 10.0, null, student());
        pack.setId(7L);
        when(lessonService.getLessonsByPack(7L)).thenReturn(List.of());

        assertThat(packService.getUsedHours(pack)).isEqualTo(0.0);
    }

    // --- getHoursOutsidePack / getFirstUnassignedLessonStart ---

    @Test
    void getHoursOutsidePack_sumsUnassignedLessonDurations() {
        Student student = student();
        LocalDateTime packStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        Pack pack = new Pack(packStart, 10.0, null, student);
        pack.setId(7L);
        Lesson unassigned = lesson(LocalDateTime.of(2026, 9, 5, 10, 0), LocalDateTime.of(2026, 9, 5, 12, 0)); // 2h
        when(lessonService.getLessonsWithNoPackAfter(2L, packStart)).thenReturn(List.of(unassigned));

        assertThat(packService.getHoursOutsidePack(pack)).isEqualTo(2.0);
    }

    @Test
    void getFirstUnassignedLessonStart_returnsEarliestStart() {
        Student student = student();
        LocalDateTime packStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        Pack pack = new Pack(packStart, 10.0, null, student);
        pack.setId(7L);
        LocalDateTime earlier = LocalDateTime.of(2026, 9, 5, 10, 0);
        LocalDateTime later = LocalDateTime.of(2026, 9, 10, 10, 0);
        when(lessonService.getLessonsWithNoPackAfter(2L, packStart)).thenReturn(List.of(
                lesson(later, later.plusHours(1)),
                lesson(earlier, earlier.plusHours(1))
        ));

        assertThat(packService.getFirstUnassignedLessonStart(pack)).contains(earlier);
    }

    @Test
    void getFirstUnassignedLessonStart_noUnassignedLessons_returnsEmpty() {
        Student student = student();
        LocalDateTime packStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        Pack pack = new Pack(packStart, 10.0, null, student);
        pack.setId(7L);
        when(lessonService.getLessonsWithNoPackAfter(2L, packStart)).thenReturn(List.of());

        assertThat(packService.getFirstUnassignedLessonStart(pack)).isEmpty();
    }

    // --- findActivePackWithAvailableHours ---

    @Test
    void findActivePackWithAvailableHours_picksEarliestEligiblePack() {
        Student student = student();
        LocalDateTime lessonStart = LocalDateTime.of(2026, 9, 15, 10, 0);

        Pack older = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 10.0, null, student);
        older.setId(1L);
        Pack newer = new Pack(LocalDateTime.of(2026, 9, 10, 0, 0), 10.0, null, student);
        newer.setId(2L);
        when(packRepository.findByStudent_Id(2L)).thenReturn(List.of(newer, older));
        // Both packs have no lessons drawn yet - both eligible
        when(lessonService.getLessonsByPack(1L)).thenReturn(List.of());
        when(lessonService.getLessonsByPack(2L)).thenReturn(List.of());

        Optional<Pack> result = packService.findActivePackWithAvailableHours(2L, lessonStart);

        assertThat(result).contains(older);
    }

    @Test
    void findActivePackWithAvailableHours_excludesClosedPacks() {
        Student student = student();
        LocalDateTime lessonStart = LocalDateTime.of(2026, 9, 15, 10, 0);

        Pack closed = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 10.0, LocalDate.of(2026, 9, 10), student);
        closed.setId(1L);
        when(packRepository.findByStudent_Id(2L)).thenReturn(List.of(closed));

        Optional<Pack> result = packService.findActivePackWithAvailableHours(2L, lessonStart);

        assertThat(result).isEmpty();
    }

    @Test
    void findActivePackWithAvailableHours_excludesPacksWithNoRemainingHours() {
        Student student = student();
        LocalDateTime packStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime lessonStart = LocalDateTime.of(2026, 9, 15, 10, 0);

        Pack full = new Pack(packStart, 2.0, null, student);
        full.setId(1L);
        Lesson usedUp = lesson(LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 12, 0)); // 2h, fully used
        when(packRepository.findByStudent_Id(2L)).thenReturn(List.of(full));
        when(lessonService.getLessonsByPack(1L)).thenReturn(List.of(usedUp));

        Optional<Pack> result = packService.findActivePackWithAvailableHours(2L, lessonStart);

        assertThat(result).isEmpty();
    }

    @Test
    void findActivePackWithAvailableHours_excludesPacksStartingAfterTheLesson() {
        Student student = student();
        LocalDateTime lessonStart = LocalDateTime.of(2026, 9, 1, 10, 0);
        // Pack starts AFTER the lesson - not eligible (lessonStartTime must be after pack.startTime)
        Pack future = new Pack(LocalDateTime.of(2026, 9, 10, 0, 0), 10.0, null, student);
        future.setId(1L);
        when(packRepository.findByStudent_Id(2L)).thenReturn(List.of(future));

        Optional<Pack> result = packService.findActivePackWithAvailableHours(2L, lessonStart);

        assertThat(result).isEmpty();
    }

    // --- assignLessonToPack ---

    @Test
    void assignLessonToPack_lessonFitsEntirely_assignsWholeLessonToThePack() {
        Pack pack = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 10.0, null, student());
        pack.setId(7L);
        when(lessonService.getLessonsByPack(7L)).thenReturn(List.of()); // no hours used yet

        Lesson newLesson = lesson(LocalDateTime.of(2026, 9, 5, 10, 0), LocalDateTime.of(2026, 9, 5, 11, 0)); // 1h, fits in 10h
        when(lessonService.saveLesson(newLesson)).thenReturn(newLesson);

        Lesson result = packService.assignLessonToPack(pack, newLesson);

        assertThat(result).isSameAs(newLesson);
        assertThat(newLesson.getPack()).isSameAs(pack);
        assertThat(newLesson.getEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 11, 0)); // unchanged
        verify(lessonService, times(1)).saveLesson(newLesson);
    }

    @Test
    void assignLessonToPack_lessonOnlyPartiallyFits_splitsIntoPackPortionAndRemainder() {
        // 1 hour remaining in the pack, but the lesson is 2 hours long
        Pack pack = new Pack(LocalDateTime.of(2026, 9, 1, 0, 0), 1.0, null, student());
        pack.setId(7L);
        when(lessonService.getLessonsByPack(7L)).thenReturn(List.of()); // 0 used, 1h remaining

        LocalDateTime start = LocalDateTime.of(2026, 9, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 5, 12, 0); // 2h lesson
        Lesson newLesson = lesson(start, end);

        when(lessonService.saveLesson(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lesson result = packService.assignLessonToPack(pack, newLesson);

        // The original lesson is trimmed to the 1h that fits in the pack...
        assertThat(result).isSameAs(newLesson);
        assertThat(newLesson.getPack()).isSameAs(pack);
        assertThat(newLesson.getEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 11, 0));

        // ...and a second, unassigned lesson is saved separately for the remaining 1h
        ArgumentCaptor<Lesson> savedCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonService, times(2)).saveLesson(savedCaptor.capture());
        Lesson remainder = savedCaptor.getAllValues().get(0);
        assertThat(remainder.getPack()).isNull();
        assertThat(remainder.getStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 11, 0));
        assertThat(remainder.getEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 5, 12, 0));
    }

    // --- assignUnassignedLessonsSince ---

    @Test
    void assignUnassignedLessonsSince_stopsOnceThePackIsFull() {
        Student student = student();
        LocalDateTime packStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        Pack pack = new Pack(packStart, 1.0, null, student); // only 1 hour available
        pack.setId(7L);

        Lesson first = lesson(LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 11, 0)); // 1h - fills the pack
        Lesson second = lesson(LocalDateTime.of(2026, 9, 3, 10, 0), LocalDateTime.of(2026, 9, 3, 11, 0)); // 1h - should never be touched
        // Mutable list required: the service sorts it in place. Unsorted on purpose.
        when(lessonService.getLessonsWithNoPackFrom(2L, packStart)).thenReturn(new java.util.ArrayList<>(List.of(second, first)));

        // getUsedHours(pack) -> lessonService.getLessonsByPack(7L) is called three times:
        // (1) the loop's pre-check before "first" (0h used, proceeds), (2) assignLessonToPack's
        // own remaining-hours computation for "first" (still 0h used - not saved yet, so the
        // whole 1h lesson fits exactly), (3) the loop's pre-check before "second" (now 1h used
        // >= 1h capacity, so it breaks and "second" is never touched).
        when(lessonService.getLessonsByPack(7L)).thenReturn(List.of(), List.of(), List.of(first));
        when(lessonService.saveLesson(first)).thenReturn(first);

        packService.assignUnassignedLessonsSince(pack);

        assertThat(first.getPack()).isSameAs(pack);
        assertThat(second.getPack()).isNull();
        verify(lessonService, never()).saveLesson(second);
    }

    // --- savePack / deletePack ---

    @Test
    void savePack_delegatesToRepository() {
        Pack pack = new Pack(LocalDateTime.now(), 10.0, null, student());
        when(packRepository.save(pack)).thenReturn(pack);

        assertThat(packService.savePack(pack)).isSameAs(pack);
    }

    @Test
    void deletePack_delegatesToRepository() {
        packService.deletePack(7L);

        verify(packRepository).deleteById(7L);
    }
}
