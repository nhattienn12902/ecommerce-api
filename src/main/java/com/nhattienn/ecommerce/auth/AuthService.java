package com.nhattienn.ecommerce.auth;

import com.nhattienn.ecommerce.auth.dto.AuthResponse;
import com.nhattienn.ecommerce.auth.dto.LoginRequest;
import com.nhattienn.ecommerce.auth.dto.RegisterRequest;
import com.nhattienn.ecommerce.common.exception.DuplicateResourceException;
import com.nhattienn.ecommerce.common.security.CustomUserDetails;
import com.nhattienn.ecommerce.user.User;
import com.nhattienn.ecommerce.user.UserRepository;
import com.nhattienn.ecommerce.user.UserRole;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32; // 256-bit entropy

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already registered.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(UserRole.USER)
                .build();
        userRepository.save(user);

        return issueTokens(user, ipAddress);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        String email = normalizeEmail(request.email());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();

        return issueTokens(user, ipAddress);
    }

    private AuthResponse issueTokens(User user, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .revoked(false)
                .ipAddress(ipAddress)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}