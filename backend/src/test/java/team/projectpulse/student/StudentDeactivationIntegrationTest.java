package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.evaluation.PeerEvaluation;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.evaluation.dto.PeerEvaluationDto;
import team.projectpulse.evaluation.dto.RatingDto;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.student.dto.StudentDto;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student end to end: the deactivate action, its route rule, and the BR-former-team-member
 * consequences of removing her from her team. Seed data: John (4), Eric (5), and Jerry (6) are Team1 (1) in
 * course section 2023-2024 (2) of course 1, whose course admin is Bingyang; Bill is an instructor assigned to that
 * course section; Tim administers another course and is assigned only to its course section.
 */
@DisplayName("Integration tests for UC-STU-deactivate-student")
public class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int ERIC = 5;
    private static final int JERRY = 6;
    private static final int NO_SUCH_ID = 999999;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    PeerEvaluationRepository peerEvaluationRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentJohnToken;
    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(patch(this.baseUrl + "/students/" + studentId + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void deactivateEric() throws Exception {
        deactivate(ERIC, this.instructorBillToken).andExpect(status().isOk());
        this.entityManager.flush();
        this.entityManager.clear();
    }

    private List<RatingDto> ratings() {
        return List.of(
                new RatingDto(null, 1, 8.0),
                new RatingDto(null, 2, 8.0),
                new RatingDto(null, 3, 8.0),
                new RatingDto(null, 4, 8.0),
                new RatingDto(null, 5, 8.0),
                new RatingDto(null, 6, 8.0));
    }

    // Main success scenario

    @Test
    @DisplayName("An assigned instructor deactivates a student: disabled, off her team, off the roster, login refused")
    void testAssignedInstructorDeactivatesStudent() throws Exception {
        deactivate(ERIC, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(ERIC))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
        this.entityManager.flush();
        this.entityManager.clear();

        Student eric = this.studentRepository.findById(ERIC).orElseThrow();
        assertThat(eric.isEnabled()).isFalse();
        assertThat(eric.getTeam()).isNull();

        // The peer evaluation form's roster no longer lists him (BR-former-team-member)
        this.mockMvc.perform(get(this.baseUrl + "/students/teams/1").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", Matchers.not(Matchers.hasItem(ERIC))));

        // He can no longer log in (FR-SEC-active-account)
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("e.hudson@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A course admin not assigned to the student's course section may deactivate her (BR-student-lifecycle)")
    void testUnassignedCourseAdminDeactivatesStudent() throws Exception {
        Section section = this.sectionRepository.findById(2).orElseThrow();
        Instructor bingyang = this.instructorRepository.findById(1).orElseThrow();
        section.removeInstructor(bingyang);
        this.entityManager.flush();
        assertThat(section.getInstructors()).doesNotContain(bingyang);

        deactivate(ERIC, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    // Extension 3a

    @Test
    @DisplayName("3a: deactivating an already deactivated student is a 409 and changes nothing")
    void testDeactivateAlreadyDeactivatedStudent() throws Exception {
        deactivateEric();

        deactivate(ERIC, this.adminBingyangToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        Student eric = this.studentRepository.findById(ERIC).orElseThrow();
        assertThat(eric.isEnabled()).isFalse();
        assertThat(eric.getTeam()).isNull();
    }

    // Route rule

    @Test
    @DisplayName("The student herself may not deactivate herself")
    void testStudentCannotDeactivateHerself() throws Exception {
        deactivate(JOHN, this.studentJohnToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertThat(this.studentRepository.findById(JOHN).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Another student may not deactivate a student")
    void testStudentCannotDeactivateAnotherStudent() throws Exception {
        deactivate(ERIC, this.studentJohnToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    @DisplayName("An instructor of another course section, admin of another course, may not deactivate the student")
    void testInstructorOfAnotherSectionCannotDeactivateStudent() throws Exception {
        deactivate(ERIC, this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertThat(this.studentRepository.findById(ERIC).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("An id that names no student is refused at the route")
    void testDeactivateNonexistentStudent() throws Exception {
        deactivate(NO_SUCH_ID, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    // Work kept (BR-student-lifecycle, BR-former-team-member)

    @Test
    @DisplayName("His weekly activity reports and the peer evaluations he gave and received are kept")
    void testDeactivationKeepsHisWork() throws Exception {
        long givenBefore = this.peerEvaluationRepository.findByWeekAndEvaluatorId("2023-W31", ERIC).size();
        long receivedBefore = this.peerEvaluationRepository.findByWeekAndEvaluateeId("2023-W31", ERIC).size();
        assertThat(givenBefore).isPositive();
        assertThat(receivedBefore).isPositive();

        deactivateEric();

        assertThat(this.peerEvaluationRepository.findByWeekAndEvaluatorId("2023-W31", ERIC)).hasSize((int) givenBefore);
        assertThat(this.peerEvaluationRepository.findByWeekAndEvaluateeId("2023-W31", ERIC)).hasSize((int) receivedBefore);

        // Searching by his former team still finds his weekly activity reports: Activity.team was fixed at submission
        String json = this.jsonMapper.writeValueAsString(Map.of("teamId", "1", "studentId", String.valueOf(ERIC)));
        MultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
        requestParams.add("page", "0");
        requestParams.add("size", "50");
        this.mockMvc.perform(post(this.baseUrl + "/activities/search").contentType(MediaType.APPLICATION_JSON).content(json).params(requestParams).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminBingyangToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.content", Matchers.not(Matchers.empty())));
    }

    // Peer evaluations (BR-former-team-member)

    @Test
    @DisplayName("A former teammate can no longer submit a new peer evaluation of him")
    void testFormerTeammateCannotEvaluateHim() throws Exception {
        deactivateEric();

        // The dev clock is 2023-08-20, in 2023-W33, so 2023-W32 is the open week; the team check refuses before any duplicate check
        PeerEvaluationDto dto = new PeerEvaluationDto(null, "2023-W32", JOHN, "John Smith", ERIC, "Eric Hudson", ratings(), 48.0, "public", "private", null, null);
        this.mockMvc.perform(post(this.baseUrl + "/evaluations").contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(dto)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("The evaluator and evaluatee must be on the same team."));
    }

    @Test
    @DisplayName("A former teammate can no longer edit an existing peer evaluation of him, though an evaluation of a current teammate stays editable")
    void testFormerTeammateCannotEditHisEvaluation() throws Exception {
        PeerEvaluation ofEric = this.peerEvaluationRepository.findByWeekAndEvaluatorIdAndEvaluateeId("2023-W32", JOHN, ERIC).orElseThrow();
        PeerEvaluation ofJerry = this.peerEvaluationRepository.findByWeekAndEvaluatorIdAndEvaluateeId("2023-W32", JOHN, JERRY).orElseThrow();
        Integer ofEricId = ofEric.getPeerEvaluationId();
        Integer ofJerryId = ofJerry.getPeerEvaluationId();

        deactivateEric();

        PeerEvaluationDto editOfEric = new PeerEvaluationDto(ofEricId, "2023-W32", JOHN, "John Smith", ERIC, "Eric Hudson", ratings(), 48.0, "rewritten", "rewritten", null, null);
        this.mockMvc.perform(put(this.baseUrl + "/evaluations/" + ofEricId).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(editOfEric)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("The evaluator and evaluatee must be on the same team."));

        PeerEvaluationDto editOfJerry = new PeerEvaluationDto(ofJerryId, "2023-W32", JOHN, "John Smith", JERRY, "Jerry Moon", ratings(), 48.0, "still editable", "still editable", null, null);
        this.mockMvc.perform(put(this.baseUrl + "/evaluations/" + ofJerryId).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(editOfJerry)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.publicComment").value("still editable"));
    }

    @Test
    @DisplayName("The weekly peer evaluation report lists him as a former member for a week he received evaluations")
    void testReportListsHimAsFormerMember() throws Exception {
        deactivateEric();

        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/2/week/2023-W31").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminBingyangToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.peerEvaluationAverages", Matchers.hasSize(11)))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[10].studentId").value(ERIC))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[10].formerMember").value(true))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[10].teamName").doesNotExist())
                .andExpect(jsonPath("$.data.peerEvaluationAverages[10].averageTotalScore").value(Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[0].formerMember").value(false));
    }

    // The generic update no longer deactivates

    @Test
    @DisplayName("PUT /students/{studentId} with enabled: false leaves her active")
    void testGenericUpdateDoesNotDeactivate() throws Exception {
        StudentDto dto = new StudentDto(ERIC, "e.hudson@abc.edu", "Eric", "Hudson", "e.hudson@abc.edu", false, null, null, null, null, null);
        this.mockMvc.perform(put(this.baseUrl + "/students/" + ERIC).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(dto)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.teamId").value(1));
    }

    @Test
    @DisplayName("An assigned instructor's enabled: true reactivates a deactivated student without restoring her team")
    void testInstructorReactivatesThroughGenericUpdate() throws Exception {
        deactivateEric();

        StudentDto dto = new StudentDto(ERIC, "e.hudson@abc.edu", "Eric", "Hudson", "e.hudson@abc.edu", true, null, null, null, null, null);
        this.mockMvc.perform(put(this.baseUrl + "/students/" + ERIC).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(dto)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
    }

    @Test
    @DisplayName("The student cannot reactivate herself with a token issued before her deactivation")
    void testStudentCannotReactivateHerselfWithAnOldToken() throws Exception {
        deactivateEric();

        StudentDto dto = new StudentDto(ERIC, "e.hudson@abc.edu", "Eric", "Hudson", "e.hudson@abc.edu", true, null, null, null, null, null);
        this.mockMvc.perform(put(this.baseUrl + "/students/" + ERIC).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(dto)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentEricToken));
        this.entityManager.flush();
        this.entityManager.clear();

        assertThat(this.studentRepository.findById(ERIC).orElseThrow().isEnabled()).isFalse();
    }

}
