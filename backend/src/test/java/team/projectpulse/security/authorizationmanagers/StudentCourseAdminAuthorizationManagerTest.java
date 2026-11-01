package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentCourseAdminAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentCourseAdminAuthorizationManager studentCourseAdminAuthorizationManager;


    private RequestAuthorizationContext contextWith(Map<String, String> variables) {
        return new RequestAuthorizationContext(mock(HttpServletRequest.class), variables);
    }

    @Test
    @DisplayName("Asks whether the caller is the course admin of the bound student's course")
    void delegatesTheBoundStudentId() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(7)).willReturn(true);

        AuthorizationResult result = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of("studentId", "7")));

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies when the caller is not the course admin of the student's course")
    void deniesWhenNotTheCourseAdmin() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(7)).willReturn(false);

        AuthorizationResult result = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of("studentId", "7")));

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Denies when the matched route bound no studentId at all")
    void deniesWhenTheVariableIsAbsent() {
        AuthorizationResult result = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of()));

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
