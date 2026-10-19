package com.cst438.dto;
/*
 * Data Transfer Object for student's score for an assignment
 */
public record GradeDTO(
        Integer gradeId,
        String studentName,
        String studentEmail,
        String assignmentTitle,
        String courseId,
        Integer sectionId,
        Integer score
) {

}
