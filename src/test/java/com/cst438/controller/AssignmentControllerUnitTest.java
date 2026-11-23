package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;
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

    //create a new assignment for section cst489
    //assignment information is sent as AssignmentDTO
    //An updated AssignmentDTO is returned if assignment created successfully
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment", //Title
        "2026-11-22", //dueDate
        "cst489", //courseId
        1, //section Id
        1 //section number
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
  public void assignmentInputValidation(){
    //login as instructor and get security token
    String jwt = loginInstructor();
    assertNotNull(jwt);

    //create a new assignment for section cst489
    //invalid assignment title is sent
    AssignmentDTO assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment$$", //Title
        "2026-11-22", //dueDate
        "cst489", //courseId
        1, //section Id
        1 //section number
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
    // invalid assignment date is create
    assignmentDTO = new AssignmentDTO(
        0, //key automatically generated
        "First Assignment", //Title
        "2027-11-22", //dueDate
        "cst489", //courseId
        1, //section Id
        1 //section number
    );
    //expect due date error message
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
  public void getStudentAssignments() throws Exception{
    String jwt = loginStudent();
    assertNotNull(jwt);

    // Get student's assignments
    client.get().uri("/assignments?year=2026&semester=Fall")
        .headers(headers -> headers.setBearerAuth(jwt))
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(AssignmentStudentDTO.class);

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


