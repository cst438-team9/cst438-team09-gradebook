package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.GradeDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.ArrayList;

@RestController
public class GradeController {
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;

    public GradeController (
            AssignmentRepository assignmentRepository,
            GradeRepository gradeRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.gradeRepository = gradeRepository;
    }
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    @GetMapping("/assignments/{assignmentId}/grades")
    public List<GradeDTO> getAssignmentGrades(@PathVariable("assignmentId") int assignmentId, Principal principal) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignment not found");
        }
        Section section = assignment.getSection();
        if (!principal.getName().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not the instructor for this section");
        }
        List<GradeDTO> result = new ArrayList<>();
        for (Enrollment enrollment : section.getEnrollments()) {
            User student = enrollment.getStudent();
            Grade grade = gradeRepository.findByStudentEmailAndAssignmentId(student.getEmail(), assignmentId);
            if (grade == null) {
                // an ungraded assignment needs an id but should not show a zero
                grade = new Grade();
                grade.setAssignment(assignment);
                grade.setEnrollment(enrollment);
                grade.setScore(null);
                grade = gradeRepository.save(grade);
            }
            result.add(new GradeDTO(grade.getGradeId(), student.getName(), student.getEmail(),
                    assignment.getTitle(), section.getCourse().getCourseId(),
                    section.getSectionId(), grade.getScore()));
        }
        return result;
    }


    @PutMapping("/grades")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    public void updateGrades(@Valid @RequestBody List<GradeDTO> dtoList, Principal principal) {
        List<Grade> grades = new ArrayList<>();
        for (GradeDTO dto : dtoList) {
            if (dto == null || dto.gradeId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grade id is required");
            }
            Grade grade = gradeRepository.findById(dto.gradeId()).orElse(null);
            if (grade == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grade not found");
            }
            if (!principal.getName().equals(grade.getAssignment().getSection().getInstructorEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not the instructor for this section");
            }
            if (dto.score() != null && (dto.score() < 0 || dto.score() > 100)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be between 0 and 100");
            }
            grades.add(grade);
        }
        // assignment scores stay in gradebook, only final course grades go to registrar
        for (int i = 0; i < dtoList.size(); i++) {
            Grade grade = grades.get(i);
            grade.setScore(dtoList.get(i).score());
            gradeRepository.save(grade);
        }
    }
}
