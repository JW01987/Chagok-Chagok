# BT-08 | 인증 (Auth)

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-07 (DB 마이그레이션)
- **완료 기준**: 회원가입 → 로그인 → 토큰 재발급 → 로그아웃 전체 플로우 동작

---

## 개요

Spring Security + JWT 기반 인증 시스템을 구축한다.
Access Token (15분) + Refresh Token (30일) 발급, 로그인 실패 5회 잠금, 토큰 로테이션을 포함한다.

---

## BT-08-01 | Spring Security 기본 설정

**파일**: `infrastructure/security/SecurityConfig.java`

```java
package com.chagok.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 퍼블릭 엔드포인트
                .requestMatchers(
                    "/health",
                    "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/api/auth/signup", "/api/auth/login", "/api/auth/reissue",
                    "/api/portfolios/**",      // 게스트 허용
                    "/api/simulation/**"       // 게스트 허용
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost factor 12
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

---

## BT-08-02 | JWT 유틸리티

**파일**: `infrastructure/security/JwtTokenProvider.java`

```java
package com.chagok.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessExpMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpMs = accessExpMs;
        this.refreshExpMs = refreshExpMs;
    }

    public String generateAccessToken(Long userId, String email) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("type", "ACCESS")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpMs))
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("type", "REFRESH")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpMs))
            .signWith(secretKey)
            .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT 토큰: {}", e.getMessage());
        }
        return false;
    }
}
```

---

## BT-08-03 | JWT 필터

**파일**: `infrastructure/security/JwtAuthenticationFilter.java`

```java
package com.chagok.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            // 간단한 인증 토큰 (UserDetails 없이 userId만 principal로)
            var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

---

## BT-08-04 | 회원가입 API

### DTO 파일: `presentation/auth/dto/SignupRequest.java`

```java
package com.chagok.presentation.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class SignupRequest {
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @NotBlank
    private String email;

    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    @NotBlank
    private String password;

    @Size(min = 1, max = 50, message = "닉네임은 1~50자 이내여야 합니다")
    @NotBlank
    private String nickname;
}
```

### 서비스: `application/auth/AuthService.java` (회원가입 메서드)

```java
@Transactional
public SignupResponse signup(SignupRequest request) {
    // 이메일 중복 체크
    if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
        throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }

    User user = User.builder()
        .email(request.getEmail())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .nickname(request.getNickname())
        .status(UserStatus.ACTIVE)
        .build();

    userRepository.save(user);

    // notification_settings 자동 생성
    notificationSettingsRepository.save(NotificationSettings.defaultOf(user));

    // user_subscriptions FREE 플랜 자동 가입
    SubscriptionPlan freePlan = subscriptionPlanRepository.findByCode("FREE")
        .orElseThrow(() -> new IllegalStateException("FREE 플랜이 없습니다"));
    userSubscriptionRepository.save(UserSubscription.freeOf(user, freePlan));

    return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
}
```

### 컨트롤러: `presentation/auth/AuthController.java`

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String deviceInfo = servletRequest.getHeader("User-Agent");
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, ip, deviceInfo)));
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access Token 재발급")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(
            @Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.reissue(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "로그아웃 되었습니다"));
    }
}
```

---

## BT-08-05 | 로그인 API (실패 횟수 + 잠금 처리)

### 서비스 로그인 메서드

```java
@Transactional
public LoginResponse login(LoginRequest request, String ip, String deviceInfo) {
    User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 계정 잠금 확인
    if (user.isLocked()) {
        throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
    }

    // 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
        user.increaseLoginFailCount(); // 실패 횟수 +1, 5회 시 LOCKED + locked_until = +15분
        userRepository.save(user);
        throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }

    // 로그인 성공 처리
    user.resetLoginFailCount();
    user.updateLastLoginAt();
    userRepository.save(user);

    // 토큰 발급
    String accessToken  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

    // refresh_tokens 저장 (SHA-256 해시)
    String tokenHash = hashToken(refreshToken);
    refreshTokenRepository.save(RefreshToken.of(user, tokenHash, ip, deviceInfo));

    return new LoginResponse(accessToken, refreshToken, user.getId());
}

// User 엔티티 메서드
public void increaseLoginFailCount() {
    this.loginFailCount++;
    if (this.loginFailCount >= 5) {
        this.status = UserStatus.LOCKED;
        this.lockedUntil = LocalDateTime.now().plusMinutes(15);
    }
}

public boolean isLocked() {
    if (this.status != UserStatus.LOCKED) return false;
    if (this.lockedUntil != null && LocalDateTime.now().isAfter(this.lockedUntil)) {
        this.status = UserStatus.ACTIVE;
        this.loginFailCount = 0;
        return false;
    }
    return true;
}
```

---

## BT-08-06 | 토큰 재발급 API

```java
@Transactional
public ReissueResponse reissue(ReissueRequest request) {
    // 1. Refresh Token 유효성 검증
    if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 2. DB에서 토큰 조회 (해시 비교)
    String tokenHash = hashToken(request.getRefreshToken());
    RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    // 3. 무효화 여부 확인
    if (storedToken.isRevoked()) {
        throw new BusinessException(ErrorCode.REVOKED_REFRESH_TOKEN);
    }

    // 4. 기존 토큰 무효화 (토큰 로테이션)
    storedToken.revoke();
    refreshTokenRepository.save(storedToken);

    // 5. 새 토큰 발급
    User user = storedToken.getUser();
    String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
    refreshTokenRepository.save(RefreshToken.of(user, hashToken(newRefreshToken), null, null));

    return new ReissueResponse(newAccessToken, newRefreshToken);
}
```

---

## BT-08-07 | 로그아웃 API

```java
@Transactional
public void logout(LogoutRequest request) {
    String tokenHash = hashToken(request.getRefreshToken());
    refreshTokenRepository.findByTokenHash(tokenHash)
        .ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
}
```

---

## BT-08-08 | 만료 토큰 배치 정리

**파일**: `infrastructure/batch/TokenCleanupScheduler.java`

```java
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(LocalDateTime.now());
        log.info("만료/무효 Refresh Token 정리 완료: {}건", deleted);
    }
}

// Repository 쿼리
@Modifying
@Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revokedAt IS NOT NULL")
int deleteExpiredOrRevoked(@Param("now") LocalDateTime now);
```

---

## BT-08-09 | 단위 테스트

**파일**: `test/application/auth/AuthServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks AuthService authService;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("이메일 중복 시 DUPLICATE_EMAIL 예외 발생")
    void signup_duplicateEmail_throwsException() {
        given(userRepository.existsByEmailAndDeletedAtIsNull("test@test.com")).willReturn(true);
        assertThatThrownBy(() -> authService.signup(new SignupRequest("test@test.com", "password1", "진")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("5회 로그인 실패 시 계정 잠금")
    void login_fiveFailures_lockAccount() {
        User user = User.builder().email("test@test.com").passwordHash("hash")
            .status(UserStatus.ACTIVE).loginFailCount(4).build();
        given(userRepository.findByEmailAndDeletedAtIsNull(any())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@test.com", "wrong"), null, null))
            .isInstanceOf(BusinessException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}
```

---

## 완료 체크리스트

- [ ] BT-08-01: Spring Security 설정 (퍼블릭/인증 경로 분리)
- [ ] BT-08-02: JWT 토큰 생성/검증 유틸리티
- [ ] BT-08-03: JWT 인증 필터
- [ ] BT-08-04: `POST /api/auth/signup` — 409 중복, 400 유효성
- [ ] BT-08-05: `POST /api/auth/login` — 5회 잠금, 토큰 발급
- [ ] BT-08-06: `POST /api/auth/reissue` — 토큰 로테이션
- [ ] BT-08-07: `POST /api/auth/logout` — revoked_at 업데이트
- [ ] BT-08-08: 만료 토큰 배치 정리 스케줄러
- [ ] BT-08-09: 단위 테스트 통과

**다음 태스크**: BT-09 (소셜 로그인), BT-10 (온보딩) — 병렬 가능

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
