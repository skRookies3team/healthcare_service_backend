# healthcare_service_backend+AI_chatbot_service

---

```markdown
# 🏥 Healthcare AI Chatbot Service Backend

> **PetLog MSA 헬스케어 서비스** - AI 기반 반려동물 건강 상담 챗봇

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M4-blue.svg)](https://spring.io/projects/spring-ai)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-Event--Driven-red.svg)](https://kafka.apache.org/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)

---

## 📋 프로젝트 개요

**Healthcare AI Chatbot Service**는 PetLog MSA 프로젝트의 핵심 마이크로서비스로, **Spring AI + AWS Bedrock/OpenAI + Milvus Vector DB**를 활용한 RAG 기반 반려동물 건강 상담 챗봇입니다.

### 핵심 가치

- **AI 기반 개인화 상담**: Diary 데이터를 RAG로 활용한 맞춤형 건강 조언
- **실시간 스트리밍 응답**: SSE를 통한 ChatGPT 스타일 답변
- **MSA 이벤트 기반**: Kafka를 통한 Diary Service와 느슨한 결합
- **반려동물 페르소나**: 반려동물이 직접 대화하는 듯한 UX

---

## 🚀 주요 기능

| 기능                     | 상태                                  | 설명 |
| ------------------------ | ------------------------------------- | ---- |
| **Kafka Event Consumer** | Diary Service의 일기 생성 이벤트 수신 |
| **AI 챗봇 대화**         | Spring AI + OpenAI/Bedrock 통합       |
| **RAG 시스템**           | Milvus Vector DB 기반 맥락 검색       |
| **스트리밍 API**         | SSE를 통한 실시간 답변                |

---

## 🛠️ 기술 스택

### Backend Framework

- **Spring Boot** 3.5.7 (Java 17)
- **Spring AI** 1.0.0-M4 (OpenAI, Bedrock 통합)
- **Spring WebFlux** (Reactive Streaming)
- **Spring Data JPA** (Hibernate ORM)
- **Spring Kafka** (Event-Driven Messaging)
- **Spring Security** (인증/인가)

### AI & Database

- **Spring AI OpenAI** - GPT-4o 통합
- **AWS Bedrock SDK** - Claude 3.5 Haiku, Titan Embeddings
- **Milvus** - Vector Database (RAG)
- **PostgreSQL** - Relational Database

### Infrastructure

- **Apache Kafka** - Event Streaming (Topic: `diary-events`, 3 partitions)
- **Docker Compose** - Local Kafka 환경
- **Gradle** - Build Tool
- **Swagger** - API 문서화

---

## 📁 프로젝트 구조 (DDD)
```

src/main/java/com/petlog/healthcare/
├── HealthcareApplication.java # Spring Boot Main
├── api/ # Presentation Layer
│ ├── controller/ # REST API
│ └── dto/ # Request/Response DTO
├── domain/ # Domain Layer
│ ├── entity/ # JPA Entity
│ ├── repository/ # JPA Repository
│ └── service/ # Business Logic
├── infrastructure/ # Infrastructure Layer
│ ├── ai/ # Spring AI Client
│ ├── kafka/ # Kafka Consumer
│ └── vector/ # Milvus Vector Store
├── config/ # Configuration
└── exception/ # Exception Handling

```

---

## ⚙️ 환경 설정

### 1. 사전 요구사항
- JDK 17 이상
- Gradle 8.x
- Docker & Docker Compose
- PostgreSQL 14+

### 2. 환경 변수 (.env)

```

# Database

DB_URL=jdbc:postgresql://localhost:5432/healthcaredb
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Kafka

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# AI (택 1)

OPENAI_API_KEY=sk-proj-xxxxx

# 또는

AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/xxxxx

# Milvus

MILVUS_HOST=localhost
MILVUS_PORT=19530

# Server

SERVER_PORT=8085

```

### 3. Kafka 실행

```

docker-compose up -d

```

### 4. 애플리케이션 실행

```

./gradlew clean build
./gradlew bootRun --args='--spring.profiles.active=local'

```

**실행 확인**:
- Healthcare Service: http://localhost:8085/actuator/health
- Kafka UI: http://localhost:8989
- Swagger: http://localhost:8085/swagger-ui/index.html

---

## 📡 Kafka 통합

### Event Flow

```

Diary Service (일기 작성)
→ Kafka Topic: diary-events
→ Healthcare Service (Consumer Group: healthcare-group)
→ RAG 처리 (Embeddings → Milvus)

```

### Kafka 구성

| 항목 | 값 |
|------|-----|
| **Topic** | `diary-events` |
| **Partitions** | 3 |
| **Consumer Group** | `healthcare-group` |
| **Event Types** | `DIARY_CREATED`, `DIARY_UPDATED`, `DIARY_DELETED` |

### 모니터링
- **Kafka UI**: http://localhost:8989
- **Consumer Lag**: Topics → `diary-events` → Consumer Groups

---

## 🧑‍💻 개발 가이드

### Git Workflow

```

# Feature 브랜치 생성

git checkout -b feat#XX/FEATURE_NAME

# Commit

git commit -m "feat: 기능 설명

WHY?

- 아키텍처 결정 이유

Technical Details:

- 기술적 세부사항

Closes #XX"

# Push

git push origin feat#XX/FEATURE_NAME

````

### Commit Convention
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링
- `chore`: 빌드/설정 변경
- `docs`: 문서 업데이트

---

## 📚 API 문서 (개발 예정)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/chat` | 동기식 AI 챗봇 대화 |
| `GET` | `/api/chat/stream` | 스트리밍 AI 챗봇 (SSE) |
| `GET` | `/api/chat/history` | 채팅 이력 조회 |
| `GET` | `/actuator/health` | 헬스체크 |

---

## 🌐 API Gateway 연동

Healthcare Service는 **API Gateway (Port 8000)**를 통해 Frontend와 통신합니다.

### 엔드포인트

| 경로 | 메서드 | 인증 | 설명 |
|------|--------|------|------|
| `/api/chat/health` | GET | ❌ | 헬스체크 |
| `/api/chat/haiku` | POST | ✅ JWT | 빠른 AI 채팅 (Haiku) |
| `/api/chat/persona` | POST | ✅ JWT | 페르소나 채팅 (RAG) |
| `/api/chat/test-chat` | POST | ✅ JWT | Sonnet 테스트 |

### Gateway 설정 가이드

API Gateway 연동을 위한 상세 설정은 다음 문서를 참고하세요:
- **[GATEWAY_INTEGRATION.md](docs/GATEWAY_INTEGRATION.md)** - API Gateway 변경 사항 가이드

### 테스트

```bash
# Gateway 연동 테스트 스크립트 실행
./scripts/test-gateway-integration.sh <JWT_TOKEN>
````

---

## 트러블슈팅

### Kafka Consumer 이벤트 수신 안 됨

```
# Kafka 컨테이너 상태 확인
docker ps | grep kafka

# Kafka UI에서 Topic 확인
http://localhost:8989 → Topics → diary-events
```

### Gradle 의존성 다운로드 실패

```
rm -rf ~/.gradle/caches
./gradlew --stop
./gradlew clean build --refresh-dependencies
```

### Spring AI 의존성 오류

```
// build.gradle에 추가
repositories {
    mavenCentral()
    maven { url 'https://repo.spring.io/milestone' }
}
```

---

## 포트 구성 (MSA)

| Service                | Port | Description         |
| ---------------------- | ---- | ------------------- |
| **API Gateway**        | 8000 | MSA Gateway         |
| **User Service**       | 8080 | 사용자 관리         |
| **Social Service**     | 8083 | 커뮤니티            |
| **Healthcare Service** | 8085 | AI 챗봇 (이 서비스) |
| **Diary Service**      | 8087 | 일기 관리           |
| **Kafka**              | 9092 | Event Broker        |
| **Kafka UI**           | 8989 | Kafka 모니터링      |

---

## 배포

### Docker 빌드

```
docker build -t healthcare-service:latest .
```

### Kubernetes (EKS)

```
kubectl apply -f k8s/deployment.yml
kubectl get pods -n petlog
```

---

## 👥 팀 정보

**Team 이음 (PetLog MSA Project)**

- Organization: [skRookies3team](https://github.com/skRookies3team)
- Repository: [healthcare_AIchatbot_service_backend](https://github.com/skRookies3team/healthcare_AIchatbot_service_backend)
- Frontend: https://d3uvkb1qxxcp2y.cloudfront.net/dashboard

### 개발 방법론

- **Agile** (Sprint 기반)
- **MSA** (Microservices Architecture)
- **Event-Driven** (Kafka)
- **DDD** (Domain-Driven Design)

---

## 📄 License

MIT License

0
