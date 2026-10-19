package com.cst438.dto;

import java.sql.Date;
/*
 * Data Transfer Object for assignment data including student's grade
 */
public record AssignmentStudentDTO(
        Integer assignmentId,
        String title,
        //Date dueDate,
        String dueDate,
        String courseId,
        Integer sectionId,
        Integer score
) {
}
