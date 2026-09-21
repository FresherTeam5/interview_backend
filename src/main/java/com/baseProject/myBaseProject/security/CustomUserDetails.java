package com.baseProject.myBaseProject.security;

import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.entity.UserAccount;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.time.Instant;

@Getter
public class CustomUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String password;
    private final UserRole role;
    private final boolean enabled;

    public CustomUserDetails(UserAccount account){
        this.id = account.getId();
        this.email = account.getEmail();
        this.password = account.getPasswordHash();
        this.role = account.getRole();
        this.enabled = account.canAuthenticateAt(Instant.now());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
