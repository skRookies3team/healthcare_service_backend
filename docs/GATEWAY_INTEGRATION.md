# API Gateway 연동 가이드 - Healthcare AI Chatbot Service

이 문서는 Healthcare AI Chatbot Service를 API Gateway에 연동하기 위한 설정 변경 사항을 설명합니다.

---

## 📋 변경 파일 목록

| 파일                             | 변경 유형 | 설명                   |
| -------------------------------- | --------- | ---------------------- |
| `application.yaml`               | 수정      | Healthcare 라우트 추가 |
| `application-dev.yaml`           | 수정      | 개발환경 URL 추가      |
| `application-prod.yaml`          | 수정      | 프로덕션 환경변수 추가 |
| `AuthorizationHeaderFilter.java` | 수정      | 공개 경로 추가         |

---

## 1. application.yaml 수정

**파일 위치:** `src/main/resources/application.yaml`

**routes 섹션 끝에 추가:**

```yaml
# --- Healthcare AI Chatbot Service ---
# [추가] Healthcare 헬스체크 (인증 불필요)
- id: healthcare-service-health
  uri: ${HEALTHCARE_SERVICE}
  predicates:
    - Path=/api/chat/health
    - Method=GET

# [추가] Healthcare AI 채팅 API (인증 필요)
- id: healthcare-service
  uri: ${HEALTHCARE_SERVICE}
  predicates:
    - Path=/api/chat/**
  filters:
    - AuthorizationHeaderFilter
```

---

## 2. application-dev.yaml 수정

**파일 위치:** `src/main/resources/application-dev.yaml`

**추가할 내용:**

```yaml
# Healthcare AI Chatbot Service (Port 8085)
HEALTHCARE_SERVICE: http://localhost:8085
```

**전체 파일 예시:**

```yaml
# [Dev 환경 변수 설정]

# 1. 서비스 주소 (Localhost)
USER_SERVICE: http://localhost:8080
SOCIAL_SERVICE: http://localhost:8083
DIARY_SERVICE: http://localhost:8087
PETMATE_SERVICE: http://localhost:8089
HEALTHCARE_SERVICE: http://localhost:8085 # [추가]

# 2. CORS 허용 도메인 (Frontend Local)
CORS_ALLOWED_ORIGIN: http://localhost:5173

# 3. JWT 설정 (개발용 임시값)
TOKEN_SECRET: my_super_secret_key_for_dev_environment_must_be_long_enough
TOKEN_EXPIRATION_TIME: 86400000
```

---

## 3. application-prod.yaml 수정

**파일 위치:** `src/main/resources/application-prod.yaml`

**추가할 내용:**

```yaml
# Healthcare AI Chatbot Service
HEALTHCARE_SERVICE: ${HEALTHCARE_SERVICE_URL}
```

---

## 4. AuthorizationHeaderFilter.java 수정

**파일 위치:** `src/main/java/com/example/gateway/filter/AuthorizationHeaderFilter.java`

**변경할 코드 (약 46-55 라인):**

**변경 전:**

```java
// JWT 검사 없이 통과
if (
        path.equals("/api/health") ||
                path.startsWith("/api/health/") ||
                path.equals("/api/users/login") ||
                path.equals("/api/users/signup") ||
                path.equals("/api/users/create") ||
                path.equals("/api/users/v3/api-docs") ||
                path.startsWith("/swagger")
) {
```

**변경 후:**

```java
// JWT 검사 없이 통과
if (
        path.equals("/api/health") ||
                path.startsWith("/api/health/") ||
                path.equals("/api/chat/health") ||  // [추가] Healthcare 헬스체크
                path.equals("/api/users/login") ||
                path.equals("/api/users/signup") ||
                path.equals("/api/users/create") ||
                path.equals("/api/users/v3/api-docs") ||
                path.startsWith("/swagger")
) {
```

---

## 5. 환경변수 설정 (프로덕션)

배포 담당자에게 다음 환경변수 설정 요청:

| 환경변수                 | 설명                  | 예시 값                          |
| ------------------------ | --------------------- | -------------------------------- |
| `HEALTHCARE_SERVICE_URL` | Healthcare 서비스 URL | `http://healthcare-service:8085` |

---

## 6. 테스트 방법

### 6.1 로컬 테스트

```bash
# 1. Healthcare 헬스체크 (Public)
curl http://localhost:8000/api/chat/health

# 2. 인증 없이 채팅 시도 (401 예상)
curl -X POST http://localhost:8000/api/chat/haiku \
  -H "Content-Type: application/json" \
  -d '{"message": "test"}'

# 3. JWT 토큰으로 채팅 (200 예상)
curl -X POST http://localhost:8000/api/chat/haiku \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"message": "강아지 건강 팁"}'
```

### 6.2 자동화 테스트

Healthcare 레포지토리의 테스트 스크립트 실행:

```bash
./scripts/test-gateway-integration.sh <JWT_TOKEN>
```

---

## 7. Git Commit 메시지

```
[Feat] Add Healthcare Service routing to API Gateway

- Add healthcare-service routes (health public, chat private)
- Add HEALTHCARE_SERVICE env var for dev/prod
- Add /api/chat/health to public path whitelist
```

---

## 📞 문의

문제 발생 시 Healthcare 담당자에게 연락:

- Port: 8085
- Endpoints: `/api/chat/health`, `/api/chat/haiku`, `/api/chat/persona`
