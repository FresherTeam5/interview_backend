package com.baseProject.myBaseProject.dto.account;

public record ClientMetadata(String userAgent, String ipAddress) {
    public static ClientMetadata unknown() {
        return new ClientMetadata(null, null);
    }
}
