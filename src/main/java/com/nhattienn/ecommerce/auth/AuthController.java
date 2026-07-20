package com.nhattienn.ecommerce.auth;

import com.nhattienn.ecommerce.auth.dto.AuthResponse;
import com.nhattienn.ecommerce.auth.dto.LoginRequest;
import com.nhattienn.ecommerce.auth.dto.RefreshTokenRequest;
import com.nhattienn.ecommerce.auth.dto.RegisterRequest;
import com.nhattienn.ecommerce.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.register(request, clientIp(httpRequest));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Registration successful."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.login(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful."));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/refresh")
public ResponseEntity<ApiResponse<AuthResponse>> refresh(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest httpRequest) {

    AuthResponse response = authService.refresh(request.refreshToken(), clientIp(httpRequest));
    return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed."));
}

@PostMapping("/logout")
public ResponseEntity<ApiResponse<Void>> logout(
        @Valid @RequestBody RefreshTokenRequest request) {

    authService.logout(request.refreshToken());
    return ResponseEntity.ok(ApiResponse.success(null, "Logged out."));
}

@PostMapping("/logout-all")
public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    authService.logoutAll(userId);
    return ResponseEntity.ok(ApiResponse.success(null, "All sessions logged out."));
}
}