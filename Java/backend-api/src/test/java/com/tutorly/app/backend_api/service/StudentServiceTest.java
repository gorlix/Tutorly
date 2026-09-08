package com.tutorly.app.backend_api.service;

import com.tutorly.app.backend_api.entity.Student;
import com.tutorly.app.backend_api.entity.User;
import com.tutorly.app.backend_api.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudentService#eraseStudent(Student)} - the GDPR-style
 * anonymize-in-place erasure, replacing the old hard delete.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentService studentService;

    private Student student;
    private User linkedGuest;

    @BeforeEach
    void setUp() {
        student = new Student("Marco", "Rossi", "3A", "Excellent in mathematics", "ACTIVE");
        student.setId(7L);

        linkedGuest = new User("guest.parent", "hashedPassword123", "ACTIVE", "GUEST");
        linkedGuest.setId(42L);
        student.setUser(linkedGuest);

        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void eraseStudent_scrubsIdentifyingFields() {
        Student erased = studentService.eraseStudent(student);

        assertThat(erased.getName()).isEqualTo("Erased");
        assertThat(erased.getSurname()).isEqualTo("Student 7");
        assertThat(erased.getDescription()).isNull();
        assertThat(erased.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void eraseStudent_stampsAnonymizedAt() {
        LocalDateTime before = LocalDateTime.now();

        Student erased = studentService.eraseStudent(student);

        assertThat(erased.getAnonymizedAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
    }

    @Test
    void eraseStudent_leavesClassUntouched() {
        Student erased = studentService.eraseStudent(student);

        assertThat(erased.getStudentClass()).isEqualTo("3A");
    }

    @Test
    void eraseStudent_leavesLinkedGuestAccountUntouched() {
        // A GUEST can be linked to more than one student, so erasing this student's own
        // data must never sever or scrub the guardian's account
        Student erased = studentService.eraseStudent(student);

        assertThat(erased.getUser()).isSameAs(linkedGuest);
        assertThat(erased.getUserId()).isEqualTo(42L);
    }

    @Test
    void eraseStudent_savesTheEntity() {
        studentService.eraseStudent(student);

        verify(studentRepository).save(student);
    }
}
