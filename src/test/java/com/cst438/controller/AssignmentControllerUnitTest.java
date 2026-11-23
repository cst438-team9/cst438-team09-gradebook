package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;
import com.cst438.service.RegistrarServiceProxy;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Random;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class AssignmentControllerUnitTest {
  @Autowired
  private WebTestClient client ;
  @Autowired
  private AssignmentRepository assignmentRepository;
  @Autowired
  private GradeRepository gradeRepository;
  @MockitoBean
  RegistrarServiceProxy registarService;

  @Test
  public void assignmentCreateUpdateDelete() throws Exception {
    //login as instructor and get security token
    // login as admin and get the security token
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
    assignmentDTO = new AssignmentDTO(
        actualAssignment.id(),
        "New Title",
        "2026-11-23",
        null,
        null,
        null
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

    a = assignmentRepository.findById(actualAssignment.id()).orElse(null);
    assertNull(a, "assignment was not deleted");

  }



}
