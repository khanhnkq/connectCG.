package org.example.connectcg_be.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.connectcg_be.dto.AuthSessionResponse;
import org.example.connectcg_be.dto.CreatProfileRequest;
import org.example.connectcg_be.dto.LoginRequest;
import org.example.connectcg_be.dto.RegisterRequest;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.ratelimit.RateLimitPolicy;
import org.example.connectcg_be.ratelimit.RateLimitService;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.AccessTokenRevocationService;
import org.example.connectcg_be.security.AuthCookieService;
import org.example.connectcg_be.security.InvalidRefreshTokenException;
import org.example.connectcg_be.security.JwtTokenProvider;
import org.example.connectcg_be.security.RefreshTokenReuseException;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.AuthService;
import org.example.connectcg_be.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RateLimitService rateLimitService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final AccessTokenRevocationService accessTokenRevocationService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            AuthService authService,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            RateLimitService rateLimitService,
            RefreshTokenService refreshTokenService,
            AuthCookieService authCookieService,
            AccessTokenRevocationService accessTokenRevocationService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.authService = authService;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.rateLimitService = rateLimitService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.accessTokenRevocationService = accessTokenRevocationService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        rateLimitService.check(
                RateLimitPolicy.LOGIN,
                request.getRemoteAddr() + "|" + loginRequest.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId()).orElseThrow();
            RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user, request);
            String accessToken = tokenProvider.generateToken(principal, refreshToken.familyId());
            authCookieService.writeSessionCookies(response, accessToken, refreshToken.rawToken());

            return ResponseEntity.ok(toSessionResponse(principal));
        } catch (DisabledException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email."));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Sai tên đăng nhập hoặc mật khẩu."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.rotate(
                    authCookieService.readRefreshToken(request), request);
            UserPrincipal principal = UserPrincipal.create(refreshToken.user());
            String accessToken = tokenProvider.generateToken(principal, refreshToken.familyId());
            authCookieService.writeSessionCookies(response, accessToken, refreshToken.rawToken());
            return ResponseEntity.ok(toSessionResponse(principal));
        } catch (RefreshTokenReuseException exception) {
            authCookieService.clearSessionCookies(response);
            accessTokenRevocationService.revokeSession(exception.getFamilyId());
            return unauthorized("Phiên đăng nhập không còn hợp lệ.");
        } catch (InvalidRefreshTokenException exception) {
            authCookieService.clearSessionCookies(response);
            return unauthorized("Phiên đăng nhập đã hết hạn.");
        }
    }

    @GetMapping("/me")
    public AuthSessionResponse currentSession(@AuthenticationPrincipal UserPrincipal principal) {
        return toSessionResponse(principal);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = authCookieService.readAccessToken(request);
        String refreshToken = authCookieService.readRefreshToken(request);
        authCookieService.clearSessionCookies(response);

        refreshTokenService.revokeCurrent(refreshToken, "LOGOUT")
                .ifPresent(accessTokenRevocationService::revokeSession);
        accessTokenRevocationService.revokeToken(accessToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = authCookieService.readAccessToken(request);
        authCookieService.clearSessionCookies(response);
        refreshTokenService.revokeAllForUser(principal.getId(), "LOGOUT_ALL", true);
        accessTokenRevocationService.revokeSession(tokenProvider.getSessionId(accessToken));
        accessTokenRevocationService.revokeToken(accessToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegisterRequest signUpRequest,
            HttpServletRequest request) {
        rateLimitService.check(RateLimitPolicy.REGISTER, request.getRemoteAddr());
        try {
            authService.register(signUpRequest);
            return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<?> createProfile(@Valid @RequestBody CreatProfileRequest profileRequest) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            authService.createProfile(profileRequest, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Profile created successfully!"));
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        rateLimitService.check(RateLimitPolicy.FORGOT_PASSWORD, email);
        authService.forgotPassword(email);
        return ResponseEntity.ok(Map.of("message", "Email sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of(
                    "message", "Xác thực email thành công! Bạn có thể đăng nhập ngay bây giờ."));
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    private AuthSessionResponse toSessionResponse(UserPrincipal principal) {
        boolean hasProfile = userProfileRepository.existsByUserId(principal.getId());
        String fullName = hasProfile
                ? userProfileRepository.findByUserId(principal.getId())
                        .map(profile -> profile.getFullName())
                        .orElse(null)
                : null;
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("ROLE_USER");
        return new AuthSessionResponse(
                principal.getId(), principal.getUsername(), role, hasProfile, fullName);
    }

    private ResponseEntity<Map<String, String>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", message));
    }
}
