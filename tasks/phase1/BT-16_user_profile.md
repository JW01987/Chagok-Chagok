# BT-16 | 유저 프로필

- **최초 작성일**: 2026-08-03
- **업데이트**: 2026-08-03
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-08 (Auth)
- **완료 기준**: 프로필 조회/수정/회원 탈퇴 API 동작

---

## BT-16-01 | 프로필 조회 API

**엔드포인트**: `GET /api/users/me`
**인증**: 필요

### Service

```java
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserOnboardingRepository userOnboardingRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<UserOnboarding> onboarding = userOnboardingRepository.findByUserId(userId);

        return UserProfileResponse.of(user, onboarding.orElse(null));
    }
}
```

### Response DTO

```java
@Getter @Builder
public class UserProfileResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String loginType;          // EMAIL | KAKAO | APPLE
    private LocalDateTime createdAt;
    // 온보딩 요약
    private String investmentType;     // nullable
    private Integer monthlyAmount;     // nullable

    public static UserProfileResponse of(User user, UserOnboarding onboarding) {
        return UserProfileResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImageUrl(user.getProfileImageUrl())
            .loginType(user.getLoginType().name())
            .createdAt(user.getCreatedAt())
            .investmentType(onboarding != null ? onboarding.getInvestmentType().name() : null)
            .monthlyAmount(onboarding != null ? onboarding.getMonthlyAmount() : null)
            .build();
    }
}
```

---

## BT-16-02 | 프로필 수정 API

**엔드포인트**: `PATCH /api/users/me`
**수정 가능 필드**: nickname, profileImageUrl

### Request DTO

```java
@Getter
public class UpdateProfileRequest {
    @Size(min = 2, max = 20)
    private String nickname;

    @URL
    private String profileImageUrl;
}
```

### Service

```java
@Transactional
public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    if (request.getNickname() != null) {
        // 닉네임 중복 체크
        if (userRepository.existsByNicknameAndIdNot(request.getNickname(), userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.updateNickname(request.getNickname());
    }

    if (request.getProfileImageUrl() != null) {
        user.updateProfileImageUrl(request.getProfileImageUrl());
    }

    Optional<UserOnboarding> onboarding = userOnboardingRepository.findByUserId(userId);
    return UserProfileResponse.of(userRepository.save(user), onboarding.orElse(null));
}
```

### User 엔티티 메서드

```java
// User.java
public void updateNickname(String nickname) {
    this.nickname = nickname;
}

public void updateProfileImageUrl(String url) {
    this.profileImageUrl = url;
}
```

---

## BT-16-03 | 회원 탈퇴 API (Soft Delete)

**엔드포인트**: `DELETE /api/users/me`
**처리 방식**: `deleted_at` 설정 (Soft Delete), ACTIVE 적립 플랜 자동 TERMINATED

### Request DTO

```java
@Getter
public class WithdrawRequest {
    @NotBlank
    private String reason;   // 탈퇴 사유 (로깅용)
}
```

### Service

```java
@Transactional
public void withdraw(Long userId, WithdrawRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 1. ACTIVE 적립 플랜 → TERMINATED
    savingsPlanRepository.findByUserIdAndStatus(userId, SavingsStatus.ACTIVE)
        .ifPresent(plan -> {
            plan.terminate();
            savingsPlanRepository.save(plan);
        });

    // 2. ACTIVE 포트폴리오 → TERMINATED
    userPortfolioRepository.findByUserIdAndStatus(userId, PortfolioStatus.ACTIVE)
        .ifPresent(up -> {
            up.terminate();
            userPortfolioRepository.save(up);
        });

    // 3. Refresh Token 삭제
    refreshTokenRepository.deleteByUserId(userId);

    // 4. 유저 Soft Delete
    user.withdraw();
    userRepository.save(user);

    // 5. 탈퇴 사유 로깅 (선택적)
    log.info("[USER_WITHDRAW] userId={}, reason={}", userId, request.getReason());
}
```

### User 엔티티

```java
// User.java
public void withdraw() {
    this.status = UserStatus.WITHDRAWN;
    this.deletedAt = LocalDateTime.now();
    // 이메일 익명화 (재가입 가능하도록)
    this.email = "withdrawn_" + this.id + "@deleted.chagok";
    this.nickname = "탈퇴한 사용자";
    this.profileImageUrl = null;
}
```

---

## BT-16-04 | 컨트롤러

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "UserProfile", description = "유저 프로필 API")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    @Operation(summary = "내 프로필 조회")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getProfile(userId)));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.updateProfile(userId, request)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawRequest request) {
        userProfileService.withdraw(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "탈퇴가 완료되었습니다"));
    }
}
```

---

## 완료 체크리스트

- [ ] BT-16-01: `GET /api/users/me` — 유저 정보 + 온보딩 요약
- [ ] BT-16-02: `PATCH /api/users/me` — 닉네임/프로필 이미지 수정, 닉네임 중복 체크
- [ ] BT-16-03: `DELETE /api/users/me` — Soft Delete, 이메일 익명화, 관련 데이터 정리
- [ ] 탈퇴 후 동일 이메일 재가입 가능 확인
- [ ] 탈퇴 유저 로그인 시 401 반환 확인

---

_최초 작성: 2026-08-03 | 업데이트: 2026-08-03_
