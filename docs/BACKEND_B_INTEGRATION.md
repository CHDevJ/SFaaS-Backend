# 로컬 Swagger 수동 테스트 (2026-11-17)

기존 로컬 프로젝트의 전용 테스트 DB·Redis·서버가 준비되어 있다. 접속: http://localhost:8081/swagger-ui.html. 토큰 발급·시작·종료·테스트 명령은 scripts/manual-test/README.md를 따른다. 공유 DB와 분리된 stf_b_manual(3308), Redis(6380)를 사용한다. A 인증 구현(68a71fd·587f3b0)과 B 기능을 함께 검증하는 환경이다.

아래는 팀 통합 환경 안내다.

# Back_B 실행 및 팀 연결 방법

작업 위치: 기존 로컬 C:/Users/한국전파진흥협회/Desktop/스마트팩토리/SFaas/stf. 별도 복제본을 만들지 않았다.

## 실행

1. DB 담당자와 V3__backend_b_contract_and_jobs.sql 및 A의 V4__auth_session_columns.sql을 검토한다. V1/V2는 DB seed 브랜치 원본이며 V1→V2→V3→V4 순서로 적용한다.
2. application-local.example.properties를 참고해 Git에서 제외된 application-local.properties 또는 DB_URL·DB_USER·DB_PASSWORD를 설정한다. 현재 개발 MySQL 컨테이너의 호스트 포트는 3307이다.
3. JWT_SECRET은 A와 동일한 32바이트 이상 값, JWT_ISSUER도 동일하게 제공한다.
4. ./gradlew.bat bootRun --args='--spring.profiles.active=local,backend-b' 실행. 프로필 없이 가짜 빈 데이터 API를 제공하지 않는다.
5. A 수집 Controller가 없을 때만 --stf.backend-b.ingestion-enabled=true와 32바이트 이상 INTERNAL_API_KEY를 제공한다.
6. TELEGRAM_BOT_TOKEN·TELEGRAM_BOT_USERNAME을 설정하고 --stf.telegram.polling-enabled=true를 지정하면 long polling을 사용한다.

실행하면 설정한 DB에 Flyway가 적용되므로 공유 DB는 먼저 검토한다. 이번 테스트는 H2 또는 별도 격리 MySQL 스키마를 사용했다.

## 프론트

- REST /api/v1, STOMP /ws. Vite의 /api·/ws 프록시와 일치한다.
- STOMP CONNECT의 Authorization: Bearer 토큰, heartbeat 10초.
- 기본 Origin은 http://localhost:5173와 http://192.168.20.11:5173. STF_WS_ORIGINS로 변경한다.
- 개인 알림은 /user/queue/notifications만 구독하며 다른 사용자 경로와 클라이언트 SEND는 거부한다.
- OpenAPI의 x-websocket.topics.schema와 7개 Event 타입이 실제 발행 레코드와 일치한다.
- eventId로 중복을 제거한다. sequence는 발행 인스턴스 순서이며 재접속 후 REST로 현재 화면을 재조회한다.
- frontend/scripts/api.mjs는 origin/backend 고정 참조다. Back_B를 backend에 반영한 뒤 npm run api:sync와 npm run check로 갱신한다. frontend 소스는 이번 브랜치에서 바꾸지 않았다.
- v5 Secure Refresh 쿠키는 유지한다. LAN HTTP 시연은 A·프론트 담당자가 HTTPS 또는 localhost 프록시 구성으로 확인한다.

## A와의 경계

- BPorts.Users·Settings는 JDBC 구현을 제공하며 A 서비스가 준비되면 교체할 수 있다.
- HTTP SecurityFilterChain·JWT 서비스·Clock은 A 설정 하나만 사용한다. BHttpSecurityConfiguration과 B의 별도 JWT/Clock 빈은 제거했다.
- B HTTP Controller는 @AuthenticationPrincipal Claims를 받는다. STOMP도 같은 Claims·계정 상태와 SessionStore를 사용하며 개인 라우팅 이름은 계정 ID 문자열이다.
- BTokenGuard는 A의 AuthFlowService.SessionStore를 필수로 사용한다. Redis 대체 경로는 제거했다. A 구현의 auth:user:revoked-until:{userId}는 epoch second 기준이며 B가 직접 해석하지 않는다.
- JWT 설정은 app.jwt.*, 봇 이름은 app.telegram.bot-username으로 통일했다. 환경변수 JWT_SECRET·JWT_ISSUER·TELEGRAM_BOT_USERNAME은 그대로다.
- STOMP는 CONNECT·SUBSCRIBE·메시지 전송 전과 유휴 주기에 활성 계정·폐기·만료를 확인한다. 무효 연결은 1008로 종료하며 새 JWT를 받으면 다시 CONNECT한다. 기본 재확인 10초, manual-test 1초다. 개인 큐와 클라이언트 SEND 제한은 유지한다.
- A의 SignupDecidedEvent를 B Outbox에 원래 트랜잭션과 함께 저장한다. 커밋 후 SIGNUP_RESULT 알림을 전달하고 발송 결과를 별도 트랜잭션으로 남긴다. 거절된 신청자는 account_id=NULL과 이름 스냅샷을 사용한다. 재시도 시 같은 이벤트의 중복 발송을 막는다.
- A의 별도 INT-001을 쓸 때 B의 선택적 수집 Controller를 끈다.

## V3와 계산 전제

- 원인 1000자, 조치 4000자, 오경보 상세 2000자, 교체 사유 1000자, 교체 내용 4000자와 소수 교체 기준을 지원한다. 교체 사유는 v5 자유 입력이다.
- backend_event_claim은 중복 방지, backend_job_lock은 당번 동시 수정, backend_alarm_impact는 발생 당시 예측·단가·생산률, backend_event_outbox는 커밋 후 발행·재시도를 저장한다.
- NORMAL_STREAK_COUNT 미설정 시 3회, STOCK_DANGER_MIN 미설정 시 10분. PART_DUE_SOON_PERCENT 미설정 시 프로젝트 제안 80%.
- 운영 일정 입력이 없는 현재 DB에서는 선택 기간 전체를 장비 계획 시간으로 사용한다. 실제 공장 일정이 정해지면 계산 입력을 교체한다.
- 손실 방지 실적은 복구 전 저장한 예측 스냅샷이 있는 효과 확인 완료 알람만 집계한다.

## 테스트

최신 통합 결과와 GitHub 재확인은 BACKEND_B_GITHUB_RECHECK.md를 따른다. 아래 233개 결과는 통합 전 검증 기록이다.

- ./gradlew.bat clean test --no-daemon.
- MySQL을 함께 검증하려면 테스트 전용 STF_TEST_MYSQL_URL·STF_TEST_MYSQL_PASSWORD를 제공한다. 격리 컨테이너의 stf_b_test URL이어야 하며 테스트마다 별도 스키마를 만든다.
- 환경변수가 없으면 MySqlBackendBTest만 건너뛴다. 이번에는 임시 MySQL을 제공해 233개 모두 실행했고 건너뜀이 없었다.
- H2 스키마는 V1/V3에서 MySQL 전용 문법을 바꾼 테스트 파일이며 실제 MySQL Flyway 검증이 이를 보완한다.
- npx --yes @redocly/cli@2.79.0 lint docs/openapi.yaml.

## 확인한 GitHub 기준

backend b387df8(PR #11), A 587f3b0, frontend 1cdc56d(PR #12), Node-RED 89c201b. 최신 대응표는 BACKEND_B_GITHUB_RECHECK.md를 따른다.

## DB 담당자에게 전달할 검토 요청

아래 내용은 비밀정보 없이 바로 전달할 수 있다. 이 작업에서 메시지는 발송하지 않았다.

> V1·V2 원본은 그대로 사용 중이고 seed-data 브랜치와 파일 차이가 없습니다. Back_B의 V3__backend_b_contract_and_jobs.sql과 A의 V4__auth_session_columns.sql을 함께 검토 부탁드립니다.
>
> V3: v5 입력 길이에 맞춘 알람·조치·교체 컬럼, 소수 교체 기준, backend_event_claim(이벤트 중복 방지), backend_job_lock(당번 동시 수정), backend_alarm_impact(발생 당시 예측 스냅샷), backend_event_outbox(커밋 후 발행·재시도)입니다.
>
> V4: A 인증의 account.last_login_at, auth_token.family_id·used_at, family 인덱스입니다. 적용·병합 순서는 V1→V2→V3→V4가 필요합니다. 이미 실행된 V1·V2는 고치지 않고 신규 마이그레이션으로 관리하겠습니다.
>
> 분리된 실제 MySQL 8.4 환경에서 전체 마이그레이션과 B 읽기·쓰기 검증을 완료했습니다. 공유 DB 적용은 하지 않았습니다. 텔레그램 연결 코드는 Redis에 10분, 통신 끊김은 COMM_TIMEOUT_SEC=30을 사용합니다. 비밀번호는 별도 비공개 경로로 관리합니다.
