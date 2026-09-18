package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AccountProperties;
import com.baseProject.myBaseProject.service.AccountMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMailServiceImpl implements AccountMailService {
    private final JavaMailSender mailSender;
    private final AccountProperties properties;

    @Override
    public void sendEmailVerification(String recipient, String fullName, String rawToken) {
        String link = link("/verify-email", rawToken);
        send(recipient, "Verify your My Interview email", """
                Hello %s,

                Verify your email by opening this link:
                %s

                If you did not create this account, you can ignore this email.
                """.formatted(fullName, link));
    }

    @Override
    public void sendPasswordReset(String recipient, String fullName, String rawToken) {
        String link = link("/reset-password", rawToken);
        send(recipient, "Reset your My Interview password", """
                Hello %s,

                Reset your password by opening this link:
                %s

                If you did not request this change, you can ignore this email.
                """.formatted(fullName, link));
    }

    @Override
    public void sendNotification(String recipient, String subject, String body) {
        send(recipient, subject, body);
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void send(String recipient, String subject, String body) {
        if (!properties.mailEnabled()) {
            log.info("Account email delivery disabled; skipped '{}' for {}", subject, recipient);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
