# iKnow — Spring Boot Backend API 명세서

> 실시간 표정 기반 학습 이해도 감지 시스템의 Spring Boot 백엔드입니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.0.5 |
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
  → WebSocket으로 강사에게 즉시 푸시

  ② 강사 강의 내용 수신
  → POST /api/lecture-chunk
  → Alert DB 저장 (강의 원문 포함)

  ③ 이벤트 직후 2분 STT 원문 + 요약 수신
  → POST /api/lecture-summary
  → Alert에 lectureSummary + keywords 저장

  ④ 강사 PASS 버튼
  → DELETE /api/alerts/:alertId

[강사 브라우저]
  WebSocket 구독 /topic/alert/{sessionId}
  → 알림 수신 → 대시보드 표시
  → 마이크 녹음 → STT 변환 → POST /api/lecture-chunk
  → 알림 2분 후 STT 원문 → FastAPI /summarize 직접 호출 (Spring 미관여)
  → 요약 결과 → POST /api/lecture-summary
```

---

## 환경변수

`src/main/resources/application.yml`에서 직접 관리합니다.

---

## 데이터 모델

### Session

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `sessionId` | String(8) | 숫자 + 대문자 알파벳 8자리 (예: `AB12CD34`), 중복 시 재생성 |
| `classId` | String | 반 식별자 |
| `thresholdPct` | Integer | 혼란 감지 임계값 % (기본 50, 대시보드 참고용) |
| `curriculum` | String (TEXT) | 커리큘럼명 (대시보드 참고용) |
| `startedAt` | LocalDateTime | 세션 시작 시각 (자동) |
| `endedAt` | LocalDateTime | 세션 종료 시각 |
| `status` | Enum | `ACTIVE` / `ENDED` |

> 세션은 생성 후 24시간이 지나면 자동으로 `ENDED` 처리됩니다.

### Alert

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `sessionId` | String | 연관 세션 ID |
| `studentId` | String | 교육생 식별자 |
| `studentName` | String | 교육생 이름 |
| `studentCount` | Integer | 해당 시점 confused 학생 수 |
| `totalStudentCount` | Integer | 전체 참여 학생 수 |
| `capturedAt` | LocalDateTime | 이벤트 발생 시각 |
| `confusedScore` | Double | 혼란도 점수 (0.0 ~ 1.0) |
| `reason` | String (TEXT) | GPT 판단 이유 |
| `unclearTopic` | String (TEXT) | 강의 원문 텍스트 |
| `lectureText` | String (TEXT) | 이벤트 직후 2분 녹음 STT 원문 |
| `lectureSummary` | String (TEXT) | GPT 요약문 (FastAPI `/summarize` → 프론트가 전달) |
| `createdAt` | LocalDateTime | 저장 시각 (자동) |

### AlertKeyword

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `alertId` | Long | 연관 Alert ID |
| `keyword` | String | 키워드 (최대 3단어, 최대 3개) |

---

## REST API 명세

### 세션

---

#### `POST /api/sessions`
강사가 수업 시작 시 세션을 생성합니다.

**Request Body**
```json
{
  "classId": "1반",
  "thresholdPct": 50,
  "curriculum": "웹개발반"
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `classId` | Y | 반 식별자 |
| `curriculum` | Y | 커리큘럼명 (`자격증반`, `웹개발반` 등 사전 등록된 값) |
| `thresholdPct` | N | 혼란 임계값 %, 기본 50 (대시보드 참고용) |

**Response `200 OK`**
```json
{
  "sessionId": "AB12CD34",
  "classId": "1반",
  "thresholdPct": 50,
  "curriculum": "웹개발반",
  "status": "ACTIVE",
  "startedAt": "2024-01-01T09:00:00",
  "endedAt": null
}
```

---

#### `GET /api/sessions/{sessionId}`
세션 정보를 조회합니다. 24시간 초과 시 자동으로 `ENDED` 처리됩니다.

**Response `200 OK`** — Session 객체 반환

---

#### `PATCH /api/sessions/{sessionId}/end`
세션을 정상 종료합니다. 참여 중인 학생 전원을 퇴장 처리합니다.

**Response `200 OK`** — Session 객체 반환

---

#### `POST /api/sessions/{sessionId}/terminate`
세션을 강제 종료합니다. 세션이 없으면 아무 동작도 하지 않습니다.

**Response `204 No Content`**

---

### 혼란 이벤트

---

#### `POST /api/confused-events`
교육생 confused 이벤트를 수신합니다. 프론트엔드에서 연속 3회(30초) 감지 시 호출합니다.

수신 즉시 강사에게 WebSocket으로 푸시합니다.

**Request Body**
```json
{
  "studentId": "student_42",
  "studentName": "홍길동",
  "sessionId": "AB12CD34",
  "studentCount": 8,
  "totalStudentCount": 20,
  "capturedAt": "2024-01-01T09:15:00",
  "confusedScore": 0.72,
  "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다."
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `sessionId` | Y | 세션 ID |
| `capturedAt` | Y | 이벤트 발생 시각 |
| `studentId` | N | 교육생 식별자 |
| `studentName` | N | 교육생 이름 |
| `studentCount` | N | confused 학생 수 (없으면 1) |
| `totalStudentCount` | N | 전체 학생 수 (없으면 1) |
| `confusedScore` | N | 혼란도 점수 |
| `reason` | N | GPT 판단 이유 |

**Response `200 OK`** — Body 없음

---

#### `GET /api/sessions/{sessionId}/alerts`
세션 알림 이력을 조회합니다. (최신순)

**Response `200 OK`**
```json
[
  {
    "id": 1,
    "sessionId": "AB12CD34",
    "studentId": "student_42",
    "studentName": "홍길동",
    "studentCount": 8,
    "totalStudentCount": 20,
    "capturedAt": "2024-01-01T09:15:00",
    "confusedScore": 0.72,
    "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다.",
    "unclearTopic": "트랜잭션 격리 수준이란 무엇인가",
    "lectureText": "지금 설명드리는 트랜잭션 격리 수준은...",
    "lectureSummary": "트랜잭션 격리 수준의 차이를 설명하는 강의 내용입니다.",
    "keywords": ["트랜잭션", "격리 수준", "REPEATABLE READ"],
    "createdAt": "2024-01-01T09:15:01"
  }
]
```

---

#### `GET /api/sessions/{sessionId}/confused-events`
세션의 confused 이벤트 목록을 조회합니다. alerts와 동일한 데이터를 반환합니다.

---

#### `DELETE /api/alerts/{alertId}`
강사가 PASS 버튼 클릭 시 알림을 삭제합니다.

**Response `204 No Content`** — Body 없음

---

### 강의 원문

---

#### `POST /api/lecture-chunk`
강사 강의 내용(STT 텍스트)을 Alert으로 저장합니다.

**Request Body**
```json
{
  "sessionId": "AB12CD34",
  "classId": "1반",
  "studentCount": 8,
  "totalStudentCount": 20,
  "capturedAt": "2024-01-01T09:14:30",
  "audioText": "트랜잭션 격리 수준이란 무엇인가",
  "confusedScore": 0.45,
  "reason": "다수 학생 혼란 감지"
}
```

**Response `200 OK`** — Alert 객체 반환

---

#### `POST /api/lecture-summary`
FastAPI `/summarize`로 얻은 요약 결과를 Alert에 저장합니다.

**Request Body**
```json
{
  "alertId": 1,
  "summary": "트랜잭션 격리 수준(READ COMMITTED, REPEATABLE READ 등)의 차이를 설명하는 강의 내용입니다.",
  "recommendedConcept": "트랜잭션 격리 수준 재설명 필요",
  "keywords": ["트랜잭션", "격리 수준", "REPEATABLE READ"]
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `alertId` | Y | 연결할 Alert ID |
| `summary` | N | GPT 요약문 |
| `recommendedConcept` | N | 보충 권장 개념 (Alert의 `reason` 필드 덮어씀) |
| `keywords` | N | 키워드 목록 (최대 3단어씩, 최대 3개 저장) |

**Response `200 OK`** — Alert 객체 반환 (`keywords` 포함)

---

#### `GET /api/alerts/{alertId}/summary`
Alert 단건 조회입니다.

**Response `200 OK`** — Alert 객체 반환

---

### 대시보드

---

#### `GET /api/dashboard/classes?date={date}`
날짜별 반별 통계를 조회합니다.

**Query Parameter**

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `date` | Y | 조회 날짜 (형식: `2024-01-01`) |

**Response `200 OK`**
```json
[
  {
    "curriculum": "웹개발반",
    "classId": "1반",
    "alertCount": 12,
    "participantCount": 20,
    "avgConfusedScore": 0.64,
    "topTopics": [
      "트랜잭션 격리 수준이란 무엇인가",
      "JPA 연관관계 매핑"
    ],
    "recentAlerts": [ ]
  }
]
```

---

#### `GET /api/dashboard/keyword-report?date={date}&keyword={keyword}&curriculum={curriculum}&classId={classId}`
특정 키워드에 대한 혼란도 리포트를 조회합니다.

**Query Parameter**

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `date` | Y | 조회 날짜 (형식: `2024-01-01`) |
| `keyword` | Y | 조회할 키워드 |
| `curriculum` | N | 커리큘럼 필터. 생략 시 전체 커리큘럼 조회 |
| `classId` | N | 반 필터. 생략 시 **전체 반 조회** |

**Response `200 OK`**
```json
{
  "keyword": "트랜잭션",
  "curriculum": "웹개발반",
  "classId": null,
  "date": "2024-01-01",
  "alertCount": 5,
  "avgUnderstanding": 42,
  "reinforcementNeed": 58,
  "reinforcementLevel": "보통",
  "report": "'트랜잭션' 관련 알림은 5건이며 평균 이해도는 42%입니다. 보충 필요도는 보통(58%)으로 판단됩니다.",
  "occurrenceTimes": ["09:15", "09:32", "10:05"]
}
```

| 필드 | 설명 |
|------|------|
| `avgUnderstanding` | 평균 이해도 % (100 - avgConfusion) |
| `reinforcementNeed` | 보충 필요도 % (= avgConfusion) |
| `reinforcementLevel` | `높음` (≥70%) / `보통` (≥40%) / `낮음` (<40%) |
| `occurrenceTimes` | 해당 키워드 알림 발생 시각 (최대 5개) |

---

## WebSocket 명세

### 연결

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `ws://{host}/ws` |
| 프로토콜 | SockJS + STOMP |

### 구독 토픽 `/topic/alert/{sessionId}`

`POST /api/confused-events` 수신 즉시 강사에게 푸시됩니다.

**수신 메시지**
```json
{
  "sessionId": "AB12CD34",
  "classId": "1반",
  "studentCount": 8,
  "totalStudentCount": 20,
  "confusedScore": 0.72,
  "reason": "fear 수치가 높고 눈썹이 찡그려진 상태로 혼란 신호가 명확합니다.",
  "capturedAt": "2024-01-01T09:15:00"
}
```

---

## 파일 구조

```
src/main/java/com/iknow/
├── IknowApplication.java             # 시작 시 커리큘럼 시드 데이터 삽입
├── config/
│   ├── WebSocketConfig.java          # SockJS + STOMP 설정
│   └── CorsConfig.java               # CORS 전체 허용
├── controller/
│   ├── SessionController.java        # POST, GET, PATCH /:id/end, POST /:id/terminate
│   ├── ConfusedEventController.java  # POST /api/confused-events, GET alerts/confused-events
│   ├── AlertController.java          # DELETE /api/alerts/:alertId
│   ├── LectureChunkController.java   # POST /api/lecture-chunk
│   ├── LectureSummaryController.java # POST /api/lecture-summary, GET /api/alerts/:id/summary
│   └── DashboardController.java      # GET /api/dashboard/classes, GET /api/dashboard/keyword-report
├── service/
│   ├── SessionService.java           # 8자리 세션ID 생성, 24시간 자동 만료
│   ├── SessionParticipantService.java
│   ├── ConfusedEventService.java     # WebSocket 푸시
│   ├── LectureChunkService.java      # Alert 저장
│   ├── LectureSummaryService.java    # 요약 + 키워드 저장
│   └── DashboardService.java         # 날짜별 반별 통계, 키워드 리포트
├── entity/
│   ├── Session.java
│   ├── Alert.java
│   ├── AlertKeyword.java
│   ├── Curriculum.java
│   └── SessionParticipant.java
├── repository/
│   ├── SessionRepository.java
│   ├── AlertRepository.java
│   ├── AlertKeywordRepository.java
│   ├── CurriculumRepository.java
│   └── SessionParticipantRepository.java
└── dto/
    ├── request/
    │   ├── CreateSessionRequest.java
    │   ├── ConfusedEventRequest.java
    │   ├── LectureChunkRequest.java
    │   └── LectureSummaryRequest.java
    └── response/
        ├── SessionResponse.java
        ├── AlertResponse.java
        ├── AlertWebSocketPayload.java
        ├── DashboardClassResponse.java
        └── KeywordReportResponse.java

src/main/resources/
└── application.yml
```

---

## 실행 방법

### 로컬 실행

```bash
./gradlew bootRun
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다.

### 테스트 실행

```bash
./gradlew test
```

### 참고

- 현재 테스트와 애플리케이션 실행은 `src/main/resources/application.yml`에 설정된 MySQL 연결 정보를 사용합니다.
- Windows 환경에서는 `gradlew.bat bootRun`, `gradlew.bat test`로 실행할 수 있습니다.
