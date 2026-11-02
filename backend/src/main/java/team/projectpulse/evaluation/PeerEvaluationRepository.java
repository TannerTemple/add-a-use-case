package team.projectpulse.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import team.projectpulse.student.Student;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Integer> {

    List<PeerEvaluation> findByWeekAndEvaluateeId(String week, Integer studentId);

    Optional<PeerEvaluation> findByWeekAndEvaluatorIdAndEvaluateeId(String week, Integer evaluatorId, Integer evaluateeId);

    List<PeerEvaluation> findByWeekAndEvaluatorId(String week, Integer evaluatorId);

    /**
     * Every (evaluator id, evaluatee id) pair recorded for a week among the course section's students.
     */
    @Query("""
            select e.evaluator.id, e.evaluatee.id
            from PeerEvaluation e
            where e.week = :week
              and e.evaluator.section.sectionId = :sectionId
            """)
    List<Object[]> findEvaluatorAndEvaluateeIds(@Param("sectionId") Integer sectionId, @Param("week") String week);

    /**
     * The course section's former team members for a week (BR-former-team-member): the distinct students of the
     * course section who are on no team now and who received at least one peer evaluation for that week, from any
     * evaluator, self-evaluation included. Ordered by last name, then first name.
     */
    @Query("""
            select distinct s
            from PeerEvaluation e join e.evaluatee s
            where e.week = :week
              and s.section.sectionId = :sectionId
              and s.team is null
            order by s.lastName, s.firstName
            """)
    List<Student> findFormerMembersEvaluatedInWeek(@Param("sectionId") Integer sectionId, @Param("week") String week);

}
