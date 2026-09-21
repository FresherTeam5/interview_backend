package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.Size;

public record AdminSessionActionRequest(
        @Size(max = 200) String reason) {
}
