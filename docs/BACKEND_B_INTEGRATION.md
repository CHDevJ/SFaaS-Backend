# Back_B 실행 및 팀 연결 방법

작업 위치: 기존 로컬 C:/Users/한국전파진흥협회/Desktop/스마트팩토리/SFaas/stf. 별도 복제본을 만들지 않았다.

## 실행

1. DB 담당자와 V3__backend_b_contract_and_jobs.sql을 검토한다. V1/V2는 DB seed 브랜치 원본이다.
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
- A의 AuthFlowService.SessionStore Bean이 있으면 HTTP·STOMP가 그 isAccessRevoked를 사용한다.
- Bean이 없을 때 공유 Redis 폐기 키: auth:access:revoked:{jti} 존재 시 거부. auth:user:revoked-before:{userId}의 epoch millisecond 이전·동일 발급 토큰도 거부. A는 이 키를 쓰거나 SessionStore를 제공해야 한다.
- telegram.port.NotificationPort는 A 가입 결과 알림용이다. 삭제 전 수신자 이름·chat ID를 스냅샷하고 커밋 후 호출한다.
- A의 별도 INT-001을 쓸 때 B의 선택적 수집 Controller를 끈다.

## V3와 계산 전제

- 원인 1000자, 조치 4000자, 오경보 상세 2000자, 교체 사유 1000자, 교체 내용 4000자와 소수 교체 기준을 지원한다. 교체 사유는 v5 자유 입력이다.
- backend_event_claim은 중복 방지, backend_job_lock은 당번 동시 수정, backend_alarm_impact는 발생 당시 예측·단가·생산률, backend_event_outbox는 커밋 후 발행·재시도를 저장한다.
- NORMAL_STREAK_COUNT 미설정 시 3회, STOCK_DANGER_MIN 미설정 시 10분. PART_DUE_SOON_PERCENT 미설정 시 프로젝트 제안 80%.
- 운영 일정 입력이 없는 현재 DB에서는 선택 기간 전체를 장비 계획 시간으로 사용한다. 실제 공장 일정이 정해지면 계산 입력을 교체한다.
- 손실 방지 실적은 복구 전 저장한 예측 스냅샷이 있는 효과 확인 완료 알람만 집계한다.

## 테스트

- ./gradlew.bat clean test --no-daemon.
- MySQL을 함께 검증하려면 테스트 전용 STF_TEST_MYSQL_URL·STF_TEST_MYSQL_PASSWORD를 제공한다. 격리 컨테이너의 stf_b_test URL이어야 하며 테스트마다 별도 스키마를 만든다.
- 환경변수가 없으면 MySqlBackendBTest만 건너뛴다. 이번에는 임시 MySQL을 제공해 233개 모두 실행했고 건너뜀이 없었다.
- H2 스키마는 V1/V3에서 MySQL 전용 문법을 바꾼 테스트 파일이며 실제 MySQL Flyway 검증이 이를 보완한다.
- npx --yes @redocly/cli@2.79.0 lint docs/openapi.yaml.

## 확인한 GitHub 기준

backend 5ff3f90, DB seed 267afd2, frontend 4bb5ec7. 프론트 REST 클라이언트·토픽·Vite 프록시·BACK_REQUESTS를 읽고 연결 규격을 맞췄다.
