package com.hookscope.api.config;

import com.hookscope.api.adapters.out.persistence.UserRepository;
import com.hookscope.api.application.IdentityService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(IdentityService.normalizeEmail(email))
            .map(user -> new AppPrincipal(user.id, user.name, user.email, user.passwordHash, user.enabled))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
