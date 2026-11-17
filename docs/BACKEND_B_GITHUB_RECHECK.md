# GitHub 재확인과 A/B 통합 (2026-11-17)

기존 로컬 `C:/Users/한국전파진흥협회/Desktop/스마트팩토리/SFaas/stf`의 `Back_B`에서 작업했다. 복제본은 없다. 기준은 `docs/openapi.yaml` v5다.

## 최신 확인

| 원격 코드 | 기준 | 반영 |
|---|---|---|
| Back_B | e49e82f | 기존 A/B 통합 103개 파일 커밋·푸시 완료 |
| backend | b387df8 / PR #11 | A 원본 이력을 2e6c50d 병합 커밋으로 연결. 단일 보안 체인과 B 내부 키·STOMP 설정 유지 |
| feature/SF5-54-auth-signup-login | 587f3b0 | A 후속 AUTH-002/003/004/006 Controller·DTO·테스트 반영 |
| frontend | 1cdc56d / PR #12 (head 6c0289e) | 변경 5개 파일과 BACK_REQUESTS를 확인. 아래 대응표에 정리 |
| Node-RED das / PR #10·#13 | 3958e82 / 75e797b | 기존 가상 데이터 코드와 추가 PowerShell 키 전달 안내 확인. PR #13은 프론트 변경이 아니며 B API 변경 불필요 |

GitHub 연결 도구의 권한 오류 때문에 인증된 기존 로컬 Git의 fetch·원격 refs·diff로 확인했다. PR 댓글·리뷰 상태와 프론트 UI 실행 결과는 이번 확인 범위에 포함하지 않는다.

## 프론트엔드 PR #12 대응

| 요청 | 처리 |
|---|---|
| AUTH-002/003/004/006 없음 | A 최신 구현을 통합. Refresh 회전 → 내 정보 → B API → 로그아웃·폐기를 연결 검증 |
| 서버 주소 | 같은 PC의 수동 테스트는 `http://localhost:8081`, Vite `STF_BACKEND_URL=http://localhost:8081`. 현재 서버는 loopback 전용 |
| Secure Refresh 쿠키 | v5의 HttpOnly·Secure·SameSite=Strict 유지. LAN HTTP에서 보안 속성을 끄는 변경은 적용하지 않음. 브라우저 시연은 localhost 또는 HTTPS 구성 필요 |
| WebSocket Origin·메시지 타입 | localhost:5173·192.168.20.11:5173 허용과 기존 7개 이벤트 스키마 제공. CONNECT·SUBSCRIBE뿐 아니라 전송 전 및 유휴 주기에도 계정·만료·폐기 검사 |
| accessToken writeOnly | LoginData·TokenData를 readOnly 응답 필드로 정정. 비밀번호·대체 refreshToken 입력의 writeOnly 유지 |
| develop 브랜치 안내 | 실제 통합 대상인 backend로 CONTRIBUTING.md 정정 |
| 계약 동기화 시점 | frontend의 api:sync가 origin/backend 고정이므로 Back_B를 backend에 병합한 뒤 갱신. 이번에는 frontend 소스를 수정하지 않음 |
| 오류 코드 전체 Enum·USR-005·요구사항 번호 | 공통 예외·사용자 API는 A/명세 담당자 확인 대상. 새 오류 정책을 임의로 추가하지 않음 |
| 알람 상세의 정지 예상·발송 목록·구조화 조치 필드 | 현재 AlarmDetailData에 없는 확장. 신규 필드·대상별 노출·발송 건수 집계 기준을 계약으로 정한 뒤 구현 |
| MEAS-002 다중 발생 ±30분 | 현재 v5 계약은 단일 알람 ±10분. 기존 사용자 확정값 유지. 화면 요구와 통합할 때 명세 변경 필요 |
| 종 알림의 과거 목록·읽음 처리 | REST 경로·보관 기간·읽음 저장 정책이 미정이므로 보류. 실시간 개인 알림 큐는 제공 |
| main 폴더 구조 통합 | backend/main 병합과 폴더 이동은 이번 작업에서 수행하지 않음 |

## 추가 B 구현

- `BWebSocketSessions`는 실제 연결 ID와 검증된 Claims만 보관한다. 원문 JWT를 저장하거나 로그에 남기지 않는다.
- 전송 전 무효 세션 메시지를 차단한다. 유휴 연결도 기본 10초 간격으로 확인하고 정책 위반 코드 1008로 닫는다. 수동 테스트 프로필만 1초다.
- 만료·로그아웃·계정 삭제·폐기 저장소 장애에 대해 차단한다. 같은 사용자의 다른 유효 로그인은 유지하고 연결 종료 시 항목을 정리한다.
- A의 SecurityConfig·JWT·Clock·SessionStore를 공유한다. 기존 가입 결정 Outbox, 내부 수집 키, 개인 구독 제한은 유지한다.
- Smoke-Test는 실제 세션 갱신·폐기 검증을 추가했다. `Test-WebSocketSession.ps1`은 실제 로그인 → CONNECT → inventory SUBSCRIBE·실제 메시지 수신 → HTTP 로그아웃 → 기존 소켓 1008 종료를 확인한다.

## 검증 결과

- Gradle `clean test --no-daemon`: **398개 통과**, 실패 0 / 오류 0 / 건너뜀 0, 68개 클래스.
- 실제 MySQL 8.4 검증 4개 포함. V1→V2→V3→V4 전체 마이그레이션과 읽기·쓰기·제약을 검증했다. 공유 DB는 변경하지 않았다.
- 실제 실행 서버 HTTP: **54개 통과**, 실패 0. JWT·Refresh 값은 결과 JSON에서 제거했다.
- 실제 WebSocket: **4개 확인 통과**, 실패 0. 기존 구독 연결이 로그아웃 뒤 1008로 종료됨을 확인했다.
- Swagger: B 40개 + A 인증·가입 승인 9개 = **49개 operation**.
- OpenAPI Redocly 2.79.0 lint 통과. STOMP 전용 7개 unused 위치별 제외는 그대로이며 실제 이벤트 필드 계약 테스트도 통과했다.
- HTTP 스크립트는 쿠키를 명시적으로 전달한다. 브라우저의 Secure 쿠키 저장이나 프론트 새로고침 화면까지 검증한 결과는 아니다.
- 실제 Telegram·PLC 발송/수집은 외부 환경 확인 대상이다. 이번 검증에서 외부 메시지를 보내지 않았다.
- 기존 Excel 보고서는 통합 전 **233개 테스트 스냅샷**으로 보존한다. 최신 결과는 이 문서와 Gradle HTML/XML을 기준으로 한다.

## 트러블슈팅 기록

- TS-24 삭제 파일 비밀정보 검사 실패: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3473409
- TS-25 SecurityConfig add/add 충돌: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3506177
- TS-26 Swagger 최초 문서 확인 시간 초과: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3571713
- TS-27 STOMP receipt 검증 가정 수정: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3637249
- TS-28 기존 WebSocket 구독의 인증 유지 검사: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3670017
- TS-29 응답 토큰 writeOnly 표기: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3702785

커밋 전 실제 런타임 비밀값과 자격증명 형태를 검사한다. .env·secrets·logs·build는 커밋하지 않는다. Back_B에만 푸시하고 backend/main은 자동 병합하지 않는다.

## DB 담당자 안내 대조

- feature/SF5-18-seed-data의 V1__init_schema.sql·V2__seed_data.sql과 현재 파일의 Git diff는 없다. 원본을 수정하지 않았다.
- account는 telegram_chat_id·telegram_linked_at을 사용한다. 텔레그램 연결 코드는 RedisTelegramLinkTokenStore에 해시 키와 TTL 10분으로 보관하고 getAndDelete로 한 번만 소비한다. DB에 연결 코드 테이블을 추가하지 않는다. 관련 테스트도 전체 398개에 포함된다.
- 통신 끊김은 V2의 COMM_TIMEOUT_SEC=30과 수집 서비스의 같은 설정값을 사용한다. WebSocket JWT 세션 검사 주기 10초와는 별도 설정이다.
- 공통 개발 DB 안내는 3307/stf이며 현재 수동 검증은 분리된 3308/stf_b_manual이다. 전달받은 비밀번호로 기존 계정이나 테스트 환경 비밀번호를 바꾸지 않았다.
- 안내에 포함된 볼륨 삭제 명령은 실행하지 않았다. 비밀번호·JWT·봇 토큰은 코드·문서·Git에 추가하지 않는다.
- 공유 DB에서는 DB 담당자가 B의 V3와 A의 V4를 검토하고 V1→V2→V3→V4 순서를 맞춰야 한다. 이번 작업은 공유 DB 적용을 수행하지 않았다.

## 일반 Spring Boot 실행의 JWT 설정 오류 수정 (2026-11-17)

- 원인: manual-test 프로필과 달리 일반 실행에는 유효한 JWT_SECRET 전달 경로가 없었다.
- 변경: application.properties에서 optional:file:./secrets/jwt-local.properties를 읽는다. scripts/Setup-LocalJwt.ps1은 Git 제외를 확인하고 안전한 난수 키를 생성하며 유효한 기존 파일은 보존한다. README에 IDE 작업 디렉터리와 초기 설정 순서를 추가했다.
- 현재 PC의 Git 제외 파일을 생성했다. 실제 비밀값은 코드·문서·로그에 포함하지 않는다. 프로세스 환경변수 JWT_SECRET이 우선하며 최소 32 UTF-8 바이트 검증은 유지한다.
- 재검증: Gradle clean test 398개 통과, 실패·오류·건너뜀 0. MySQL 검증 4개 포함.
- 실제 JAR 4개 확인 통과: 파일·환경변수 모두 없으면 기동 거절, 짧은 환경변수가 유효한 로컬 파일보다 우선하여 기동 거절, JWT 환경변수 없이 파일만 있으면 health 200, 실제 로그인과 B API 200.
- 검증에는 전용 3308 DB·6380 Redis와 임시 8082 포트를 사용했다. 검증 세션은 로그아웃하고 임시 프로세스를 종료했다. 공유 DB와 일반 개발 DB는 변경하지 않았다.
- 생성 스크립트 재실행 시 기존 키 보존, PowerShell 문법 검사, 실제 비밀값 5개 대조 검사도 통과했다.
- 오류 기록: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3833857 (TS-30), https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/3604483 (TS-31).
- 공유 범위는 실행 설정·키 생성 스크립트·README·이 검증 문서 4개다. 실제 로컬 키 파일과 .env·secrets·logs·build는 Git에서 제외한다.

## A 사용자 관리와 당번 제외 최종 통합 (2026-11-17)

- A 원본 0a6e0e2를 merge --no-ff로 통합해 원본 계보를 보존한다. 기존 JWT 로컬 설정·STOMP 7개 스키마·readOnly·단일 보안 구성을 유지했다.
- 사용자는 마지막 담당자인 계정을 삭제하기 전에 대체 보전 담당자를 먼저 지정하는 정책을 확정했다. 422 DUTY_REPLACEMENT_REQUIRED를 USR-004와 API_SPEC 오류 표에 반영했다.
- 동기 AccountDutyCleanup은 같은 트랜잭션에서 현재·예정 멤버 제외와 DUTY 감사 기록을 처리한다. 완료된 과거 멤버·기간은 보존한다. 실패 시 계정 삭제까지 롤백한다.
- 당번 후보 계정→DUTY 순서의 잠금과 과거 이름 조회/신규 검증을 보완했다. A의 Entity·Repository를 B 업무 코드에서 직접 참조하지 않았다.
- Gradle clean test: **433개 통과, 71개 클래스, 실패·오류·건너뜀 0**, 실제 MySQL 검증 5개 포함.
- 실제 HTTP **62개 통과**, 실제 STOMP **4개 확인 통과**, Swagger **54개 operation**. 실제 Telegram·PLC 발송/수집은 수행하지 않았다.
- OpenAPI lint 통과. STOMP 전용 7개 위치별 제외만 유지한다. V1/V2 및 V3/V4 마이그레이션 변경은 없다.
- 새 검증: AccountDutyCleanupIntegrationTest(9개)와 MySqlBackendBTest의 계정 제외 검증, Smoke-Test의 사용자 조회·재지정·삭제 흐름.
- 관련 오류 TS-33: https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/4063245. 테스트 실패 주입을 AOP 실제 대상으로 수정했으며 제품 트랜잭션 정책은 유지했다.
- 남은 구현과 필요한 준비물·API 목록은 BACKEND_B_NEXT_STEPS.md에 정리했다. 기존 Excel 보고서는 233개 통합 전 스냅샷이다.
