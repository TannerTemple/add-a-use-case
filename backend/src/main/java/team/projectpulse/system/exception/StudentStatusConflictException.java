package team.projectpulse.system.exception;

/**
 * A change of a student's account status that her current status rules out, such as deactivating a student who is
 * already deactivated (UC-STU-deactivate-student, extension 3a). Nothing is changed.
 */
public class StudentStatusConflictException extends RuntimeException {

    public StudentStatusConflictException(String message) {
        super(message);
    }

}
