package com.baseProject.myBaseProject.dto.feedback;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpsertInterviewFeedbackRequest(
        @Min(value = 1, message = "Question rating must be between 1 and 5")
        @Max(value = 5, message = "Question rating must be between 1 and 5")
        Integer questionRating,
        @Min(value = 1, message = "Voice rating must be between 1 and 5")
        @Max(value = 5, message = "Voice rating must be between 1 and 5")
        Integer voiceRating,
        @Min(value = 1, message = "Report rating must be between 1 and 5")
        @Max(value = 5, message = "Report rating must be between 1 and 5")
        Integer reportRating,
        @Size(max = 2000, message = "Feedback comment must not exceed 2000 characters")
        String comment) {

    @AssertTrue(message = "At least one rating or comment is required")
    public boolean isNotEmpty() {
        return questionRating != null || voiceRating != null || reportRating != null
                || (comment != null && !comment.isBlank());
    }
}
