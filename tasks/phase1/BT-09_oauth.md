# BT-09 | 소셜 로그인 (OAuth2)

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-08 (Auth)
- **완료 기준**: 카카오 / 애플 OAuth2 로그인 후 JWT 발급

---

## 개요

카카오와 애플 OAuth2 로그인을 구현한다.
신규 유저는 자동 가입, 기존 유저는 기존 계정에 연결된다.
소셜 토큰은 AES-256으로 암호화하여 `user_oauth` 테이블에 저장한다.

---

## BT-09-01 | 카카오 개발자 앱 등록

**작업 유형**: 수동

1. [카카오 개발자 콘솔](https://developers.kakao.com) 접속
2. 내 애플리케이션 → 애플리케이션 추가
   - 앱 이름: 차곡차곡
   - 사업자명: 개인
3. 앱 키 확인: REST API 키 → GitHub Secrets에 `KAKAO_CLIENT_ID` 등록
4. 플랫폼 → Web → 사이트 도메인 등록: `http://localhost:8080`, `https://api-dev.chagok.com`
5. 카카오 로그인 → 활성화
6. Redirect URI 등록: `http://localhost:8080/login/oauth2/code/kakao`
7. 동의항목: 닉네임 (필수), 이메일 (선택 → 필수로 변경 요청)

---

## BT-09-02 | 카카오 OAuth2 의존성 및 설정

**파일**: `application-local.yml` 추가

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            authorization-grant-type: authorization_code
            scope:
              - profile_nickname
              - account_email
            client-name: Kakao
            client-authentication-method: client_secret_post
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id
```

---

## BT-09-03 | CustomOAuth2UserService

**파일**: `infrastructure/security/oauth2/CustomOAuth2UserService.java`

```java
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserOauthRepository userOauthRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId().toUpperCase();

        OAuthUserInfo userInfo = OAuthUserInfoFactory.getOAuthUserInfo(provider, oAuth2User.getAttributes());

        // 기존 OAuth 연결 조회
        Optional<UserOauth> existingOauth = userOauthRepository
            .findByProviderAndProviderUserId(provider, userInfo.getProviderId());

        User user;
        if (existingOauth.isPresent()) {
            // 기존 유저 — 토큰 갱신
            user = existingOauth.get().getUser();
            existingOauth.get().updateTokens(
                aesEncryptionUtil.encrypt(request.getAccessToken().getTokenValue()),
                null
            );
        } else {
            // 이메일로 기존 유저 조회 (이메일 로그인 유저가 소셜 연결하는 경우)
            user = userRepository.findByEmailAndDeletedAtIsNull(userInfo.getEmail())
                .orElseGet(() -> createNewUser(userInfo));

            // user_oauth 연결
            userOauthRepository.save(UserOauth.builder()
                .user(user)
                .provider(provider)
                .providerUserId(userInfo.getProviderId())
                .accessTokenEncrypted(aesEncryptionUtil.encrypt(request.getAccessToken().getTokenValue()))
                .build());
        }

        return new CustomOAuth2User(user.getId(), user.getEmail(), oAuth2User.getAttributes());
    }

    private User createNewUser(OAuthUserInfo userInfo) {
        User newUser = User.builder()
            .email(userInfo.getEmail())
            .nickname(userInfo.getNickname())
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(newUser);
        notificationSettingsRepository.save(NotificationSettings.defaultOf(newUser));
        SubscriptionPlan freePlan = subscriptionPlanRepository.findByCode("FREE").orElseThrow();
        userSubscriptionRepository.save(UserSubscription.freeOf(newUser, freePlan));
        return newUser;
    }
}
```

---

## BT-09-04 | OAuth2 성공 핸들러 (JWT 발급)

**파일**: `infrastructure/security/oauth2/OAuth2SuccessHandler.java`

```java
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken  = jwtTokenProvider.generateAccessToken(oAuth2User.getUserId(), oAuth2User.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(oAuth2User.getUserId());

        refreshTokenRepository.save(RefreshToken.of(
            oAuth2User.getUser(), hashToken(refreshToken),
            request.getRemoteAddr(), request.getHeader("User-Agent")
        ));

        // 앱으로 토큰 전달 (딥링크 또는 JSON 응답)
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
            {"accessToken":"%s","refreshToken":"%s"}
            """.formatted(accessToken, refreshToken));
    }
}
```

---

## BT-09-05 | OAuthUserInfo 팩토리

**파일**: `infrastructure/security/oauth2/OAuthUserInfoFactory.java`

```java
public class OAuthUserInfoFactory {
    public static OAuthUserInfo getOAuthUserInfo(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "KAKAO" -> new KakaoOAuthUserInfo(attributes);
            case "APPLE" -> new AppleOAuthUserInfo(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth Provider: " + provider);
        };
    }
}

// 카카오 정보 추출
public class KakaoOAuthUserInfo implements OAuthUserInfo {
    private final Map<String, Object> attributes;

    @Override
    public String getProviderId() { return String.valueOf(attributes.get("id")); }

    @Override
    public String getEmail() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        return (String) account.get("email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> profile = (Map<String, Object>)
            ((Map<String, Object>) attributes.get("kakao_account")).get("profile");
        return (String) profile.get("nickname");
    }
}
```

---

## BT-09-06 | AES-256 암호화 유틸

**파일**: `infrastructure/security/AesEncryptionUtil.java`

```java
@Component
public class AesEncryptionUtil {

    private final SecretKey secretKey;

    public AesEncryptionUtil(@Value("${encryption.aes-key}") String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // IV + 암호문을 Base64로 합쳐서 저장
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            byte[] iv = Arrays.copyOfRange(combined, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("복호화 실패", e);
        }
    }
}
```

### `.env.example`에 추가

```dotenv
# AES-256 암호화 키 (Base64 인코딩된 32바이트)
ENCRYPTION_AES_KEY=
```

### 키 생성 방법

```bash
# 32바이트 랜덤 키 생성 후 Base64 인코딩
openssl rand -base64 32
```

---

## BT-09-03 | 애플 로그인 설정

**작업 유형**: 수동

1. [Apple Developer](https://developer.apple.com) → Certificates, IDs & Profiles
2. Identifiers → App IDs → Capabilities → Sign In with Apple 활성화
3. Keys → Create Key → Sign In with Apple 체크 → 키 다운로드 (`.p8` 파일)
4. GitHub Secrets 등록:
   - `APPLE_CLIENT_ID`: 앱 번들 ID (`com.chagok.app`)
   - `APPLE_TEAM_ID`: 팀 ID
   - `APPLE_KEY_ID`: 키 ID
   - `APPLE_PRIVATE_KEY`: `.p8` 파일 내용

```yaml
# application.yml 추가
spring:
  security:
    oauth2:
      client:
        registration:
          apple:
            client-id: ${APPLE_CLIENT_ID}
            redirect-uri: "{baseUrl}/login/oauth2/code/apple"
            authorization-grant-type: authorization_code
            scope: name,email
        provider:
          apple:
            authorization-uri: https://appleid.apple.com/auth/authorize
            token-uri: https://appleid.apple.com/auth/token
            user-info-uri: https://appleid.apple.com/auth/userinfo
            user-name-attribute: sub
```

---

## 완료 체크리스트

- [ ] BT-09-01: 카카오 개발자 앱 등록 + Redirect URI 설정
- [ ] BT-09-02: `application.yml` 카카오 OAuth2 설정
- [ ] BT-09-03: `CustomOAuth2UserService` 구현 (신규/기존 유저 분기)
- [ ] BT-09-04: `OAuth2SuccessHandler` — JWT 발급
- [ ] BT-09-05: `OAuthUserInfoFactory` (카카오 + 애플 파싱)
- [ ] BT-09-06: AES-256 암호화 유틸 + 소셜 토큰 암호화 저장
- [ ] BT-09-03: 애플 Developer 등록 + `.p8` 키 설정

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
