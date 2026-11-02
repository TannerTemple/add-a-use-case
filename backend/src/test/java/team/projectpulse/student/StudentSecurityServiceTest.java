package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Student john;

    @BeforeEach
    void setUp() {
        Instructor courseAdmin = new Instructor("b.wei@abc.edu", "Bingyang", "Wei", "b.wei@abc.edu", "123456", true, "admin instructor");
        courseAdmin.setId(1);
        Course course = new Course("COSC 40993 Senior Design", "Senior design");
        course.setCourseAdmin(courseAdmin);
        // The course admin is deliberately not assigned to the course section
        Section section = new Section("2023-2024", LocalDate.of(2023, 7, 31), LocalDate.of(2024, 10, 6), true, DayOfWeek.MONDAY, LocalTime.of(23, 59), DayOfWeek.TUESDAY, LocalTime.of(23, 59));
        section.setCourse(course);
        this.john = new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        this.john.setId(4);
        section.addStudent(this.john);
    }

    @Test
    void testCourseAdminNotAssignedToTheSectionIsAdminOfStudentCourse() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(4)).willReturn(Optional.of(this.john));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).isTrue();
    }

    @Test
    void testAnotherInstructorIsNotAdminOfStudentCourse() {
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findById(4)).willReturn(Optional.of(this.john));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).isFalse();
    }

    @Test
    void testUnknownStudentIsDeniedRatherThanThrown() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(99)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(99)).isFalse();
    }

}
