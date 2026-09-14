package com.baseProject.myBaseProject.support;

import com.baseProject.myBaseProject.config.properites.AdminBootstrapProperties;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void createsInitialAdministratorWithNormalizedFieldsAndEncodedPassword() {
        Fixture fixture = new Fixture(properties(
                true,
                "  Admin@Example.COM  ",
                "a-strong-admin-password",
                "  System Administrator  "));
        when(fixture.users.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(fixture.users.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        fixture.service.bootstrap();

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(fixture.users).saveAndFlush(captor.capture());
        UserAccount admin = captor.getValue();
        assertThat(admin.getFullName()).isEqualTo("System Administrator");
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getCreatedAt()).isEqualTo(NOW);
        assertThat(admin.getUpdatedAt()).isEqualTo(NOW);
        assertThat(fixture.passwordEncoder.matches(
                "a-strong-admin-password", admin.getPasswordHash())).isTrue();
        assertThat(admin.getPasswordHash()).doesNotContain("a-strong-admin-password");
    }

    @Test
    void doesNothingWhenBootstrapIsDisabled() {
        Fixture fixture = new Fixture(properties(false, null, null, null));

        fixture.service.bootstrap();

        verifyNoInteractions(fixture.users);
    }

    @Test
    void skipsWithoutChangingAnythingWhenAnAdministratorAlreadyExists() {
        Fixture fixture = new Fixture(validProperties());
        when(fixture.users.existsByRole(UserRole.ADMIN)).thenReturn(true);

        fixture.service.bootstrap();

        verify(fixture.users, never()).findByEmail("admin@example.com");
        verify(fixture.users, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesToPromoteAnExistingUser() {
        Fixture fixture = new Fixture(validProperties());
        UserAccount user = UserAccount.builder()
                .id(7L)
                .email("admin@example.com")
                .role(UserRole.USER)
                .enabled(true)
                .build();
        when(fixture.users.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(fixture.users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(fixture.service::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs to a USER account");

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        verify(fixture.users, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidEmailBeforeAccessingTheDatabase() {
        Fixture fixture = new Fixture(properties(
                true,
                "invalid-email",
                "a-strong-admin-password",
                "System Administrator"));

        assertThatThrownBy(fixture.service::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is invalid");

        verifyNoInteractions(fixture.users);
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        Fixture fixture = new Fixture(properties(
                true,
                "admin@example.com",
                "too-short",
                "System Administrator"));

        assertThatThrownBy(fixture.service::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password must be 12-100 characters");

        verifyNoInteractions(fixture.users);
    }

    private static AdminBootstrapProperties validProperties() {
        return properties(
                true,
                "admin@example.com",
                "a-strong-admin-password",
                "System Administrator");
    }

    private static AdminBootstrapProperties properties(
            boolean enabled,
            String email,
            String password,
            String fullName) {
        return new AdminBootstrapProperties(enabled, email, password, fullName);
    }

    private static final class Fixture {
        private final UserAccountRepository users = mock(UserAccountRepository.class);
        private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        private final AdminBootstrapService service;

        private Fixture(AdminBootstrapProperties properties) {
            service = new AdminBootstrapService(
                    users,
                    passwordEncoder,
                    properties,
                    VALIDATOR,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
