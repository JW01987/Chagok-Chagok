# BT-02 | 백엔드 프로젝트 초기화

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **선행 태스크**: BT-01
- **완료 기준**: `GET /health` 200 응답 + Swagger UI 접근 가능 + 프로파일 분리 동작

---

## 개요

Spring Boot 3.x + Java 17 기반 백엔드 프로젝트를 초기화한다.
레이어드 아키텍처(domain / application / infrastructure / presentation)로 패키지를 구성하고,
환경별 설정 파일을 분리한다.

---

## BT-02-01 | Spring Boot 프로젝트 생성

**작업 유형**: Claude Code 실행 가능

### Spring Initializr 설정

```
Project: Gradle - Groovy
Language: Java
Spring Boot: 3.3.x (최신 stable)
Group: com.chagok
Artifact: chagok-backend
Name: chagok-backend
Package name: com.chagok
Packaging: Jar
Java: 17
```

### 선택할 Dependencies

```
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Spring Data Redis
- Validation
- Lombok
- Spring Boot DevTools (개발용)
- Flyway Migration
- Quartz Scheduler
- Spring Batch
- Spring Boot Actuator
```

### 실행 방법

```bash
cd chagok-chagok/backend

# Spring Initializr CLI 또는 curl로 생성
curl https://start.spring.io/starter.zip \
  -d type=gradle-project \
  -d language=java \
  -d bootVersion=3.3.5 \
  -d baseDir=. \
  -d groupId=com.chagok \
  -d artifactId=chagok-backend \
  -d name=chagok-backend \
  -d packageName=com.chagok \
  -d packaging=jar \
  -d javaVersion=17 \
  -d dependencies=web,security,data-jpa,postgresql,data-redis,validation,lombok,devtools,flyway,quartz,batch,actuator \
  -o backend.zip && unzip backend.zip && rm backend.zip
```

### 완료 확인
- [ ] `./gradlew build` 성공
- [ ] `backend/src/main/java/com/chagok/` 디렉토리 존재

---

## BT-02-02 | build.gradle 의존성 추가

**작업 유형**: 파일 수정 (Claude Code 실행 가능)

### 수정 파일: `backend/build.gradle`

Initializr에서 생성된 기본 파일에 아래 의존성 추가:

```groovy
dependencies {
    // Spring Boot 기본
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-batch'

    // DB
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // 스케줄러
    implementation 'org.springframework.boot:spring-boot-starter-quartz'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // Swagger
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

    // AWS SDK
    implementation platform('software.amazon.awssdk:bom:2.26.0')
    implementation 'software.amazon.awssdk:s3'
    implementation 'software.amazon.awssdk:sqs'

    // FCM
    implementation 'com.google.firebase:firebase-admin:9.3.0'

    // 유틸
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'com.h2database:h2'
}
```

### 완료 확인
- [ ] `./gradlew dependencies` 오류 없음

---

## BT-02-03 | 패키지 구조 생성

**작업 유형**: 디렉토리/파일 생성 (Claude Code 실행 가능)

### 생성할 패키지 구조

```
src/main/java/com/chagok/
├── ChagokApplication.java          ← 메인 클래스
│
├── domain/                         ← 도메인 엔티티 & 리포지토리
│   ├── user/
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── UserOauth.java
│   │   │   ├── RefreshToken.java
│   │   │   └── UserOnboarding.java
│   │   └── repository/
│   │       ├── UserRepository.java
│   │       ├── UserOauthRepository.java
│   │       ├── RefreshTokenRepository.java
│   │       └── UserOnboardingRepository.java
│   ├── portfolio/
│   │   ├── entity/
│   │   └── repository/
│   ├── savings/
│   │   ├── entity/
│   │   └── repository/
│   ├── notification/
│   │   ├── entity/
│   │   └── repository/
│   └── subscription/
│       ├── entity/
│       └── repository/
│
├── application/                    ← 비즈니스 로직 (Service)
│   ├── auth/
│   │   └── AuthService.java
│   ├── portfolio/
│   ├── savings/
│   ├── simulation/
│   ├── notification/
│   └── report/
│
├── infrastructure/                 ← 외부 연동 (DB, Redis, AWS, FCM)
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── SecurityConfig.java
│   ├── redis/
│   ├── aws/
│   ├── fcm/
│   └── brokerage/
│
└── presentation/                   ← API 컨트롤러 & DTO
    ├── auth/
    │   ├── AuthController.java
    │   └── dto/
    ├── portfolio/
    ├── savings/
    ├── simulation/
    ├── dashboard/
    ├── user/
    ├── notification/
    └── common/
        ├── response/
        │   ├── ApiResponse.java      ← 공통 응답 래퍼
        │   └── ErrorResponse.java
        └── exception/
            ├── GlobalExceptionHandler.java
            └── BusinessException.java
```

### 공통 응답 형식: `ApiResponse.java`

```java
package com.chagok.presentation.common.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;

    private ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

### 완료 확인
- [ ] 패키지 디렉토리 구조 생성 완료
- [ ] `ChagokApplication.java` 실행 가능

---

## BT-02-04 | 환경 설정 파일 분리

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `backend/src/main/resources/application.yml`

```yaml
spring:
  profiles:
    active: local   # 기본값. 배포 시 환경변수로 오버라이드
  application:
    name: chagok-backend

# 공통 설정 (프로파일 무관)
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

### 생성 파일: `backend/src/main/resources/application-local.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chagok_db
    username: chagok
    password: chagok1234
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway가 DDL 담당 — JPA는 검증만
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        default_batch_fetch_size: 100
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: localhost
      port: 6379
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID:local-kakao-id}
            client-secret: ${KAKAO_CLIENT_SECRET:local-kakao-secret}

logging:
  level:
    com.chagok: DEBUG
    org.springframework.security: DEBUG

jwt:
  secret: ${JWT_SECRET:local-dev-secret-key-must-be-at-least-256-bits-long}
  access-expiration-ms: 900000       # 15분
  refresh-expiration-ms: 2592000000  # 30일

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /api-docs
```

### 생성 파일: `backend/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD:}

logging:
  level:
    com.chagok: INFO

jwt:
  secret: ${JWT_SECRET}
  access-expiration-ms: 900000
  refresh-expiration-ms: 2592000000

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

### 생성 파일: `backend/src/main/resources/application-prod.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}

logging:
  level:
    root: WARN
    com.chagok: WARN

jwt:
  secret: ${JWT_SECRET}
  access-expiration-ms: 900000
  refresh-expiration-ms: 2592000000

springdoc:
  swagger-ui:
    enabled: false   # 운영 환경에서 Swagger 비활성화
```

### 완료 확인
- [ ] `local` 프로파일로 실행 시 DB 연결 시도 로그 확인

---

## BT-02-05 | Health Check API 구현

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `presentation/common/HealthController.java`

```java
package com.chagok.presentation.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now().toString(),
            "service", "chagok-backend"
        ));
    }
}
```

### Spring Security에서 `/health` 퍼블릭 허용

`infrastructure/security/SecurityConfig.java` 에 추가:

```java
.requestMatchers("/health", "/api-docs/**", "/swagger-ui/**").permitAll()
```

### 완료 확인

```bash
./gradlew bootRun &
curl http://localhost:8080/health
# 예상 응답: {"status":"UP","timestamp":"...","service":"chagok-backend"}
```

---

## BT-02-06 | Swagger 설정 및 연동 확인

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `infrastructure/config/SwaggerConfig.java`

```java
package com.chagok.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("차곡차곡 API")
                .description("첫 월급부터 차곡차곡 모으는 투자 습관 앱 API 명세")
                .version("v1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

### 완료 확인

```bash
# 앱 실행 후
open http://localhost:8080/swagger-ui.html
# Swagger UI 페이지 로드 확인
```

---

## 완료 체크리스트

- [ ] BT-02-01: Spring Boot 프로젝트 생성 + `./gradlew build` 성공
- [ ] BT-02-02: `build.gradle` 의존성 추가 완료
- [ ] BT-02-03: 패키지 구조 생성 (domain / application / infrastructure / presentation)
- [ ] BT-02-04: 환경 설정 분리 (local / dev / prod)
- [ ] BT-02-05: `GET /health` → 200 OK
- [ ] BT-02-06: `http://localhost:8080/swagger-ui.html` 접근 가능

**다음 태스크**: BT-04 (Docker Compose)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
