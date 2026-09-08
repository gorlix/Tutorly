package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Prenotation;
import com.tutorly.app.backend_api.repository.PrenotationRepository;
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
 * Unit tests for {@link PrenotationService} - every method is a thin
 * pass-through to {@link PrenotationRepository}, no business logic in this
 * service.
 */
@ExtendWith(MockitoExtension.class)
class PrenotationServiceTest {

    @Mock
    private PrenotationRepository prenotationRepository;

    @InjectMocks
    private PrenotationService prenotationService;

    @Test
    void getAllPrenotations_delegatesToRepository() {
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findAll()).thenReturn(prenotations);

        assertThat(prenotationService.getAllPrenotations()).isSameAs(prenotations);
    }

    @Test
    void getPrenotationById_delegatesToRepository() {
        Prenotation prenotation = new Prenotation();
        when(prenotationRepository.findById(5L)).thenReturn(Optional.of(prenotation));

        assertThat(prenotationService.getPrenotationById(5L)).contains(prenotation);
    }

    @Test
    void getPrenotationsByStudent_delegatesToRepository() {
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findByStudent_Id(7L)).thenReturn(prenotations);

        assertThat(prenotationService.getPrenotationsByStudent(7L)).isSameAs(prenotations);
    }

    @Test
    void getPrenotationsByTutor_delegatesToRepository() {
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findByTutor_Id(3L)).thenReturn(prenotations);

        assertThat(prenotationService.getPrenotationsByTutor(3L)).isSameAs(prenotations);
    }

    @Test
    void getPrenotationsByCreator_delegatesToRepository() {
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findByCreator_Id(3L)).thenReturn(prenotations);

        assertThat(prenotationService.getPrenotationsByCreator(3L)).isSameAs(prenotations);
    }

    @Test
    void getPrenotationsByFlag_delegatesToRepository() {
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findByFlag(true)).thenReturn(prenotations);

        assertThat(prenotationService.getPrenotationsByFlag(true)).isSameAs(prenotations);
    }

    @Test
    void getPrenotationsByDateRange_delegatesToRepository() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 30, 23, 59);
        List<Prenotation> prenotations = List.of(new Prenotation());
        when(prenotationRepository.findByStartTimeBetween(start, end)).thenReturn(prenotations);

        assertThat(prenotationService.getPrenotationsByDateRange(start, end)).isSameAs(prenotations);
    }

    @Test
    void savePrenotation_delegatesToRepository() {
        Prenotation prenotation = new Prenotation();
        when(prenotationRepository.save(prenotation)).thenReturn(prenotation);

        assertThat(prenotationService.savePrenotation(prenotation)).isSameAs(prenotation);
    }

    @Test
    void deletePrenotation_delegatesToRepository() {
        prenotationService.deletePrenotation(5L);

        verify(prenotationRepository).deleteById(5L);
    }
}
