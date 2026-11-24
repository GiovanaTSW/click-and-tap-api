package mx.edu.clickandtap.api.domain.service;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.Role;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.exception.UnauthorizedException;
import mx.edu.clickandtap.api.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    /** BCrypt ignores everything after the first 72 bytes, so longer passwords are rejected. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(String name, String email, String rawPassword, String phone) {
        String normalizedEmail = normalizeEmail(email);
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (tooLong(rawPassword)) {
            throw new BadRequestException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email is already registered");
        }
        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setActive(true);
        return userRepository.save(user);
    }

    public User authenticate(String email, String rawPassword) {
        if (rawPassword == null || tooLong(rawPassword)) {
            throw new UnauthorizedException("Invalid credentials");
        }
        User user = userRepository.getByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return user;
    }

    public User getById(Long id) {
        return userRepository.getById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Locale.ROOT avoids locale bugs such as the Turkish dotless i. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
