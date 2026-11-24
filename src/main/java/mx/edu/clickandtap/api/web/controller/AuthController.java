package mx.edu.clickandtap.api.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.exception.UnauthorizedException;
import mx.edu.clickandtap.api.domain.service.UserService;
import mx.edu.clickandtap.api.web.dto.ApiDto.AuthResponse;
import mx.edu.clickandtap.api.web.dto.ApiDto.LoginRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.RegisterRequest;
import mx.edu.clickandtap.api.web.dto.ApiDto.UserResponse;
import mx.edu.clickandtap.api.web.security.AuthRateLimiter;
import mx.edu.clickandtap.api.web.security.AuthenticatedUser;
import mx.edu.clickandtap.api.web.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthRateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest request) {
        rateLimiter.checkAndRecordRegistration(request.getRemoteAddr());
        User user = userService.register(r.name(), r.email(), r.password(), r.phone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwtUtil.generateToken(user), UserResponse.from(user)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        rateLimiter.checkLogin(ip, r.email());
        User user;
        try {
            user = userService.authenticate(r.email(), r.password());
        } catch (UnauthorizedException e) {
            rateLimiter.recordLoginFailure(ip, r.email());
            throw e;
        }
        rateLimiter.recordLoginSuccess(ip, r.email());
        return new AuthResponse(jwtUtil.generateToken(user), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser auth) {
        return UserResponse.from(userService.getById(auth.id()));
    }
}
