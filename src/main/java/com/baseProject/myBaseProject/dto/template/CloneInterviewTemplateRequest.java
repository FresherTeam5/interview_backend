package com.baseProject.myBaseProject.dto.template;

import jakarta.validation.constraints.Size;

public record CloneInterviewTemplateRequest(
        @Size(max = 200, message = "Template title must not exceed 200 characters")
        String title) {
}
