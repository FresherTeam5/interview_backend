package com.baseProject.myBaseProject.service;

public interface AccountMailService {
    void sendEmailVerification(String recipient, String fullName, String rawToken);

    void sendPasswordReset(String recipient, String fullName, String rawToken);

    void sendNotification(String recipient, String subject, String body);
}
