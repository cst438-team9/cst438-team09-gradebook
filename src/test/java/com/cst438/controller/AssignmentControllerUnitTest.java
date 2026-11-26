package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;
import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class AssignmentControllerUnitTest {
  @Autowired
  private WebTestClient client ;
  @Autowired
  private AssignmentRepository assignmentRepository;
  @Autowired
  private SectionRepository sectionRepository;

  @Test
  public void getInstructorSectionsAssignments() throws Exception {

    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Get instructor's sections
    client.get().uri("/sections?year=2026&semester=Fall")
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(SectionDTO.class)
        .hasSize(1);

    // Get assignments for instructor section 1
    client.get().uri("/sections/1/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(AssignmentDTO.class);
  }

  @Test
  public void assignmentCreateUpdateDelete() throws Exception {
    //login as instructor and get security token
    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Get a section taught by the instructor
    Section section = sectionRepository.findByInstructorEmailAndYearAndSemester("ted@csumb.edu", 2026, "Fall").get(0);
    assertNotNull(section);

    //create a new assignment for section cst489
    //assignment information is sent as AssignmentDTO
    //An updated AssignmentDTO is returned if assignment created successfully
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment", //Title
        "2026-11-22", //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        section.getSectionNo() //section number
    );

    EntityExchangeResult<AssignmentDTO> response = client.post().uri("/assignments")
            .headers(headers -> headers.setBearerAuth(jwt))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(assignmentDTO)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AssignmentDTO.class).returnResult();

    AssignmentDTO actualAssignment = response.getResponseBody();

    assertTrue(actualAssignment.id() > 0);
    assertNotNull(actualAssignment);

    // check that the new assignment was sent to the database
    Assignment a = assignmentRepository.findById(actualAssignment.id()).orElse(null);
    assertNotNull(a, "assignment was created, but is not in the database");

    // update the assignment dueDate and titile
    //An updated AssignmentDTO is returned if assignment updated successfully
    assignmentDTO = new AssignmentDTO(
        actualAssignment.id(),  // assignment id
        "New Title",            // new title
        "2026-11-23",           // new date
        null,                   // course id not updated
        null,                   // section id not update
        null                    // section number not updated
    );

    response = client.put().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(AssignmentDTO.class).returnResult();

    actualAssignment = response.getResponseBody();
    // verify change in the database
    a = assignmentRepository.findById(actualAssignment.id()).orElse(null);
    assertNotNull(a);
    assertEquals("New Title", a.getTitle(), "Title was not changed");
    assertEquals( "2026-11-23", a.getDueDate().toString(), "Due date was not changed");

    //delete the assignment
    client.delete().uri("/assignments/"+actualAssignment.id())
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus().isOk();

    //Verify asssignment was deleted
    a = assignmentRepository.findById(actualAssignment.id()).orElse(null);
    assertNull(a, "assignment was not deleted");

  }

  @Test
  public void assignmentTitleSectionInputValidation(){
    //login as instructor and get security token
    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Get a section taught by the instructor
    Section section = sectionRepository.findByInstructorEmailAndYearAndSemester("ted@csumb.edu", 2026, "Fall").get(0);
    assertNotNull(section);

    //create a new assignment for section cst489
    //invalid assignment title is sent
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment$$", //Title
        "2026-11-22", //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        section.getSectionNo() //section number
    );
    //expect title error message
    EntityExchangeResult<byte[]> response = client.post().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='invalid char in title')]").exists().returnResult();

    //create a new assignment for section cst489
    //invalid section is sent
    assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment", //Title
        "2026-11-22", //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        null //section number
    );
    //expect section required error
    response = client.post().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='Section number missing')]").exists().returnResult();
  }

  @Test
  public void assignmentDateInputValidation() {
    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Get a section taught by the instructor
    Section section = sectionRepository.findByInstructorEmailAndYearAndSemester("ted@csumb.edu", 2026, "Fall").get(0);
    assertNotNull(section);

    // Test missing date
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        0,
        "First Assignment", //Title
        null,  //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        section.getSectionNo() //section number
    );

    EntityExchangeResult<byte[]> response = client.post().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='Invalid due date')]").exists().returnResult();

    // Test Incorrect date
    assignmentDTO = new AssignmentDTO(
        0,
        "First Assignment", //Title
        "11/22/2026", //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        section.getSectionNo() //section number
    );

    response = client.post().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='Invalid due date')]").exists().returnResult();

    // Test due date outside of term
    assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment", //Title
        "2000-11-22", //dueDate
        section.getCourse().getCourseId(),  //course ID
        section.getSectionId(), //section ID
        section.getSectionNo() //section number
    );
    response = client.post().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='Due Date must be between start and end date')]").exists().returnResult();
  }

  @Test
  public void updateAssignmentMissingId() {

    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Update an assignment without an ID
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        null,
        "Updated Assignment",
        "2026-11-22",
        null,
        null,
        null
    );

    // Expect error message
    EntityExchangeResult<byte[]> response = client.put().uri("/assignments")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(assignmentDTO)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors[?(@=='Assignment ID missing')]").exists().returnResult();
  }

  @Test
  public void instructorUnauthorizedAssignmentAccess() {
    String jwt = loginInstructor();
    assertNotNull(jwt);

    // Get a section taught by another instructor
    Section section = sectionRepository.findByInstructorEmailAndYearAndSemester("ben@csumb.edu", 2026, "Fall").get(0);
    assertNotNull(section);

    // Create an assignment belonging to that instructor
    Assignment assignment = new Assignment();
    assignment.setSection(section);
    assignment.setTitle("Other Instructor Assignment");
    assignment.setDueDate(section.getTerm().getStartDate());

    assignment = assignmentRepository.save(assignment);
    int assignmentId = assignment.getAssignmentId();

    // Test assignments cannot be accessed by other users
      client.get().uri("/sections/" +
              section.getSectionNo() + "/assignments")
          .headers(headers -> headers.setBearerAuth(jwt))
          .exchange()
          .expectStatus().isBadRequest()
          .expectBody()
          .jsonPath("$.errors[?(@=='you do not have access')]").exists().returnResult();

      // test assignment cannot be updated by others
      AssignmentDTO assignmentDTO = new AssignmentDTO(
          assignmentId,
          "Updated Assignment",
          section.getTerm().getStartDate().toString(),
          section.getCourse().getCourseId(),
          section.getSectionId(),
          section.getSectionNo()
      );

      client.put().uri("/assignments")
          .headers(headers -> headers.setBearerAuth(jwt))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(assignmentDTO)
          .exchange()
          .expectStatus().isBadRequest()
          .expectBody()
          .jsonPath("$.errors[?(@=='you do not have access')]").exists().returnResult();

      // Verify assignment was not updated
      Assignment a = assignmentRepository.findById(assignmentId).orElse(null);
      assertNotNull(a);
      assertEquals("Other Instructor Assignment", a.getTitle());

      // test instructor can only delete their assignment
      client.delete().uri("/assignments/" + assignmentId)
          .headers(headers -> headers.setBearerAuth(jwt))
          .exchange()
          .expectStatus().isBadRequest()
          .expectBody()
          .jsonPath("$.errors[?(@=='you do not have access')]").exists().returnResult();

      // Verify assignment was not deleted
      a = assignmentRepository.findById(assignmentId).orElse(null);
      assertNotNull(a, "Unauthorized assignment was deleted");

      assignmentRepository.deleteById(assignmentId);
  }

  @Test
  public void getStudentAssignments() throws Exception{
    String jwt = loginStudent();
    assertNotNull(jwt);

    Section section = sectionRepository.findById(1).orElseThrow();

    //Create two assignments
    Assignment a1 = new Assignment();
    a1.setTitle("First Assignment");
    a1.setDueDate(Date.valueOf("2026-11-20"));
    a1.setSection(section);
    a1 = assignmentRepository.save(a1);

    Assignment a2 = new Assignment();
    a2.setTitle("Second Assignment");
    a2.setDueDate(Date.valueOf("2026-11-22"));
    a2.setSection(section);
    a2 = assignmentRepository.save(a2);

    // Get student's assignments
    client.get().uri("/assignments?year=2026&semester=Fall")
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(AssignmentStudentDTO.class)
        .value(assignments -> {
          assertTrue(assignments.stream()
              .anyMatch(a -> a.title().equals("First Assignment")));

          assertTrue(assignments.stream()
              .anyMatch(a -> a.title().equals("Second Assignment")));
        });

  }

  public String loginInstructor() {
    //login as instructor and get security token
    String instructorEmail = "ted@csumb.edu";
    String password = "ted2025";

    EntityExchangeResult<LoginDTO> login_dto =  client.get().uri("/login")
        .headers(headers -> headers.setBasicAuth(instructorEmail, password))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoginDTO.class).returnResult();

    String jwt = login_dto.getResponseBody().jwt();
    assertNotNull(jwt);

    return jwt;
  }

  public String loginStudent() {
    //login as student and get security token
    String studentEmail = "sam@csumb.edu";
    String password = "sam2025";

    EntityExchangeResult<LoginDTO> login_dto =  client.get().uri("/login")
        .headers(headers -> headers.setBasicAuth(studentEmail, password))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoginDTO.class).returnResult();

    String jwt = login_dto.getResponseBody().jwt();
    assertNotNull(jwt);

    return jwt;
  }

}


