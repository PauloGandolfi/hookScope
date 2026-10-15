package com.hookscope.api.adapters.in.http;

import com.hookscope.api.adapters.out.persistence.UserEntity;
import com.hookscope.api.application.IdentityService;
import com.hookscope.api.application.ApiException;
import com.hookscope.api.config.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identity;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;

    public AuthController(IdentityService identity, AuthenticationManager authenticationManager, SecurityContextRepository contexts) {
        this.identity = identity;
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(new CsrfResponse(token.getToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = identity.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user.id, user.name, user.email));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        try {
            var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(IdentityService.normalizeEmail(request.email()), request.password()));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            servletRequest.getSession(true);
            servletRequest.changeSessionId();
            contexts.saveContext(context, servletRequest, servletResponse);
            AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();
            return new UserResponse(principal.id(), principal.name(), principal.email());
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "E-mail ou senha inválidos.");
        }
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return new UserResponse(principal.id(), principal.name(), principal.email());
    }

    public record CsrfResponse(String token) {}
    public record RegisterRequest(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Email @Size(max = 320) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "RegisterRequest[email=" + email + ", password=REDACTED]"; }
    }
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
        @Override public String toString() { return "LoginRequest[email=" + email + ", password=REDACTED]"; }
    }
    public record UserResponse(UUID id, String name, String email) {}
}
