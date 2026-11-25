package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.EnrollmentDTO;
import com.cst438.dto.GradeDTO;
import com.cst438.dto.LoginDTO;
import com.cst438.service.RegistrarServiceProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.sql.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:week4comparison;DB_CLOSE_DELAY=-1")
@AutoConfigureWebTestClient
public class EnrollmentGradeControllerUnitTest {
    @Autowired WebTestClient client;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TermRepository terms;
    @Autowired SectionRepository sections;
    @Autowired EnrollmentRepository enrollments;
    @Autowired AssignmentRepository assignments;
    @Autowired GradeRepository grades;
    @Autowired PasswordEncoder encoder;

    // keep the real database and http server, replace just the remote service for mock
    @MockitoBean RegistrarServiceProxy registrar;

    private int assignmentId;
    private int otherAssignmentId;
    private String instructorToken;
    private String studentToken;

    @BeforeEach
    void setup() {
        // these deletes apply only to this test's private h2 db..
        grades.deleteAll();
        assignments.deleteAll();
        enrollments.deleteAll();
        User instructor = user(90001, "Instructor", "owner@example.test", "INSTRUCTOR");
        User other = user(90002, "Other Instructor", "other@example.test", "INSTRUCTOR");
        User ada = user(90003, "Ada Student", "ada@example.test", "STUDENT");
        User zoe = user(90004, "Zoe Student", "zoe@example.test", "STUDENT");

        Course course = new Course();
        course.setCourseId("test438");
        course.setTitle("Test course");
        course.setCredits(4);
        courses.save(course);
        Term term = new Term();
        term.setTermId(90001);
        term.setYear(2026);
        term.setSemester("Fall");
        term.setAddDate(Date.valueOf("2026-05-01"));
        term.setAddDeadline(Date.valueOf("2026-11-30"));
        term.setDropDeadline(Date.valueOf("2026-11-30"));
        term.setStartDate(Date.valueOf("2026-08-20"));
        term.setEndDate(Date.valueOf("2026-12-17"));
        terms.save(term);
        Section owned = section(90001, instructor, course, term);
        Section foreign = section(90002, other, course, term);
        section(90003, instructor, course, term);
        enrollment(90001, owned, zoe);
        enrollment(90002, owned, ada);
        enrollment(90003, foreign, ada);
        assignmentId = assignment(owned, "First assignment").getAssignmentId();
        otherAssignmentId = assignment(foreign, "Other assignment").getAssignmentId();
        instructorToken = login(instructor.getEmail());
        studentToken = login(ada.getEmail());
    }

    @Test
    void instructorGetsOwnRosterInNameOrder() {
        List<EnrollmentDTO> result = roster(90001);
        assertEquals(2, result.size());
        assertEquals("Ada Student", result.get(0).name());
        assertEquals("Zoe Student", result.get(1).name());
        assertEquals(90002, result.get(0).enrollmentId());
        assertEquals("test438", result.get(0).courseId());
        assertEquals(2026, result.get(0).year());
        assertNull(result.get(0).grade());
        verifyNoInteractions(registrar);
    }

    @Test
    void ownedEmptySectionReturnsEmptyRoster() {
        assertTrue(roster(90003).isEmpty());
        verifyNoInteractions(registrar);
    }

    @Test
    void missingOrOtherInstructorsSectionIsRejected() {
        get("/sections/99999/enrollments").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("section not found");
        get("/sections/90002/enrollments").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("not the instructor for this section");
        verifyNoInteractions(registrar);
    }

    @Test
    void finalGradeIsSavedAndSentToRegistrar() {
        put("/enrollments", List.of(enrollmentDTO(90001, "A")))
                .expectStatus().isOk();
        assertEquals("A", enrollments.findById(90001).orElseThrow().getGrade());
        ArgumentCaptor<EnrollmentDTO> sentEnrollment = ArgumentCaptor.forClass(EnrollmentDTO.class);
        verify(registrar, times(1)).sendMessage(eq("updateEnrollment"), sentEnrollment.capture());
        assertEquals(90001, sentEnrollment.getValue().enrollmentId());
        assertEquals("A", sentEnrollment.getValue().grade());
    }

    @Test
    void finalGradeCanBeCleared() {
        Enrollment enrollment = enrollments.findById(90001).orElseThrow();
        enrollment.setGrade("A");
        enrollments.save(enrollment);
        put("/enrollments", List.of(enrollmentDTO(90001, null))).expectStatus().isOk();
        assertNull(enrollments.findById(90001).orElseThrow().getGrade());
        ArgumentCaptor<EnrollmentDTO> sentEnrollment = ArgumentCaptor.forClass(EnrollmentDTO.class);
        verify(registrar, times(1)).sendMessage(eq("updateEnrollment"), sentEnrollment.capture());
        assertEquals(90001, sentEnrollment.getValue().enrollmentId());
        assertNull(sentEnrollment.getValue().grade());
    }

    @Test
    void invalidEnrollmentUpdatesAreRejected() {
        put("/enrollments", List.of(enrollmentDTO(null, "A"))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("enrollment id is required");
        put("/enrollments", List.of(enrollmentDTO(99999, "A"))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("enrollment not found");
        put("/enrollments", List.of(enrollmentDTO(90001, "TOOLONG"))).expectStatus().isBadRequest();
        // the dto claims our section, but the stored enrollment belongs to someone else
        put("/enrollments", List.of(enrollmentDTO(90003, "F")))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("not the instructor for this section");
        assertNull(enrollments.findById(90001).orElseThrow().getGrade());
        assertNull(enrollments.findById(90003).orElseThrow().getGrade());
        verifyNoInteractions(registrar);
    }

    @Test
    void gradeListCreatesMissingRowsAndReusesTheirIds() {
        List<GradeDTO> first = gradeList();
        assertEquals(2, first.size());
        assertEquals(2, grades.count());
        for (GradeDTO grade : first) {
            assertTrue(grade.gradeId() > 0);
            assertNull(grade.score());
            assertEquals("First assignment", grade.assignmentTitle());
            assertEquals("test438", grade.courseId());
        }
        int id = first.get(0).gradeId();
        put("/grades", List.of(gradeDTO(id, 88))).expectStatus().isOk();
        List<GradeDTO> second = gradeList();
        assertEquals(2, grades.count());
        GradeDTO updated = second.stream().filter(g -> g.gradeId() == id).findFirst().orElseThrow();
        assertEquals(88, updated.score());
        verifyNoInteractions(registrar);
    }

    @Test
    void missingOrOtherInstructorsAssignmentIsRejected() {
        get("/assignments/99999/grades").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("assignment not found");
        get("/assignments/" + otherAssignmentId + "/grades").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("not the instructor for this section");
        assertEquals(0, grades.count());
        verifyNoInteractions(registrar);
    }

    @Test
    void scoreBoundsAndNullAreSavedWithoutSendingFinalGrades() {
        int id = gradeList().get(0).gradeId();
        put("/grades", List.of(gradeDTO(id, 0))).expectStatus().isOk();
        assertEquals(0, grades.findById(id).orElseThrow().getScore());
        put("/grades", List.of(gradeDTO(id, 100))).expectStatus().isOk();
        assertEquals(100, grades.findById(id).orElseThrow().getScore());
        put("/grades", List.of(gradeDTO(id, null))).expectStatus().isOk();
        assertNull(grades.findById(id).orElseThrow().getScore());
        verifyNoInteractions(registrar);
    }

    @Test
    void badScoreAndMissingGradeIdAreRejected() {
        int id = gradeList().get(0).gradeId();
        put("/grades", List.of(gradeDTO(id, -1))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("score must be between 0 and 100");
        put("/grades", List.of(gradeDTO(id, 101))).expectStatus().isBadRequest();
        put("/grades", List.of(gradeDTO(null, 80))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("grade id is required");
        put("/grades", List.of(gradeDTO(99999, 80))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("grade not found");
        assertNull(grades.findById(id).orElseThrow().getScore());
        verifyNoInteractions(registrar);
    }

    @Test
    void scoreUpdateUsesStoredOwnershipNotTheDto() {
        Grade other = new Grade();
        other.setAssignment(assignments.findById(otherAssignmentId).orElseThrow());
        other.setEnrollment(enrollments.findById(90003).orElseThrow());
        other = grades.save(other);
        put("/grades", List.of(gradeDTO(other.getGradeId(), 90))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0]").isEqualTo("not the instructor for this section");
        assertNull(grades.findById(other.getGradeId()).orElseThrow().getScore());
        verifyNoInteractions(registrar);
    }

    @Test
    void studentsCannotUseInstructorEndpoints() {
        client.get().uri("/sections/90001/enrollments").headers(h -> h.setBearerAuth(studentToken))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/assignments/" + assignmentId + "/grades").headers(h -> h.setBearerAuth(studentToken))
                .exchange().expectStatus().isForbidden();
        client.put().uri("/enrollments").headers(h -> h.setBearerAuth(studentToken))
                .bodyValue(List.of(enrollmentDTO(90001, "A"))).exchange().expectStatus().isForbidden();
        client.put().uri("/grades").headers(h -> h.setBearerAuth(studentToken))
                .bodyValue(List.of(gradeDTO(1, 80))).exchange().expectStatus().isForbidden();
        assertNull(enrollments.findById(90001).orElseThrow().getGrade());
        assertEquals(0, grades.count());
        verifyNoInteractions(registrar);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        client.get().uri("/sections/90001/enrollments").exchange().expectStatus().isUnauthorized();
        client.get().uri("/assignments/" + assignmentId + "/grades").exchange().expectStatus().isUnauthorized();
        client.put().uri("/enrollments").bodyValue(List.of(enrollmentDTO(90001, "A")))
                .exchange().expectStatus().isUnauthorized();
        client.put().uri("/grades").bodyValue(List.of(gradeDTO(1, 80)))
                .exchange().expectStatus().isUnauthorized();
        assertNull(enrollments.findById(90001).orElseThrow().getGrade());
        verifyNoInteractions(registrar);
    }

    private WebTestClient.ResponseSpec get(String uri) {
        return client.get().uri(uri).headers(h -> h.setBearerAuth(instructorToken))
                .accept(MediaType.APPLICATION_JSON).exchange();
    }

    private WebTestClient.ResponseSpec put(String uri, Object body) {
        return client.put().uri(uri).headers(h -> h.setBearerAuth(instructorToken))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private List<EnrollmentDTO> roster(int sectionNo) {
        return get("/sections/" + sectionNo + "/enrollments").expectStatus().isOk()
                .expectBodyList(EnrollmentDTO.class).returnResult().getResponseBody();
    }

    private List<GradeDTO> gradeList() {
        return get("/assignments/" + assignmentId + "/grades").expectStatus().isOk()
                .expectBodyList(GradeDTO.class).returnResult().getResponseBody();
    }

    private String login(String email) {
        LoginDTO result = client.get().uri("/login")
                .headers(h -> h.setBasicAuth(email, "local-test-password"))
                .exchange().expectStatus().isOk().expectBody(LoginDTO.class).returnResult().getResponseBody();
        assertNotNull(result);
        assertNotNull(result.jwt());
        return result.jwt();
    }

    private EnrollmentDTO enrollmentDTO(Integer id, String grade) {
        return new EnrollmentDTO(id, grade, null, null, null, null, null,
                null, 90001, null, null, null, null, null, null);
    }

    private GradeDTO gradeDTO(Integer id, Integer score) {
        return new GradeDTO(id, null, null, null, "test438", 1, score);
    }

    private User user(int id, String name, String email, String type) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setType(type);
        user.setPassword(encoder.encode("local-test-password"));
        return users.save(user);
    }

    private Section section(int id, User instructor, Course course, Term term) {
        Section section = new Section();
        section.setSectionNo(id);
        section.setSectionId(id - 90000);
        section.setInstructorEmail(instructor.getEmail());
        section.setCourse(course);
        section.setTerm(term);
        return sections.save(section);
    }

    private void enrollment(int id, Section section, User student) {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentId(id);
        enrollment.setSection(section);
        enrollment.setStudent(student);
        enrollments.save(enrollment);
    }

    private Assignment assignment(Section section, String title) {
        Assignment assignment = new Assignment();
        assignment.setSection(section);
        assignment.setTitle(title);
        assignment.setDueDate(Date.valueOf("2026-11-24"));
        return assignments.save(assignment);
    }
}
