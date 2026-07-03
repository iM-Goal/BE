# iM AgentiX 🤖💰

> 목표만 말하면 AI와 스마트 컨트랙트가 저축을 실행합니다.

보증금 기반 미션 시스템으로 저축 목표를 달성하는 AI 금융 에이전트 플랫폼입니다.  
직접 미션(목표 기반 가용 금액 역산)과 자동 미션(4주 평균 대비 과소비 감지) 두 가지 방식으로 미션을 제안하고,  
iMKRW + Kaia 스마트 컨트랙트로 보증금을 관리합니다.

---

## 📐 아키텍처

```
[Frontend]
    │
    ▼
[Spring Boot API :8080]  ←──── JWT 인증
    │
    ├── [PostgreSQL(pgvector) :5432]
    ├── [Redis :6379]
    │
    ├── [Agent: Goal Parsing  :8001]  ─┐
    ├── [Agent: Spending      :8002]   ├── Ollama (qwen2.5:1.5b) 로컬 필요
    ├── [Agent: Mission (RAG) :8003]   │
    └── [Agent: Execution     :8004]  ─┘
```

---

## 🧩 서비스 구성

| 컨테이너 | 포트 | 역할 |
|---|---|---|
| `agentic-postgres` | 5432 | PostgreSQL + pgvector (벡터 DB) |
| `agentic-redis` | 6379 | 캐시, 세션 |
| `agentic-fastapi` | 8000 | FastAPI Mock 서버 |
| `agentic-agent-goal` | 8001 | 목표 파싱 AI 에이전트 |
| `agentic-agent-spending` | 8002 | 소비 분석 AI 에이전트 |
| `agentic-agent-mission` | 8003 | 미션 생성 AI 에이전트 (RAG) |
| `agentic-agent-execution` | 8004 | 판단 실행 AI 에이전트 |
| `agentic-backend-api` | 8080 | Spring Boot 메인 API 서버 |

---

## 🚀 빠른 시작

### 사전 요구사항

- Docker & Docker Compose
- (AI 기능 사용 시) [Ollama](https://ollama.com/) 설치 + 모델 pull

```bash
# Ollama 모델 다운로드 (AI 기능 사용 시 필요)
ollama pull qwen2.5:1.5b
```

### 프론트엔드 개발자 (백엔드 전체 환경 실행)

```bash
git clone <repo-url>
cd agentic

docker-compose up -d
```

> 백엔드 개발자가 최신 이미지를 Docker Hub에 push해뒀다면, 위 명령 한 줄로 Spring Boot 포함 전체 환경이 실행됩니다.

### 백엔드 개발자 (코드 수정 후 배포)

```bash
# Spring Boot 빌드 → Docker 이미지 빌드 → Docker Hub push → 컨테이너 재시작
./deploy-backend.sh
```

---

## 📡 API 문서 (Swagger)

| 서비스 | 주소 |
|---|---|
| **Spring Boot (메인)** | http://localhost:8080/swagger-ui.html |
| FastAPI Mock | http://localhost:8000/docs |
| Agent - Goal Parsing | http://localhost:8001/docs |
| Agent - Spending | http://localhost:8002/docs |
| Agent - Mission | http://localhost:8003/docs |
| Agent - Execution | http://localhost:8004/docs |

> Swagger에서 JWT 인증이 필요한 API는 로그인 후 발급받은 토큰을 `Authorize` 버튼에 입력하세요.

---

## ⚠️ Ollama 없이 사용 가능한 기능

Ollama가 로컬에 설치되어 있지 않아도 아래 기능은 정상 동작합니다.

| 기능 | Ollama 필요 여부 |
|---|---|
| 회원가입 / 로그인 (JWT) | ✅ 불필요 |
| 저축 목표 CRUD | ✅ 불필요 |
| 소비 내역 조회 | ✅ 불필요 |
| 보증금(iMKRW) 관리 | ✅ 불필요 |
| 대시보드 / 주간 리포트 | ✅ 불필요 |
| **AI 목표 파싱** | ❌ Ollama 필요 |
| **AI 소비 분석** | ❌ Ollama 필요 |
| **AI 미션 생성 (RAG)** | ❌ Ollama 필요 |
| **AI 판단 실행** | ❌ Ollama 필요 |

---

## 🗂 프로젝트 구조

```
agentic/
├── agentic-api/        # Spring Boot 메인 API (포트 8080)
├── agentic-common/     # 공통 모듈
├── agentic-domain/     # 도메인 모델
├── agentic-infra/      # 인프라 (DB, Redis 등)
├── agents/
│   ├── goal-parsing/   # AI 에이전트: 목표 파싱 (포트 8001)
│   ├── spending/       # AI 에이전트: 소비 분석 (포트 8002)
│   ├── mission/        # AI 에이전트: 미션 생성 (포트 8003)
│   └── execution/      # AI 에이전트: 판단 실행 (포트 8004)
├── fastapi-mock/       # 목업 API 서버 (포트 8000)
├── sql/                # DB 초기화 스크립트
├── docker-compose.yml
└── deploy-backend.sh   # 백엔드 빌드 & 배포 스크립트
```

---

## 🔑 환경변수 (docker-compose 기본값)

| 변수 | 기본값 |
|---|---|
| DB_HOST | postgres |
| DB_PORT | 5432 |
| DB_NAME | agentic |
| DB_USERNAME | agentic_user |
| DB_PASSWORD | agentic_password |
| REDIS_HOST | redis |
| REDIS_PORT | 6379 |
| OLLAMA_MODEL | qwen2.5:1.5b |

---

## 🛠 기술 스택

- **Backend**: Spring Boot, PostgreSQL (pgvector), Redis, JWT
- **AI Agents**: FastAPI (Python), Ollama (qwen2.5:1.5b), RAG
- **Blockchain**: iMKRW, Kaia 스마트 컨트랙트
- **Infra**: Docker, Docker Compose, Docker Hub
