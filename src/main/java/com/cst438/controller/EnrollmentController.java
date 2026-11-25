package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.EnrollmentDTO;
import com.cst438.service.RegistrarServiceProxy;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import java.util.List;
import java.util.ArrayList;

@RestController
public class EnrollmentController {

    private final EnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final RegistrarServiceProxy registrar;

    public EnrollmentController (
            EnrollmentRepository enrollmentRepository,
            SectionRepository sectionRepository,
            RegistrarServiceProxy registrar
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.sectionRepository = sectionRepository;
        this.registrar = registrar;
    }


    // instructor gets student enrollments with grades for a section
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    @GetMapping("/sections/{sectionNo}/enrollments")
    public List<EnrollmentDTO> getEnrollments(
            @PathVariable("sectionNo") int sectionNo, Principal principal ) {
        Section section = sectionRepository.findById(sectionNo).orElse(null);
        if (section == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "section not found");
        }
        if (!principal.getName().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not the instructor for this section");
        }
        List<EnrollmentDTO> result = new ArrayList<>();
        for (Enrollment enrollment : enrollmentRepository.findEnrollmentsBySectionNoOrderByStudentName(sectionNo)) {
            result.add(toDTO(enrollment));
        }
        return result;
    }

    // instructor updates enrollment grades
    @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
    @PutMapping("/enrollments")
    public void updateEnrollmentGrade(@Valid @RequestBody List<EnrollmentDTO> dtoList, Principal principal) {
        // check the whole list before saving anything or sending messages
        List<Enrollment> enrollments = new ArrayList<>();
        for (EnrollmentDTO dto : dtoList) {
            if (dto == null || dto.enrollmentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enrollment id is required");
            }
            Enrollment enrollment = enrollmentRepository.findById(dto.enrollmentId()).orElse(null);
            if (enrollment == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enrollment not found");
            }
            if (!principal.getName().equals(enrollment.getSection().getInstructorEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not the instructor for this section");
            }
            if (dto.grade() != null && dto.grade().length() > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grade must be at most 5 characters");
            }
            enrollments.add(enrollment);
        }
        for (int i = 0; i < dtoList.size(); i++) {
            Enrollment enrollment = enrollments.get(i);
            enrollment.setGrade(dtoList.get(i).grade());
            enrollmentRepository.save(enrollment);
            registrar.sendMessage("updateEnrollment", toDTO(enrollment));
        }
    }

    private EnrollmentDTO toDTO(Enrollment enrollment) {
        Section section = enrollment.getSection();
        User student = enrollment.getStudent();
        return new EnrollmentDTO(enrollment.getEnrollmentId(), enrollment.getGrade(),
                student.getId(), student.getName(), student.getEmail(),
                section.getCourse().getCourseId(), section.getCourse().getTitle(),
                section.getSectionId(), section.getSectionNo(), section.getBuilding(),
                section.getRoom(), section.getTimes(), section.getCourse().getCredits(),
                section.getTerm().getYear(), section.getTerm().getSemester());
    }
}
