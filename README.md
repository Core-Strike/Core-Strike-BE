# iKnow — Spring Boot Backend API 명세서

> 실시간 표정 기반 학습 이해도 감지 시스템의 Spring Boot 백엔드입니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.x |
| 데이터베이스 | MySQL 8 |
| ORM | Spring Data JPA |
| 실시간 통신 | WebSocket + STOMP (SockJS) |
| 빌드 | Gradle |

---

## 전체 시스템에서의 역할

```
[교육생 브라우저]
  연속 3회 confused 감지 (30초)
  → POST /api/confused-events          ← 이 서비스로 전송

[Spring Boot]                          ← 이 서비스
  ① confused 이벤트 수신
  → Alert DB 저장
  → 해당 시점 강의 토픽 매칭 (LectureTopic)
  → WebSocket으로 강사에게 즉시 푸시

  ② 강사 STT 텍스트 수신
  → POST /api/lecture-chunk
  → LectureTopic DB 저장

[강사 브라우저]
  WebSocket 구독 /topic/alert/{sessionId}
  → 알림 수신 → 대시보드 표시
  → 마이크 녹음 → STT 변환 → POST /api/lecture-chunk
```

---

## 환경변수

`application.yml`은 아래 환경변수를 참조합니다. 미설정 시 괄호 안의 기본값을 사용합니다.

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DB_HOST` | `localhost` | MySQL 호스트 |
| `DB_NAME` | `iknow` | 데이터베이스명 |
| `DB_USERNAME` | `root` | DB 계정 |
| `DB_PASSWORD` | `password` | DB 비밀번호 |

---

## 데이터 모델

### Session

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK (자동 생성) |
| `sessionId` | String | UUID (프론트엔드와 공유하는 식별자) |
| `classId` | String | 반 식별자 |
| `startedAt` | LocalDateTime | 세션 시작 시각 (자동 기록) |
| `endedAt` | LocalDateTime | 세션 종료 시각 |
| `status` | Enum | `ACTIVE` / `ENDED` |

### Alert

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `sessionId` | String | 연관 세션 ID |
| `studentId` | String | 교육생 식별자 |
| `capturedAt` | LocalDateTime | confused 발생 시각 |
| `confusedScore` | Double | 혼란도 점수 (0.0 ~ 1.0) |
| `reason` | String | GPT 판단 이유 |
| `unclearTopic` | String | 매칭된 강의 토픽 (LectureTopic에서 자동 조회) |
| `createdAt` | LocalDateTime | 저장 시각 (자동 기록) |

### LectureTopic

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `sessionId` | String | 연관 세션 ID |
| `classId` | String | 반 식별자 |
| `topicText` | String (TEXT) | 강사 음성 STT 변환 텍스트 |
| `capturedAt` | LocalDateTime | 녹음 시각 |
| `createdAt` | LocalDateTime | 저장 시각 (자동 기록) |

---

## REST API 명세

### 세션

---

#### `POST /api/sessions`
세션을 생성합니다. 강사가 수업을 시작할 때 호출합니다.

**Request Body**
```json
{
  "classId": "class-1"
}
```

**Response `200 OK`**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "classId": "class-1",
  "status": "ACTIVE",
  "startedAt": "2024-01-01T09:00:00",
  "endedAt": null
}
```

---

#### `PATCH /api/sessions/{sessionId}/end`
세션을 종료합니다. 강사가 수업을 끝낼 때 호출합니다.

**Path Parameter**

| 파라미터 | 설명 |
|----------|------|
| `sessionId` | 종료할 세션 ID |

**Response `200 OK`**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "classId": "class-1",
  "status": "ENDED",
  "startedAt": "2024-01-01T09:00:00",
  "endedAt": "2024-01-01T10:30:00"
}
```

---

### 혼란 이벤트

---

#### `POST /api/confused-events`
교육생의 confused 이벤트를 수신합니다.
프론트엔드에서 연속 3회(30초) confused 감지 시 호출합니다.

수신 즉시 Alert를 DB에 저장하고, 해당 시점 가장 가까운 강의 토픽(LectureTopic)을 자동으로 매칭한 뒤 강사에게 WebSocket으로 푸시합니다.

**Request Body**
```json
{
  "studentId": "student_42",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "capturedAt": "2024-01-01T09:15:00",
  "confusedScore": 0.72,
  "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다."
}
```

**Response `200 OK`**

응답 Body 없음. 처리 성공 시 강사 WebSocket으로 알림이 푸시됩니다.

---

#### `GET /api/sessions/{sessionId}/alerts`
세션의 알림 이력을 조회합니다. (최신순)

**Path Parameter**

| 파라미터 | 설명 |
|----------|------|
| `sessionId` | 조회할 세션 ID |

**Response `200 OK`**
```json
[
  {
    "id": 1,
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "studentId": "student_42",
    "capturedAt": "2024-01-01T09:15:00",
    "confusedScore": 0.72,
    "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다.",
    "unclearTopic": "트랜잭션 격리 수준이란 무엇인가",
    "createdAt": "2024-01-01T09:15:01"
  }
]
```

---

#### `GET /api/sessions/{sessionId}/confused-events`
세션의 confused 이벤트 목록을 조회합니다. (최신순)

alerts와 동일한 데이터를 반환합니다.

**Response** — `/api/sessions/{sessionId}/alerts`와 동일

---

### 강의 토픽

---

#### `POST /api/lecture-chunk`
강사 음성의 STT 변환 텍스트를 저장합니다.

강사 브라우저에서 confused 알림 수신 시점 전후의 마이크 청크를 STT로 변환하여 호출합니다. 저장된 토픽은 이후 confused 이벤트 발생 시 `unclearTopic`으로 자동 매칭됩니다.

**Request Body**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "classId": "class-1",
  "topicText": "트랜잭션 격리 수준이란 무엇인가",
  "capturedAt": "2024-01-01T09:14:30"
}
```

**Response `200 OK`**

응답 Body 없음.

---

### 대시보드

---

#### `GET /api/dashboard/classes`
반별 통계를 조회합니다. 관리자 대시보드에서 사용합니다.

**Response `200 OK`**
```json
[
  {
    "classId": "class-1",
    "alertCount": 12,
    "avgConfusedScore": 0.64,
    "topTopics": [
      "트랜잭션 격리 수준이란 무엇인가",
      "JPA 연관관계 매핑",
      "인덱스 동작 원리"
    ],
    "recentAlerts": [
      {
        "id": 5,
        "sessionId": "550e8400-e29b-41d4-a716-446655440000",
        "studentId": "student_42",
        "capturedAt": "2024-01-01T09:15:00",
        "confusedScore": 0.72,
        "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다.",
        "unclearTopic": "트랜잭션 격리 수준이란 무엇인가",
        "createdAt": "2024-01-01T09:15:01"
      }
    ]
  }
]
```

| 필드 | 설명 |
|------|------|
| `classId` | 반 식별자 |
| `alertCount` | 총 알림 발생 횟수 |
| `avgConfusedScore` | 평균 혼란도 점수 |
| `topTopics` | 자주 혼란이 발생한 강의 내용 (빈도 상위 5개) |
| `recentAlerts` | 최근 알림 10개 |

---

## WebSocket 명세

### 연결

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `ws://{host}/ws` |
| 프로토콜 | SockJS + STOMP |
| 클라이언트 라이브러리 | `@stomp/stompjs` + `sockjs-client` |

### 구독 토픽

#### `/topic/alert/{sessionId}`

강사 브라우저가 구독합니다. `POST /api/confused-events` 수신 시 자동으로 메시지가 전송됩니다.

**수신 메시지 예시**
```json
{
  "studentId": "student_42",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "confusedScore": 0.72,
  "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다.",
  "unclearTopic": "트랜잭션 격리 수준이란 무엇인가",
  "capturedAt": "2024-01-01T09:15:00"
}
```

---

## 파일 구조

```
src/main/java/com/iknow/
├── IknowApplication.java
├── config/
│   ├── WebSocketConfig.java       # SockJS + STOMP 설정
│   └── CorsConfig.java            # CORS 전체 허용 (운영 시 도메인 제한 권장)
├── controller/
│   ├── SessionController.java     # POST /api/sessions, PATCH /api/sessions/:id/end
│   ├── ConfusedEventController.java  # POST /api/confused-events, GET /api/sessions/:id/alerts|confused-events
│   ├── LectureChunkController.java   # POST /api/lecture-chunk
│   └── DashboardController.java   # GET /api/dashboard/classes
├── service/
│   ├── SessionService.java
│   ├── ConfusedEventService.java  # Alert 저장 + WebSocket 푸시 + 토픽 매칭
│   ├── LectureChunkService.java
│   └── DashboardService.java      # 반별 집계 쿼리
├── entity/
│   ├── Session.java
│   ├── Alert.java
│   └── LectureTopic.java
├── repository/
│   ├── SessionRepository.java
│   ├── AlertRepository.java
│   └── LectureTopicRepository.java
└── dto/
    ├── request/
    │   ├── CreateSessionRequest.java
    │   ├── ConfusedEventRequest.java
    │   └── LectureChunkRequest.java
    └── response/
        ├── SessionResponse.java
        ├── AlertResponse.java
        ├── AlertWebSocketPayload.java
        └── DashboardClassResponse.java

src/main/resources/
└── application.yml
```

---

## 실행 방법

```bash
# 환경변수 설정 후 실행
export DB_HOST=localhost
export DB_NAME=iknow
export DB_USERNAME=root
export DB_PASSWORD=password

./gradlew bootRun
# → http://localhost:8080
```
