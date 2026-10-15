package com.hookscope.api.config;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppPrincipal(UUID id, String name, String email, String passwordHash, boolean enabled) implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public String toString() { return "AppPrincipal[id=" + id + ", email=" + email + "]"; }
}
