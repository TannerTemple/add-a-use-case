-- Remove every already-deactivated student from her team (BR-student-lifecycle).
-- Before UC-STU-deactivate-student was built, deactivation was the `enabled` switch on the
-- generic Edit Student form, which left the student on her team. Deactivation now removes her
-- from her team, so staging and prod may hold disabled students still on a team: they would
-- stay on rosters and on the peer evaluation form. Clearing the team makes them former members
-- (BR-former-team-member), labeled in the weekly peer evaluation report for the weeks in which
-- they received peer evaluations.
-- Data only; no schema change. Active students keep their teams.

UPDATE `peer_evaluation_user`
SET `team_team_id` = NULL
WHERE `dtype` = 'Student'
  AND `enabled` = b'0'
  AND `team_team_id` IS NOT NULL;
