# BT-17 | 알림 설정

- **최초 작성일**: 2026-08-03
- **업데이트**: 2026-08-03
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-08 (Auth), BT-07 (DB 마이그레이션)
- **완료 기준**: 알림 설정 조회/수정 API + 회원가입 시 자동 생성 + FCM 토큰 등록

---

## BT-17-01 | 회원가입 시 기본 알림 설정 자동 생성

인증 완료(BT-08) 후 AuthService에서 호출.

```java
// AuthService.java (signup 메서드 내)
@Transactional
public SignupResponse signup(SignupRequest request) {
    // ... 유저 생성 로직 ...

    // 알림 설정 기본값 생성
    notificationSettingsRepository.save(NotificationSettings.defaultOf(savedUser));

    return SignupResponse.of(savedUser, accessToken, refreshToken);
}
```

```java
// NotificationSettings.java 엔티티 팩토리 메서드
public static NotificationSettings defaultOf(User user) {
    return NotificationSettings.builder()
        .user(user)
        .savingsExecutionEnabled(true)     // 적립 완료 알림 ON
        .savingsFailedEnabled(true)        // 적립 실패 알림 ON
        .monthlyReportEnabled(true)        // 월간 리포트 알림 ON
        .marketingEnabled(false)           // 마케팅 알림 OFF (기본)
        .pushEnabled(true)
        .emailEnabled(false)               // MVP는 이메일 알림 비활성화
        .fcmToken(null)
        .build();
}
```

---

## BT-17-02 | 알림 설정 조회 API

**엔드포인트**: `GET /api/notification-settings`
**인증**: 필요

```java
public NotificationSettingsResponse getSettings(Long userId) {
    NotificationSettings settings = notificationSettingsRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTINGS_NOT_FOUND));

    return NotificationSettingsResponse.of(settings);
}
```

### Response DTO

```java
@Getter @Builder
public class NotificationSettingsResponse {
    private Boolean savingsExecutionEnabled;
    private Boolean savingsFailedEnabled;
    private Boolean monthlyReportEnabled;
    private Boolean marketingEnabled;
    private Boolean pushEnabled;
    private Boolean emailEnabled;
    private Boolean hasFcmToken;           // FCM 토큰 등록 여부 (토큰 자체는 미노출)

    public static NotificationSettingsResponse of(NotificationSettings s) {
        return NotificationSettingsResponse.builder()
            .savingsExecutionEnabled(s.isSavingsExecutionEnabled())
            .savingsFailedEnabled(s.isSavingsFailedEnabled())
            .monthlyReportEnabled(s.isMonthlyReportEnabled())
            .marketingEnabled(s.isMarketingEnabled())
            .pushEnabled(s.isPushEnabled())
            .emailEnabled(s.isEmailEnabled())
            .hasFcmToken(s.getFcmToken() != null)
            .build();
    }
}
```

---

## BT-17-03 | 알림 설정 수정 API

**엔드포인트**: `PATCH /api/notification-settings`

### Request DTO

```java
@Getter
public class UpdateNotificationSettingsRequest {
    private Boolean savingsExecutionEnabled;
    private Boolean savingsFailedEnabled;
    private Boolean monthlyReportEnabled;
    private Boolean marketingEnabled;
    private Boolean pushEnabled;
    private Boolean emailEnabled;
    // null인 필드는 변경하지 않음 (Partial Update)
}
```

### Service

```java
@Transactional
public NotificationSettingsResponse updateSettings(Long userId, UpdateNotificationSettingsRequest request) {
    NotificationSettings settings = notificationSettingsRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTINGS_NOT_FOUND));

    if (request.getSavingsExecutionEnabled() != null)
        settings.setSavingsExecutionEnabled(request.getSavingsExecutionEnabled());
    if (request.getSavingsFailedEnabled() != null)
        settings.setSavingsFailedEnabled(request.getSavingsFailedEnabled());
    if (request.getMonthlyReportEnabled() != null)
        settings.setMonthlyReportEnabled(request.getMonthlyReportEnabled());
    if (request.getMarketingEnabled() != null)
        settings.setMarketingEnabled(request.getMarketingEnabled());
    if (request.getPushEnabled() != null)
        settings.setPushEnabled(request.getPushEnabled());
    if (request.getEmailEnabled() != null)
        settings.setEmailEnabled(request.getEmailEnabled());

    return NotificationSettingsResponse.of(notificationSettingsRepository.save(settings));
}
```

---

## BT-17-04 | FCM 토큰 등록/갱신 API

**엔드포인트**: `POST /api/notification-settings/fcm-token`
**목적**: 앱 실행 시 FCM 토큰 갱신 (토큰은 주기적으로 변경됨)

### Request / Service

```java
@Getter
public class RegisterFcmTokenRequest {
    @NotBlank
    private String fcmToken;
}
```

```java
@Transactional
public void registerFcmToken(Long userId, String fcmToken) {
    NotificationSettings settings = notificationSettingsRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTINGS_NOT_FOUND));

    settings.setFcmToken(fcmToken);
    settings.setFcmTokenUpdatedAt(LocalDateTime.now());
    notificationSettingsRepository.save(settings);
}
```

---

## BT-17-05 | 컨트롤러

```java
@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
@Tag(name = "NotificationSettings", description = "알림 설정 API")
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    @Operation(summary = "알림 설정 조회")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getSettings(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificationSettingsService.getSettings(userId)));
    }

    @PatchMapping
    @Operation(summary = "알림 설정 수정 (Partial Update)")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateSettings(
            @AuthenticationPrincipal Long userId,
            @RequestBody UpdateNotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificationSettingsService.updateSettings(userId, request)));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록/갱신")
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RegisterFcmTokenRequest request) {
        notificationSettingsService.registerFcmToken(userId, request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "FCM 토큰이 등록되었습니다"));
    }
}
```

---

## 완료 체크리스트

- [ ] BT-17-01: 회원가입 시 기본값으로 자동 생성 (savingsExecution/Failed ON, marketing OFF)
- [ ] BT-17-02: `GET /api/notification-settings` — 설정 조회
- [ ] BT-17-03: `PATCH /api/notification-settings` — 부분 수정 (null 필드는 변경 안 함)
- [ ] BT-17-04: `POST /api/notification-settings/fcm-token` — FCM 토큰 등록
- [ ] FCM 토큰 응답에 미포함 (hasFcmToken boolean만 반환) 확인

---

_최초 작성: 2026-08-03 | 업데이트: 2026-08-03_
