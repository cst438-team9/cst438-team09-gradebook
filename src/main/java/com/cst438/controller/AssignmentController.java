package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.AssignmentDTO;
import com.cst438.dto.AssignmentStudentDTO;
import com.cst438.dto.SectionDTO;
import jakarta.validation.Valid;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


import java.security.Principal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@RestController
public class AssignmentController {

    private final SectionRepository sectionRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;

    public AssignmentController(
            SectionRepository sectionRepository,
            AssignmentRepository assignmentRepository,
            GradeRepository gradeRepository,
            UserRepository userRepository
    ) {
        this.sectionRepository = sectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.gradeRepository = gradeRepository;
        this.userRepository = userRepository;
    }

    // get Sections for an instructor
    @GetMapping("/sections")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public List<SectionDTO> getSectionsForInstructor(
            @RequestParam("year") int year ,
            @RequestParam("semester") String semester,
            Principal principal)  {
        // return the Sections that have instructorEmail for the 
		// logged in instructor user for the given term.
      User u = userRepository.findByEmail(principal.getName());
      List <Section> sections = sectionRepository.findByInstructorEmailAndYearAndSemester(principal.getName(), year, semester);
      return sections.stream().map((section) -> new SectionDTO(
          section.getSectionNo(),
          section.getTerm().getYear(),
          section.getTerm().getSemester(),
          section.getCourse().getCourseId(),
          section.getCourse().getTitle(),
          section.getSectionId(),
          section.getBuilding(),
          section.getRoom(),
          section.getTimes(),
          u.getName(),
          section.getInstructorEmail()
      )).toList();
    }

    // instructor lists assignments for a section.
    @GetMapping("/sections/{secNo}/assignments")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public List<AssignmentDTO> getAssignments(
            @PathVariable("secNo") int secNo,
            Principal principal) {

        // verify that user is the instructor for the section
      Section section = sectionRepository.findById(secNo).orElse(null);
      if(section == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "section does not exist");
      } else if (!principal.getName().equals(section.getInstructorEmail())){
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you do not have access");
      }
        //  return list of assignments for the Section
      List<Assignment> assignments = section.getAssignments();
      return assignments.stream().map((assignment -> new AssignmentDTO(
          assignment.getAssignmentId(),
          assignment.getTitle(),
          assignment.getDueDate().toString(),
          assignment.getSection().getCourse().getCourseId(),
          assignment.getSection().getSectionId(),
          assignment.getSection().getSectionNo()
      ))).toList();
    }


    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public AssignmentDTO createAssignment(
            @Valid @RequestBody AssignmentDTO dto,
            Principal principal) {
        
        //  user must be the instructor for the Section
      Section section = sectionRepository.findById(dto.secNo()).orElse(null);
      if(section == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "section does not exist");
      } else if (!principal.getName().equals(section.getInstructorEmail())){
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you do not have access");
      }
		//  check that assignment dueDate is between start date and 
		//  end date of the term
      Date dueDate = Date.valueOf(dto.dueDate());
      if(dueDate.before(section.getTerm().getStartDate()) || dueDate.after(section.getTerm().getEndDate())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due Date must be between start and end date");
      }
		//  create and save an Assignment entity
      Assignment assignment = new Assignment();
      assignment.setSection(section);
      assignment.setDueDate(dueDate);
      assignment.setTitle(dto.title());
      assignmentRepository.save(assignment);
        //  return AssignmentDTO with database generated primary key
        return new AssignmentDTO(
            assignment.getAssignmentId(),
            assignment.getTitle(),
            assignment.getDueDate().toString(),
            assignment.getSection().getCourse().getCourseId(),
            assignment.getSection().getSectionId(),
            assignment.getSection().getSectionNo()
        );
    }


    @PutMapping("/assignments")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public AssignmentDTO updateAssignment(@Valid @RequestBody AssignmentDTO dto, Principal principal) {
        //  update Assignment Entity.  only title and dueDate fields can be changed.
      //get assignment to be updated
      Assignment assignment = assignmentRepository.findById(dto.id()).orElse(null);
      if(assignment == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment not found");
      }
      //  user must be instructor of the Section
      Section section = assignment.getSection();
      if(section == null || !principal.getName().equals(section.getInstructorEmail())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you do not have access");
      }
    // validate due date
      Date dueDate = Date.valueOf(dto.dueDate());
      if(dueDate.before(section.getTerm().getStartDate()) || dueDate.after(section.getTerm().getEndDate())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due Date must be between start and end date");
      }
      // update assignment
      assignment.setTitle(dto.title());
      assignment.setDueDate(dueDate);
      assignmentRepository.save(assignment);
      //return update assignment
      return new AssignmentDTO(
          assignment.getAssignmentId(),
          assignment.getTitle(),
          assignment.getDueDate().toString(),
          assignment.getSection().getCourse().getCourseId(),
          assignment.getSection().getSectionId(),
          assignment.getSection().getSectionNo()
      );
    }


    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public void deleteAssignment(@PathVariable("assignmentId") int assignmentId, Principal principal) {
        // verify that user is the instructor of the section
      Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
      if(assignment == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment not found");
      }
      Section section = assignment.getSection();
      if(section == null || !principal.getName().equals(section.getInstructorEmail())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you do not have access");
      }
        // delete the Assignment entity
      assignmentRepository.deleteById(assignmentId);
        
    }

    // student lists their assignments/grades  ordered by due date
    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_STUDENT')")
    public List<AssignmentStudentDTO> getStudentAssignments(
            @RequestParam("year") int year,
            @RequestParam("semester") String semester,
            Principal principal) {

        //  return AssignmentStudentDTOs with scores of a 
		//  Grade entity exists.
		//  hint: use the GradeRepository findByStudentEmailAndAssignmentId
        //  If assignment has not been graded, return a null score.
      // get list of student assignments
      List<Assignment> assignments  = assignmentRepository.findByStudentEmailAndYearAndSemester(principal.getName(), year, semester);

      //  return list of assignments for the student
      return assignments.stream().sorted(Comparator.comparing(Assignment::getDueDate)).map(assignment -> {
        Grade grade = gradeRepository.findByStudentEmailAndAssignmentId(principal.getName(), assignment.getAssignmentId());

        return new AssignmentStudentDTO(
            assignment.getAssignmentId(),
            assignment.getTitle(),
            assignment.getDueDate().toString(),
            assignment.getSection().getCourse().getCourseId(),
            assignment.getSection().getSectionId(),
            grade == null? null : grade.getScore()
        );
      }).toList();
    }
}
