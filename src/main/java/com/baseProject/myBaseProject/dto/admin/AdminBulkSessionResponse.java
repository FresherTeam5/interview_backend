package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.util.List;

public record AdminBulkSessionResponse(
        int requested,
        int succeeded,
        int failed,
        List<Item> items) {

    public record Item(
            Long sessionId,
            boolean success,
            InterviewSessionStatus status,
            String errorCode,
            String errorMessage) {
    }
}
