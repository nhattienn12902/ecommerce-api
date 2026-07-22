package com.nhattienn.ecommerce;

import com.nhattienn.ecommerce.auth.AuthService;
import com.nhattienn.ecommerce.auth.JwtProperties;
import com.nhattienn.ecommerce.auth.JwtService;
import com.nhattienn.ecommerce.auth.RefreshToken;
import com.nhattienn.ecommerce.auth.RefreshTokenRepository;
import com.nhattienn.ecommerce.auth.dto.AuthResponse;
import com.nhattienn.ecommerce.auth.dto.LoginRequest;
import com.nhattienn.ecommerce.auth.dto.RegisterRequest;
import com.nhattienn.ecommerce.common.exception.DuplicateResourceException;
import com.nhattienn.ecommerce.common.exception.UnauthorizedException;
import com.nhattienn.ecommerce.common.security.CustomUserDetails;
import com.nhattienn.ecommerce.user.User;
import com.nhattienn.ecommerce.user.UserRepository;
import com.nhattienn.ecommerce.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // -----------------------------------------------------------------------
    // Mocks & subject under test
    // -----------------------------------------------------------------------

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    private static final String IP = "127.0.0.1";
    private static final String RAW_TOKEN = "raw-refresh-token";

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@gmail.com")
                .passwordHash("hashed")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();

        // Stub mặc định cho issueTokens() — hầu hết test đều cần
        // Đặt ở đây để tránh lặp lại stub trong mỗi test happy path
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        lenient().when(jwtProperties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
    }

    // -----------------------------------------------------------------------
    // register()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register successfully and return tokens")
        void shouldRegisterSuccessfully() {
            when(userRepository.existsByEmail("user@gmail.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed-password");

            RegisterRequest request = new RegisterRequest("user@gmail.com", "password123", "Test User");
            AuthResponse response = authService.register(request, IP);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.tokenType()).isEqualTo("Bearer");

            verify(userRepository).save(any(User.class));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void shouldThrowWhenEmailAlreadyExists() {
            when(userRepository.existsByEmail("user@gmail.com")).thenReturn(true);

            RegisterRequest request = new RegisterRequest("user@gmail.com", "password123", "Test User");

            assertThatThrownBy(() -> authService.register(request, IP))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Email already registered");

            // Đảm bảo không tạo user khi email đã tồn tại
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should normalize email before checking duplicate and saving")
        void shouldNormalizeEmailBeforeProcessing() {
            // Email có uppercase và whitespace — phải được normalize trước khi dùng
            RegisterRequest request = new RegisterRequest("  User@Gmail.COM  ", "password123", "Test User");
            when(userRepository.existsByEmail("user@gmail.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");

            authService.register(request, IP);

            // Verify duplicate check dùng email đã normalize — không phải email raw từ request
            verify(userRepository).existsByEmail("user@gmail.com");

            // Verify user được save với email đã normalize
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("user@gmail.com");
        }
    }

    // -----------------------------------------------------------------------
    // login()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should login successfully and return tokens")
        void shouldLoginSuccessfully() {
            CustomUserDetails userDetails = new CustomUserDetails(testUser);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authToken);

            LoginRequest request = new LoginRequest("user@gmail.com", "password123");
            AuthResponse response = authService.login(request, IP);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("should throw when credentials are invalid")
        void shouldThrowWhenCredentialsAreInvalid() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            LoginRequest request = new LoginRequest("user@gmail.com", "wrong-password");

            assertThatThrownBy(() -> authService.login(request, IP))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    // -----------------------------------------------------------------------
    // refresh()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        private RefreshToken validToken;

        @BeforeEach
        void setUp() {
            // Tạo token hợp lệ làm base cho các test trong nhóm này
            // Mỗi test sẽ override property cần thiết để test case cụ thể
            validToken = RefreshToken.builder()
                    .userId(testUser.getId())
                    .tokenHash("dummy-hash") // hash không quan trọng — DB lookup được mock
                    .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                    .revoked(false)
                    .build();
        }

        @Test
        @DisplayName("should issue new tokens and revoke old token on valid refresh")
        void shouldRotateTokenOnValidRefresh() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            AuthResponse response = authService.refresh(RAW_TOKEN, IP);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isNotBlank();

            // Token cũ phải bị revoke — đây là core của rotation
            assertThat(validToken.isRevoked()).isTrue();

            // Token mới phải được lưu vào DB
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should revoke all user tokens and throw when reused revoked token is detected")
        void shouldRevokeAllTokensOnReuseDetection() {
            // Token đã bị revoke từ trước — đây là dấu hiệu token bị stolen và reuse
            validToken.setRevoked(true);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN, IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("no longer valid");

            // Toàn bộ session của user phải bị revoke — không chỉ token này
            verify(refreshTokenRepository).revokeAllActiveByUserId(validToken.getUserId());
        }

        @Test
        @DisplayName("should throw when refresh token is expired")
        void shouldThrowWhenTokenIsExpired() {
            validToken.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN, IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("expired");

            // Expired token không trigger revoke all — chỉ reuse detection mới trigger
            verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any());
        }

        @Test
        @DisplayName("should throw when refresh token does not exist in database")
        void shouldThrowWhenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN, IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid refresh token");
        }
    }

    // -----------------------------------------------------------------------
    // logout()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("should revoke token on valid logout")
        void shouldRevokeTokenOnLogout() {
            RefreshToken token = RefreshToken.builder()
                    .userId(testUser.getId())
                    .tokenHash("dummy-hash")
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

            authService.logout(RAW_TOKEN);

            assertThat(token.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("should not throw when logout token does not exist")
        void shouldNotThrowWhenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            // ifPresent() trong logout() — token không tồn tại thì không làm gì, không throw
            authService.logout(RAW_TOKEN);

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // logoutAll()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("logoutAll()")
    class LogoutAll {

        @Test
        @DisplayName("should revoke all active tokens for the user")
        void shouldRevokeAllActiveTokens() {
            UUID userId = testUser.getId();
            when(refreshTokenRepository.revokeAllActiveByUserId(userId)).thenReturn(3);

            authService.logoutAll(userId);

            verify(refreshTokenRepository).revokeAllActiveByUserId(userId);
        }
    }
}