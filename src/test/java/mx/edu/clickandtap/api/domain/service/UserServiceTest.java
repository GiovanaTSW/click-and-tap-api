package mx.edu.clickandtap.api.domain.service;

import mx.edu.clickandtap.api.domain.Role;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.exception.UnauthorizedException;
import mx.edu.clickandtap.api.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    // ---------- register ----------

    @Test
    void registerNormalizesEmailHashesPasswordAndCreatesACustomer() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.register("  Ana  ", "  Ana@Example.COM ", "password123", "555");

        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void registerCannotCreateAnAdmin() {
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.register("Ana", "ana@example.com", "password123", null).getRole())
                .isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmails() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ana", "ANA@example.com", "password123", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsShortPasswords() {
        assertThatThrownBy(() -> service.register("Ana", "ana@example.com", "short", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 8");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerRejectsPasswordsBcryptWouldSilentlyTruncate() {
        String tooLong = "a".repeat(73);

        assertThatThrownBy(() -> service.register("Ana", "ana@example.com", tooLong, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void registerCountsBytesNotCharacters() {
        // 37 two-byte characters = 74 bytes, even though it is only 37 characters long.
        String multibyte = "ñ".repeat(37);

        assertThatThrownBy(() -> service.register("Ana", "ana@example.com", multibyte, null))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------- authenticate ----------

    @Test
    void authenticateReturnsTheUserWhenCredentialsMatch() {
        User stored = user(true);
        when(userRepository.getByEmail("ana@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThat(service.authenticate(" ANA@example.com ", "password123")).isSameAs(stored);
    }

    @Test
    void authenticateFailsWithTheSameMessageForWrongPasswordAndUnknownEmail() {
        when(userRepository.getByEmail("ana@example.com")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);
        when(userRepository.getByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ana@example.com", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> service.authenticate("nobody@example.com", "whatever123"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
    }

    @Test
    void authenticateRejectsInactiveUsersEvenWithTheRightPassword() {
        when(userRepository.getByEmail("ana@example.com")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.authenticate("ana@example.com", "password123"))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticateRejectsOversizedPasswordsWithoutQueryingTheDatabase() {
        assertThatThrownBy(() -> service.authenticate("ana@example.com", "a".repeat(100)))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    // ---------- getById ----------

    @Test
    void getByIdOfUnknownUserIsNotFound() {
        when(userRepository.getById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(NotFoundException.class);
    }

    private User user(boolean active) {
        User u = new User();
        u.setId(1L);
        u.setEmail("ana@example.com");
        u.setPasswordHash("hashed");
        u.setRole(Role.CUSTOMER);
        u.setActive(active);
        return u;
    }
}
