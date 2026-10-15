package com.hookscope.api.application;

import com.hookscope.api.adapters.out.persistence.UserEntity;
import com.hookscope.api.adapters.out.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public IdentityService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserEntity register(String name, String email, String password) {
        String normalized = normalizeEmail(email);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "A senha deve ter no máximo 72 bytes.");
        }
        if (users.existsByEmail(normalized)) {
            throw registrationFailed();
        }
        try {
            return users.saveAndFlush(new UserEntity(name.trim(), normalized, passwords.encode(password)));
        } catch (DataIntegrityViolationException exception) {
            throw registrationFailed();
        }
    }

    private static ApiException registrationFailed() {
        return new ApiException(HttpStatus.CONFLICT, "registration_failed", "Não foi possível criar a conta com os dados informados.");
    }
}
