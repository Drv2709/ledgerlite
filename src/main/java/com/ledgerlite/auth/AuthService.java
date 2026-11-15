package com.ledgerlite.auth;

import com.ledgerlite.auth.dto.LoginRequest;
import com.ledgerlite.auth.dto.RegisterRequest;
import com.ledgerlite.auth.dto.UserResponse;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.user.AuthenticatedUser;
import com.ledgerlite.user.Role;
import com.ledgerlite.user.User;
import com.ledgerlite.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    /** The user to return in the body and the JWT to put in the cookie (never in the body). */
    public record AuthResult(UserResponse user, String token) {}

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        User saved;
        try {
            saved = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()), Role.USER));
        } catch (DataIntegrityViolationException ex) {
            // Two registrations racing: the UNIQUE constraint is the final authority.
            throw emailTaken();
        }
        AuthenticatedUser principal = AuthenticatedUser.from(saved);
        return new AuthResult(toResponse(principal), jwtService.generateToken(principal));
    }

    public AuthResult login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalize(request.email()), request.password()));
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            return new AuthResult(toResponse(principal), jwtService.generateToken(principal));
        } catch (AuthenticationException ex) {
            // Same message for unknown email and wrong password (no user enumeration).
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unauthorized",
                    "Invalid email or password");
        }
    }

    public UserResponse toResponse(AuthenticatedUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered",
                "An account with this email already exists.");
    }
}
