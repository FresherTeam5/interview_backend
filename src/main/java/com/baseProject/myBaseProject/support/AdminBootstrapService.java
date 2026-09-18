package com.baseProject.myBaseProject.support;

import com.baseProject.myBaseProject.config.properites.AdminBootstrapProperties;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public void bootstrap() {
        if (!properties.enabled()) {
            return;
        }

        Credentials credentials = validatedCredentials();

        if (users.existsByRole(UserRole.ADMIN)) {
            log.info("Admin bootstrap skipped because an administrator already exists");
            return;
        }

        if (users.findByEmail(credentials.email()).isPresent()) {
            throw new IllegalStateException(
                    "Cannot bootstrap administrator: configured email already belongs to a USER account");
        }

        Instant now = clock.instant();
        UserAccount admin = UserAccount.builder()
                .fullName(credentials.fullName())
                .email(credentials.email())
                .passwordHash(passwordEncoder.encode(credentials.password()))
                .role(UserRole.ADMIN)
                .enabled(true)
                .emailVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        users.saveAndFlush(admin);
        log.info("Initial administrator account created for email {}", credentials.email());
    }

    private Credentials validatedCredentials() {
        Credentials credentials = new Credentials(
                normalizeFullName(properties.fullName()),
                normalizeEmail(properties.email()),
                properties.password());
        Set<ConstraintViolation<Credentials>> violations = validator.validate(credentials);

        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .sorted(Comparator.comparing(violation ->
                            violation.getPropertyPath().toString()))
                    .map(violation -> violation.getPropertyPath() + " "
                            + violation.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Invalid app.admin-bootstrap configuration: " + details);
        }

        return credentials;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName == null ? null : fullName.trim();
    }

    private record Credentials(
            @NotBlank(message = "full-name is required")
            @Size(max = 150, message = "full-name must not exceed 150 characters")
            String fullName,

            @NotBlank(message = "email is required")
            @Email(message = "email is invalid")
            @Size(max = 150, message = "email must not exceed 150 characters")
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 12, max = 100,
                    message = "password must be 12-100 characters")
            String password
    ) {
    }
}
