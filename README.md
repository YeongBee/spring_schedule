# Quartz 동적 스케줄러 백엔드 가이드 (`schejulde`)

웹(클라이언트)에서 REST API를 통해 실시간으로 **Cron 배치 스케줄을 등록·수정·일시정지·재개·즉시실행·삭제**하고, 다중 컨테이너(클러스터) 환경에서도 중복 실행 없이 동작하도록 구현된 **Spring Boot 3 + Quartz JDBC JobStore** 기반 백엔드 프로젝트입니다.

---

## 1. 기술 스택 및 환경

| 구분 | 기술 / 버전 | 비고 |
| :--- | :--- | :--- |
| **Framework** | Spring Boot `3.2.5` | Spring Web, Spring Data JPA, Validation |
| **Scheduler** | Spring Boot Starter Quartz | `JDBC JobStore` + 클러스터링(`isClustered=true`) |
| **Database (Local/Test)** | H2 Database (In-Memory) | 테스트 및 로컬 개발용 (`jdbc:h2:mem:scheduledb`) |
| **Database (Prod)** | Oracle DB (`ojdbc11`) | 운영 다중 컨테이너 공유 DB |
| **Language** | Java (Toolchain 25 / Release 21) | Lombok `1.18.38` |

---

## 2. 백엔드 패키지 및 클래스 구조

```text
src/main/java/com/sub/schejulde
├── SchejuldeApplication.java          # Spring Boot 메인 애플리케이션
├── config
│   └── WebCorsConfig.java             # CORS 허용 설정 (/api/**)
├── controller
│   └── ScheduleController.java        # 스케줄 관리 REST API 엔드포인트
├── service
│   └── ScheduleService.java           # Quartz Scheduler 제어 비즈니스 로직
├── job
│   └── DynamicTaskJob.java            # 실제 스케줄 트리거 시 실행되는 QuartzJobBean
├── domain
│   └── ScheduleHistory.java           # 스케줄 실행 이력 JPA 엔티티 (SEQ_SCHEDULE_HISTORY)
├── repository
│   └── ScheduleHistoryRepository.java # 실행 이력 조회/저장 JPA Repository
└── dto
    ├── ScheduleRequest.java           # 스케줄 신규 생성 요청 DTO (POST)
    ├── ScheduleUpdateRequest.java     # 스케줄 수정 요청 DTO (PUT)
    └── ScheduleResponse.java          # 스케줄 상태 및 메타데이터 응답 DTO
```

### 핵심 컴포넌트 역할
1. **`DynamicTaskJob` (`QuartzJobBean`)**
   * `@DisallowConcurrentExecution`이 적용되어 있어 동일 Job이 이전 실행이 끝나기 전에 중복 실행되는 것을 방지합니다.
   * 스케줄 등록 시 전달된 `JobDataMap`(`taskType`, `payload` 등)을 `context.getMergedJobDataMap()`으로 꺼내 비즈니스 로직을 수행하고, 성공/실패 여부를 `ScheduleHistory` 테이블에 기록합니다.
2. **`ScheduleService`**
   * Quartz의 `Scheduler` 빈을 주입받아 `JobDetail`과 `CronTrigger`를 조립하고 DB(`QRTZ_*` 테이블)에 영속화합니다.
   * 복합 식별자인 `JobKey(jobName, jobGroup)`와 `TriggerKey(jobName + "_trigger", jobGroup)`를 기준으로 스케줄을 관리합니다.
3. **다중 컨테이너(2대 이상) 중복 실행 방지**
   * `spring.quartz.job-store-type=jdbc` 및 `isClustered=true`, `instanceId=AUTO` 설정을 통해 DB의 `QRTZ_LOCKS` 테이블(`SELECT ... FOR UPDATE`)로 분산 락을 획득한 **단 1대의 컨테이너만** 스케줄을 실행합니다.

---

## 3. 전체 동작 흐름

```
[Client 요청 (POST /api/schedules)]
        │
        ▼
[ScheduleController] ──(@Valid 검증)──▶ [ScheduleService]
                                              │
                       ┌──────────────────────┴──────────────────────┐
                       ▼                                             ▼
          1. CronExpression 유효성 검증                 2. JobDetail(DynamicTaskJob) 및
          (CronExpression.isValidExpression)               CronTrigger 생성 후 등록
                                                                     │
                                                                     ▼
                                                       [DB QRTZ_* 테이블에 영속화]
                                                       (QRTZ_JOB_DETAILS, QRTZ_TRIGGERS,
                                                        QRTZ_CRON_TRIGGERS, QRTZ_LOCKS)
                                                                     │
                                              (Cron 주기 도달 시 1개 컨테이너가 Lock 획득)
                                                                     │
                                                                     ▼
                                                             [DynamicTaskJob]
                                                       작업 실행 및 ScheduleHistory 저장
```

---

## 4. REST API 상세 명세 (백엔드 인입 형태)

모든 API의 기본 경로는 `/api/schedules`이며, 요청/응답 본문은 `application/json` 형식입니다.

### 4.1 스케줄 생성 (`POST /api/schedules`)

새로운 Cron 스케줄을 등록합니다. `jobGroup`을 생략하거나 빈 값으로 보내면 `"DEFAULT"` 그룹으로 지정됩니다.

* **Request**
  ```http
  POST /api/schedules HTTP/1.1
  Content-Type: application/json

  {
    "jobName": "dailyReportJob",
    "jobGroup": "REPORT",
    "cronExpression": "0/30 * * * * ?",
    "description": "30초마다 일일 리포트 발송",
    "jobData": {
      "taskType": "EMAIL_REPORT",
      "payload": "{\"target\":\"admin@sub.com\"}"
    }
  }
  ```
* **Response (`201 Created`)**
  ```json
  {
    "jobName": "dailyReportJob",
    "jobGroup": "REPORT",
    "description": "30초마다 일일 리포트 발송",
    "cronExpression": "0/30 * * * * ?",
    "timeZone": "Asia/Seoul",
    "triggerState": "NORMAL",
    "nextFireTime": "2026-10-25T21:35:00",
    "previousFireTime": null,
    "jobData": {
      "taskType": "EMAIL_REPORT",
      "payload": "{\"target\":\"admin@sub.com\"}"
    }
  }
  ```

---

### 4.2 전체 스케줄 목록 조회 (`GET /api/schedules`)

현재 등록된 모든 스케줄의 상태(`NORMAL`, `PAUSED`, `ERROR` 등)와 다음 실행 예정 시각(`nextFireTime`)을 조회합니다.

* **Request**
  ```http
  GET /api/schedules HTTP/1.1
  ```
* **Response (`200 OK`)**
  ```json
  [
    {
      "jobName": "dailyReportJob",
      "jobGroup": "REPORT",
      "description": "30초마다 일일 리포트 발송",
      "cronExpression": "0/30 * * * * ?",
      "timeZone": "Asia/Seoul",
      "triggerState": "NORMAL",
      "nextFireTime": "2026-10-25T21:35:30",
      "previousFireTime": "2026-10-25T21:35:00",
      "jobData": {
        "taskType": "EMAIL_REPORT",
        "payload": "{\"target\":\"admin@sub.com\"}"
      }
    }
  ]
  ```

---

### 4.3 단건 스케줄 상세 조회 (`GET /api/schedules/{jobGroup}/{jobName}`)

* **Request**
  ```http
  GET /api/schedules/REPORT/dailyReportJob HTTP/1.1
  ```
* **Response (`200 OK`)**: `4.1`의 단건 JSON 객체와 동일

---

### 4.4 스케줄 수정 (`PUT /api/schedules/{jobGroup}/{jobName}`)

대상 리소스 식별자(`jobGroup`, `jobName`)는 URL Path 변수로 전달하고, 본문(`ScheduleUpdateRequest`)에는 변경할 **Cron 표현식, 설명, 작업 데이터(`jobData`)**만 담아 전송합니다.

* **Request**
  ```http
  PUT /api/schedules/REPORT/dailyReportJob HTTP/1.1
  Content-Type: application/json

  {
    "cronExpression": "0/10 * * * * ?",
    "description": "10초 주기로 변경",
    "jobData": {
      "taskType": "EMAIL_REPORT",
      "payload": "{\"target\":\"ops@sub.com\"}"
    }
  }
  ```
* **Response (`200 OK`)**
  ```json
  {
    "jobName": "dailyReportJob",
    "jobGroup": "REPORT",
    "description": "10초 주기로 변경",
    "cronExpression": "0/10 * * * * ?",
    "timeZone": "Asia/Seoul",
    "triggerState": "NORMAL",
    "nextFireTime": "2026-10-25T21:36:10",
    "previousFireTime": "2026-10-25T21:36:00",
    "jobData": {
      "taskType": "EMAIL_REPORT",
      "payload": "{\"target\":\"ops@sub.com\"}"
    }
  }
  ```

---

### 4.5 스케줄 일시정지 (`POST /api/schedules/{jobGroup}/{jobName}/pause`)

스케줄을 삭제하지 않고 트리거 상태를 `PAUSED`로 변경하여 실행을 멈춥니다.

* **Request**
  ```http
  POST /api/schedules/REPORT/dailyReportJob/pause HTTP/1.1
  ```
* **Response (`200 OK`)**: `triggerState`가 `"PAUSED"`로 변경된 `ScheduleResponse` 반환

---

### 4.6 스케줄 재개 (`POST /api/schedules/{jobGroup}/{jobName}/resume`)

일시정지(`PAUSED`)된 스케줄을 다시 `NORMAL` 상태로 복구합니다. (`withMisfireHandlingInstructionDoNothing` 설정에 의해 정지되어 있던 동안 지나간 트리거는 한꺼번에 몰아서 실행되지 않고 다음 정각 주기부터 실행됩니다.)

* **Request**
  ```http
  POST /api/schedules/REPORT/dailyReportJob/resume HTTP/1.1
  ```
* **Response (`200 OK`)**: `triggerState`가 `"NORMAL"`로 변경된 `ScheduleResponse` 반환

---

### 4.7 스케줄 즉시 1회 실행 (`POST /api/schedules/{jobGroup}/{jobName}/run-now`)

Cron 주기와 무관하게 즉시 해당 Job을 1회 트리거합니다. 일시정지(`PAUSED`) 상태인 스케줄도 수동 1회 실행이 가능합니다.

* **Request**
  ```http
  POST /api/schedules/REPORT/dailyReportJob/run-now HTTP/1.1
  ```
* **Response (`200 OK`)**
  ```json
  {
    "message": "스케줄 즉시 실행이 요청되었습니다."
  }
  ```

---

### 4.8 스케줄 삭제 (`DELETE /api/schedules/{jobGroup}/{jobName}`)

Quartz에서 해당 Job과 연결된 Trigger를 영구 삭제합니다.

* **Request**
  ```http
  DELETE /api/schedules/REPORT/dailyReportJob HTTP/1.1
  ```
* **Response (`204 No Content`)**: 본문 없음

---

### 4.9 스케줄 실행 이력 조회 (`GET /api/schedules/histories`)

`DynamicTaskJob`이 실행될 때마다 기록된 최근 50건의 실행 이력을 최신순으로 반환합니다.

* **Request**
  ```http
  GET /api/schedules/histories HTTP/1.1
  ```
* **Response (`200 OK`)**
  ```json
  [
    {
      "id": 1,
      "jobName": "dailyReportJob",
      "jobGroup": "REPORT",
      "status": "SUCCESS",
      "message": "작업 수행 완료 (taskType=EMAIL_REPORT, payload={\"target\":\"admin@sub.com\"})",
      "executedAt": "2026-10-25T21:35:00.124"
    }
  ]
  ```

---

### 4.10 에러 응답 형태 (`GlobalExceptionHandler`)

모든 에러 응답은 `{"error": "메시지"}` 형태로 통일되어 있습니다.

* **입력값 검증 실패 (`400 Bad Request`)**
  ```json
  {
    "error": "jobName: 영문, 숫자, '_', '-' 만 사용할 수 있습니다.",
    "details": [
      "jobName: 영문, 숫자, '_', '-' 만 사용할 수 있습니다."
    ]
  }
  ```
* **잘못된 Cron 표현식 (`400 Bad Request`)**
  ```json
  {
    "error": "유효하지 않은 Quartz Cron 표현식입니다: invalid-cron"
  }
  ```
* **중복된 스케줄 등록 시도 (`409 Conflict`)**
  ```json
  {
    "error": "이미 동일한 스케줄이 존재합니다: [REPORT.dailyReportJob]"
  }
  ```
* **존재하지 않는 스케줄 조회/수정/삭제 (`404 Not Found`)**
  ```json
  {
    "error": "스케줄을 찾을 수 없습니다: [REPORT.noSuchJob]"
  }
  ```

---

## 5. 요청/응답 페이로드(Payload) 필드 상세 설명

### 5.1 스케줄 생성 및 수정 요청 필드 (`ScheduleRequest` / `ScheduleUpdateRequest`)

| 필드명 | 필수 여부 | 설명 및 Quartz 내부 역할 |
| :--- | :---: | :--- |
| **`jobName`** | **필수** *(생성 시)* | **스케줄(작업)의 고유 이름**입니다. (최대 100자, 영문/숫자/`_`/`-`만 허용)<br>Quartz 내부에서 `JobKey(jobName, jobGroup)`의 이름 부분으로 들어가며, 같은 그룹(`jobGroup`) 내에서는 중복될 수 없습니다. 수정(`PUT`) 시에는 URL Path로 전달합니다. |
| **`jobGroup`** | 선택 *(생성 시)* | **스케줄을 묶어서 분류하는 카테고리(폴더) 이름**입니다. (최대 100자, 영문/숫자/`_`/`-`만 허용)<br>예: `"REPORT"`(리포트용), `"SETTLEMENT"`(정산용), `"ALARM"`(알림용). 생략하거나 빈 값이면 백엔드에서 자동으로 `"DEFAULT"`로 지정합니다. 수정(`PUT`) 시에는 URL Path로 전달합니다. |
| **`cronExpression`** | **필수** | **언제, 어떤 주기로 실행할지 정의하는 Quartz Cron 표현식**입니다. (최대 120자)<br>예: `"0/30 * * * * ?"` (30초마다), `"0 0 9 * * ?"` (매일 오전 9시). 등록/수정 시 백엔드가 유효한 식인지 먼저 검증합니다. |
| **`description`** | 선택 | 관리자가 스케줄의 용도를 알아보기 쉽도록 적어두는 **스케줄 메모/설명**입니다. (최대 250자, `QRTZ_JOB_DETAILS.DESCRIPTION` 컬럼에 저장) |
| **`jobData`** | 선택 | **스케줄이 실행될 때 `DynamicTaskJob`에게 넘겨줄 동적 파라미터 바구니(`Map<String, String>`)**입니다.<br>Quartz의 `useProperties=true` 설정과 함께 문자열 키-값(Properties) 형태로 DB(`QRTZ_JOB_DETAILS.JOB_DATA`)에 안전하게 영속화됩니다. |
| ↳ `jobData.taskType` | 선택 (커스텀) | `DynamicTaskJob` 안에서 **"어떤 종류의 작업을 수행할지" 분기(if/switch)하기 위한 구분값**입니다.<br>예: `"EMAIL_REPORT"`면 이메일 발송 로직 실행, `"DATA_CLEANUP"`이면 오래된 DB 데이터 정리 실행. |
| ↳ `jobData.payload` | 선택 (커스텀) | 해당 작업 실행 시 **실제로 필요한 세부 인자값**입니다.<br>예: 이메일 발송 대상(`{"target":"admin@sub.com"}`)이나 호출할 웹훅 URL 등을 문자열(JSON String 등)로 넣어두면 Job 실행 시점에 꺼내 사용합니다. |

### 5.2 스케줄 조회 응답 필드 (`ScheduleResponse`)

| 필드명 | 설명 |
| :--- | :--- |
| **`timeZone`** | Cron이 해석되는 시간대입니다. 컨테이너 시간대와 관계없이 항상 `"Asia/Seoul"`입니다. |
| **`triggerState`** | 현재 스케줄 트리거의 **실행 상태**입니다.<br>• `NORMAL`: 정상 대기 중 (Cron 주기가 도래하면 실행됨)<br>• `PAUSED`: 일시정지 상태 (`/pause` 호출 시 전환되며 주기가 되어도 실행되지 않음)<br>• `BLOCKED`: 현재 해당 Job이 **실행 중**이어서(`@DisallowConcurrentExecution`) 다음 실행이 대기 중인 상태<br>• `ERROR`: 트리거 설정 또는 Job 클래스 로드에 문제가 발생한 상태 |
| **`nextFireTime`** | Quartz가 Cron 표현식을 계산해 둔 **다음번 실행 예정 시각**입니다. (한국 시간) |
| **`previousFireTime`** | **가장 마지막에 실행되었던 시각**입니다. (한국 시간, 스케줄 등록 직후 아직 한 번도 실행되지 않았다면 `null`) |

### 5.3 스케줄 실행 이력 응답 필드 (`ScheduleHistory`)

| 필드명 | 설명 |
| :--- | :--- |
| **`id`** | DB 시퀀스(`SEQ_SCHEDULE_HISTORY`)로 자동 채번된 실행 이력 고유 번호(PK) |
| **`jobName` / `jobGroup`** | 어떤 스케줄이 실행된 기록인지 나타내는 작업 이름과 그룹 |
| **`status`** | 실행 결과 상태 (`"SUCCESS"` 또는 예외 발생 시 `"FAIL"`) |
| **`message`** | 실행 완료 요약 메시지 또는 실패 시 발생한 예외 메시지(`e.getMessage()`) |
| **`executedAt`** | 실제 `DynamicTaskJob`이 실행된 시각 |

---

## 6. Quartz Cron 표현식 작성 규칙 및 해부 (`"0/30 * * * * ?"`)

Linux 표준 Cron(5자리, 분 단위 시작)과 달리 **Quartz Cron은 맨 앞자리가 `초(Seconds)`인 6자리(연도 포함 시 7자리) 형식**을 사용합니다.

### 6.1 `"0/30 * * * * ?"` 자리별 상세 의미

```text
  0/30       *          *          *          *          ?
 ──────    ─────      ─────      ─────      ─────      ─────
 ① 초      ② 분       ③ 시       ④ 일       ⑤ 월       ⑥ 요일
(Seconds) (Minutes)  (Hours)    (Day of    (Month)    (Day of
                                 Month)                Week)
```

| 자리 | 입력된 값 | 의미 |
| :---: | :---: | :--- |
| **① 초 (Seconds)** | `0/30` | **0초부터 시작해서 매 30초 간격으로 실행** (`0초`, `30초`에 실행)<br>※ `시작값/간격` 문법입니다. 예: `5/10`이면 5초부터 10초마다(5초, 15초, 25초...) |
| **② 분 (Minutes)** | `*` | **매 분(Every minute)**마다 조건 만족 (`0분 ~ 59분` 모두) |
| **③ 시 (Hours)** | `*` | **매 시간(Every hour)**마다 조건 만족 (`0시 ~ 23시` 모두) |
| **④ 일 (Day of Month)** | `*` | **매일(Every day)** 조건 만족 (`1일 ~ 31일` 모두) |
| **⑤ 월 (Month)** | `*` | **매월(Every month)** 조건 만족 (`1월 ~ 12월` 모두) |
| **⑥ 요일 (Day of Week)** | `?` | **특정 요일을 지정하지 않음 (No specific value)**<br>※ Quartz Cron에서는 **`④ 일`과 `⑥ 요일`을 동시에 `*`로 쓸 수 없습니다.** 날짜(`④ 일`)에 `*`나 숫자를 썼다면 요일(`⑥ 요일`) 자리에는 반드시 **`?`**를 넣어야 에러가 나지 않습니다. (반대로 요일을 `MON-FRI`로 지정하면 `④ 일` 자리에 `?`를 넣습니다.) |

👉 **최종 해석**: **"매월, 매일, 매시간, 매분 0초와 30초마다(즉, 30초 주기로 계속) 실행하라"**는 뜻입니다.

---

### 6.2 특수 기호 핵심 요약

| 기호 | 의미 | 예시 |
| :---: | :--- | :--- |
| **`*`** | 모든 값 (매 초, 매 분, 매 시...) | 분 자리에 `*` → 매 분마다 |
| **`?`** | 특정 값 없음 (`일` 또는 `요일` 중 한 곳에만 필수 사용) | `0 0 9 * * ?` (요일 무관 매일 9시) |
| **`/`** | 시작값과 증가 간격 (`시작/주기`) | 초 자리에 `0/10` → 0초부터 10초마다 |
| **`,`** | 여러 값 나열 | 시 자리에 `9,18` → 오전 9시와 오후 6시 |
| **`-`** | 범위 지정 | 요일 자리에 `MON-FRI` → 월요일부터 금요일까지 |
| **`L`** | 마지막(Last) 날짜 또는 요일 | 일 자리에 `L` → 매월 말일(28, 30, 31일) |

---

### 6.3 자리별 허용 범위 및 실무 자주 쓰는 예시

| 순서 | 필드명 | 허용 값 | 허용 특수문자 |
| :---: | :--- | :--- | :--- |
| 1 | **초 (Seconds)** | `0 ~ 59` | `, - * /` |
| 2 | **분 (Minutes)** | `0 ~ 59` | `, - * /` |
| 3 | **시 (Hours)** | `0 ~ 23` | `, - * /` |
| 4 | **일 (Day of Month)** | `1 ~ 31` | `, - * ? / L W` |
| 5 | **월 (Month)** | `1 ~ 12` 또는 `JAN ~ DEC` | `, - * /` |
| 6 | **요일 (Day of Week)** | `1 ~ 7` 또는 `SUN ~ SAT` | `, - * ? / L #` |

* **실무 자주 쓰는 예시**
  * `0/10 * * * * ?` : 10초마다 실행 (`0초, 10초, 20초, 30초, 40초, 50초`)
  * `0 0/5 * * * ?` : 5분마다 정각(0초)에 실행 (`0분 0초, 5분 0초, 10분 0초...`)
  * `0 0 9 * * ?` : 매일 오전 9시 00분 00초에 실행
  * `0 30 18 ? * MON-FRI` : 평일(월~금) 오후 6시 30분 00초에 실행 (요일을 지정했으므로 `④ 일` 자리에 `?` 사용)
  * `0 0 0 L * ?` : 매월 말일 자정(00:00:00)에 실행

---

## 7. 운영 환경(Oracle DB + 컨테이너 2대) 배포 시 체크리스트

### 7.1 프로필 구성

| 파일 | 용도 | 주요 설정 |
| :--- | :--- | :--- |
| `application.properties` | 공통 | Quartz JDBC JobStore, 클러스터링(`isClustered=true`), 기본 프로필 `local` |
| `application-local.properties` | 로컬/테스트 (H2) | `initialize-schema=always`, `ddl-auto=update`, H2 콘솔 활성화 |
| `application-prod.properties` | 운영 (Oracle) | `initialize-schema=never`, `ddl-auto=validate`, `OracleDelegate`, 접속 정보는 환경변수 주입 |

프로필을 지정하지 않으면 `local`(H2)로 실행되며, 운영에서는 아래와 같이 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:oracle:thin:@//<ORACLE_HOST>:1521/<SERVICE_NAME> \
DB_USERNAME=<USER> \
DB_PASSWORD=<PASSWORD> \
java -jar schejulde.jar
```

### 7.2 운영 DB 최초 1회 DDL 실행

운영은 테이블을 자동으로 생성하지 않으므로(컨테이너 2대가 동시에 기동될 때 DDL 충돌 방지), 배포 전에 아래 두 스크립트를 Oracle에서 1회 실행합니다.

1. **Quartz 테이블(`QRTZ_*`)**: quartz jar 안의 `org/quartz/impl/jdbcjobstore/tables_oracle.sql`
2. **실행 이력 테이블 및 시퀀스**: [`src/main/resources/db/oracle/schedule_history.sql`](src/main/resources/db/oracle/schedule_history.sql)

### 7.3 시간대: 항상 한국 시간(Asia/Seoul) 기준으로 실행

운영 컨테이너의 시간대(UTC 등)와 **관계없이 모든 Cron은 한국 시간 기준으로 해석**됩니다.

* `ScheduleTimeZone.ZONE_ID`(`Asia/Seoul`)를 `CronScheduleBuilder.inTimeZone()`에 지정합니다.
* 이 시간대는 `QRTZ_CRON_TRIGGERS.TIME_ZONE_ID` 컬럼에 함께 저장되므로, **클러스터의 어느 컨테이너가 실행하더라도** `0 0 9 * * ?`는 항상 한국 시간 오전 9시에 실행됩니다.
* API 응답의 `nextFireTime`, `previousFireTime`과 실행 이력의 `executedAt`도 모두 **한국 시간**으로 반환됩니다.
* 응답의 `timeZone` 필드(`"Asia/Seoul"`)로 트리거에 적용된 시간대를 확인할 수 있습니다.
* 테스트는 JVM 시간대를 UTC로 고정한 상태(`build.gradle`의 `user.timezone=UTC`)에서 실행되어, 운영 컨테이너 조건에서도 한국 시간 기준으로 동작하는지 검증합니다.

### 7.4 컨테이너 간 시간 동기화 (NTP)
* 클러스터에 참여하는 컨테이너들의 서버 시각이 서로 1초 이내로 동기화되어 있어야 락 경합과 트리거 인계가 정확하게 동작합니다.
