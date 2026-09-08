package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Test;
import com.tutorly.app.backend_api.repository.TestRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TestService} (the student evaluation/mark entity,
 * not a JUnit test class) - every method is a thin pass-through to
 * {@link TestRepository}, no business logic in this service.
 */
@ExtendWith(MockitoExtension.class)
class TestServiceTest {

    @Mock
    private TestRepository testRepository;

    @InjectMocks
    private TestService testService;

    @org.junit.jupiter.api.Test
    void getAllTests_delegatesToRepository() {
        List<Test> tests = List.of(new Test());
        when(testRepository.findAll()).thenReturn(tests);

        assertThat(testService.getAllTests()).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void getTestById_delegatesToRepository() {
        Test test = new Test();
        when(testRepository.findById(5L)).thenReturn(Optional.of(test));

        assertThat(testService.getTestById(5L)).contains(test);
    }

    @org.junit.jupiter.api.Test
    void getTestsByTutor_delegatesToRepository() {
        List<Test> tests = List.of(new Test());
        when(testRepository.findByTutor_Id(3L)).thenReturn(tests);

        assertThat(testService.getTestsByTutor(3L)).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void getTestsByStudent_delegatesToRepository() {
        List<Test> tests = List.of(new Test());
        when(testRepository.findByStudent_Id(7L)).thenReturn(tests);

        assertThat(testService.getTestsByStudent(7L)).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void getTestsByTutorAndStudent_delegatesToRepository() {
        List<Test> tests = List.of(new Test());
        when(testRepository.findByTutor_IdAndStudent_Id(3L, 7L)).thenReturn(tests);

        assertThat(testService.getTestsByTutorAndStudent(3L, 7L)).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void getTestsByDateRange_delegatesToRepository() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        List<Test> tests = List.of(new Test());
        when(testRepository.findByDayBetween(start, end)).thenReturn(tests);

        assertThat(testService.getTestsByDateRange(start, end)).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void getTestsByMinMark_delegatesToRepository() {
        List<Test> tests = List.of(new Test());
        when(testRepository.findByMarkGreaterThanEqual(7.5)).thenReturn(tests);

        assertThat(testService.getTestsByMinMark(7.5)).isSameAs(tests);
    }

    @org.junit.jupiter.api.Test
    void saveTest_delegatesToRepository() {
        Test test = new Test();
        when(testRepository.save(test)).thenReturn(test);

        assertThat(testService.saveTest(test)).isSameAs(test);
    }

    @org.junit.jupiter.api.Test
    void deleteTest_delegatesToRepository() {
        testService.deleteTest(5L);

        verify(testRepository).deleteById(5L);
    }
}
