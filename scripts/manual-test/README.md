# 로컬 수동 테스트 사용 방법

기존 `stf` 프로젝트의 `Back_B` 코드에서 실행한다. 별도 복제본은 없다.

## 지금 확인하기

Swagger: <http://localhost:8081/swagger-ui.html>

상태 확인: <http://localhost:8081/actuator/health>

PowerShell을 기존 `stf` 폴더에서 열고 다음을 실행한다.

```powershell
.\scripts\manual-test\Get-ManualTestToken.ps1 -Role SYSTEM_ADMIN -CopyToClipboard
```

Swagger 오른쪽 위 **Authorize**를 누르고 Value에 붙여넣는다. `Bearer `를 직접 붙이지 않는다. Authorize와 Close를 누른 후 기능을 펼쳐 **Try it out → Execute**로 실행한다. JWT는 30분 동안 유효하다. 새로고침 후에는 다시 입력한다. 브라우저 영구 저장은 사용하지 않는다.

역할별 테스트는 `-Role MAINTENANCE` 또는 `-Role LINE_MANAGER`로 새 토큰을 발급한다. 토큰은 테스트 DB의 승인된 계정(admin01 / maint01 / linemgr01)을 코드로 조회해 발급한다. A의 실제 로그인·가입·승인 API도 통합했다. 이 명령은 로그인 API와 별도로 B 기능만 빠르게 점검할 때 사용한다.

## 실제 로그인 API로 확인하기

```powershell
.\scripts\manual-test\Get-ManualTestLogin.ps1
```

이 명령이 출력하는 JSON을 Swagger `POST /api/v1/auth/login`의 요청 본문에 넣는다. 응답의 accessToken을 Authorize에 붙여넣는다. 출력에는 로컬 연습 계정 비밀번호가 포함되므로 Git·공유 문서에 복사하지 않는다.

역할별 연습 계정은 badmin01(SYSTEM_ADMIN), bmaint01(MAINTENANCE), bline01(LINE_MANAGER)이다. 비밀번호는 최초 실행 시 `.env.manual-test`에 무작위로 생성한다. 원래 V2 계정은 수정하지 않으며 연습 계정의 비밀번호나 역할을 바꾸어도 재시작으로 덮어쓰지 않는다.

봇 이름이 없으면 실제 회원가입 연결 URL을 만들 수 없다. 회원가입·실제 Telegram 연결은 TELEGRAM_BOT_USERNAME과 TELEGRAM_BOT_TOKEN 준비 후 확인한다. Refresh·로그아웃·내 정보·비밀번호 변경도 A 후속 구현을 통합했다. 갱신은 쿠키를 사용하고 로그아웃·비밀번호 변경 후에는 JWT를 다시 발급받는다. 비밀번호 변경은 해당 연습 계정에도 실제 적용되며 재시작으로 복원하지 않는다.

## 추천 확인 순서

| 순서 | API | 확인할 결과 |
|---|---|---|
| 1 | GET `/api/v1/overview/equipment-layout` | V2의 설비 27대와 현재 상태 |
| 2 | GET `/api/v1/inventory` | 부품 라인별 연습 재고 |
| 3 | GET `/api/v1/alarms` | `[테스트]` 이름의 연습 알람 |
| 4 | GET `/api/v1/alarms/{alarmId}` | 3번에서 받은 ID로 상세·추세·타임라인 |
| 5 | PATCH `/api/v1/alarms/{alarmId}/acknowledgement` | `assigneeUserId`는 아래 명령으로 조회 |
| 6 | POST `/api/v1/alarms/{alarmId}/actions` | `cause`, `actionContent`로 조치 등록 |
| 7 | GET `/api/v1/alarms/{alarmId}/effect` | 조치 후 효과 확인 상태 |

보전 계정 ID는 아래처럼 조회한다. 입력한 ID와 설비·항목 ID를 임의로 1로 가정하지 않는다.

```powershell
. .\scripts\manual-test\Common.ps1
Invoke-ManualSql "SELECT account_id,login_id,name,role FROM account WHERE approval_status='APPROVED' AND deleted_at IS NULL;"
```

기간 필드 예: `2026-11-17T00:00:00+09:00` / `2026-11-17T23:59:59+09:00`. URL을 직접 조합할 때 `+`는 `%2B`로 인코딩한다. Swagger 입력칸에는 원래 날짜 문자열을 넣는다. 날짜는 확인할 데이터 기간에 맞춘다.

현재 당번을 등록하지 않은 경우 DUT-003의 404 `DUTY_SCHEDULE_NOT_FOUND`는 정상이다. 첫 당번은 관리자 토큰으로 미래 기간과 승인된 보전 계정을 지정해 등록할 수 있다. 보전 토큰의 당번 등록은 403이어야 한다.

Telegram 봇 설정은 비워 두었다. 실제 발송·PLC 수집·자동 정상 복귀·프론트 화면은 별도 연동 대상이다. 연습 데이터는 첫 실행 때만 넣으며 재시작으로 사용자 조치 이력을 초기화하지 않는다.

## 다시 실행·종료·테스트

```powershell
# Docker Desktop 실행 후 서버 시작. 빌드·DB/Redis 준비·상태 확인을 포함한다.
.\scripts\manual-test\Start-ManualTest.ps1

# 전체 Gradle 테스트. MySQL 검증용 스키마는 수동 연습 DB와 분리한다.
.\scripts\manual-test\Run-Tests.ps1

# 실제 HTTP 검증. 전용 DB에 [SMOKE] 측정 항목·알람 이력이 남는다.
.\scripts\manual-test\Smoke-Test.ps1

# 실제 STOMP 연결·구독 후 로그아웃 1008 종료 검증
.\scripts\manual-test\Test-WebSocketSession.ps1

# 실제 STOMP 연결·구독 후 로그아웃 1008 종료 검증
.\scripts\manual-test\Test-WebSocketSession.ps1

# 테스트 서버만 종료
.\scripts\manual-test\Stop-ManualTest.ps1

# 테스트 서버와 전용 MySQL/Redis 종료. DB 볼륨은 보존한다.
.\scripts\manual-test\Stop-ManualTest.ps1 -StopDependencies
```

## 환경 위치

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.2 / Java 21 / 프로필 `backend-b,manual-test` |
| 서버 | `127.0.0.1:8081` |
| MySQL | `127.0.0.1:3308` / DB `stf_b_manual` / 사용자 `stf_b_manual` |
| Redis | `127.0.0.1:6380` |
| Compose 프로젝트 | `stf-back-b-manual` |
| 서버 로그 | `logs/manual-test-server.log`, `logs/manual-test-server-error.log` |
| 실행 파일 | `logs/manual-test-runtime.jar` (Gradle clean 파일 잠금 방지) |
| 인증·DB 설정 | `.env.manual-test`, `secrets/manual-test.properties` (Git 제외) |

기존 `compose.yaml`의 개발 DB(3307)·Redis(6379)와 분리했다. 테스트용 V1/V2/V3/V4 적용은 전용 DB에서만 수행한다. 공유 DB 적용과 backend 브랜치 머지는 별도다. 비밀번호·서명키·JWT는 문서·Git에 넣지 않는다.

최신 결과: Gradle 433개·실제 HTTP 62개·WebSocket 4개 확인 통과. Swagger는 B 40개와 A 인증·사용자·가입 승인 14개, 총 54개를 제공한다. 233개 Excel 보고서는 통합 전 스냅샷이다. 최신 통합 검증 결과는 docs/BACKEND_B_GITHUB_RECHECK.md를 따른다.

브라우저의 Secure Refresh 쿠키는 localhost 또는 HTTPS에서 확인한다. 현재 수동 서버는 같은 PC의 loopback 전용이다. 프론트 Vite에는 STF_BACKEND_URL=http://localhost:8081을 설정한다. Smoke-Test의 명시적 Cookie 헤더 검증은 브라우저 저장 검증을 대신하지 않는다.

브라우저의 Secure Refresh 쿠키는 localhost 또는 HTTPS에서 확인한다. 현재 수동 서버는 같은 PC의 loopback 전용이다. 프론트 Vite에는 STF_BACKEND_URL=http://localhost:8081을 설정한다. Smoke-Test의 명시적 Cookie 헤더 검증은 브라우저 저장 검증을 대신하지 않는다.

## 계정 삭제와 당번 재지정

현재·예정 당번의 유일 담당자인 계정은 422 DUTY_REPLACEMENT_REQUIRED로 삭제가 거절된다. 관리자 토큰으로 DUT-004(일정 수정)에서 다른 사용 중인 MAINTENANCE를 먼저 지정한 뒤 USR-004를 실행한다. 과거 이력과 duty 기간은 보존한다.

Smoke-Test는 [SMOKE] 임시 계정과 연속된 임시 일정을 사용해 422 → 일정 재지정 → 남은 일정 때문에 422 → 다음 일정 재지정 → 204 → 404 흐름을 검증한다. 임시 일정은 정리하고 계정은 논리 삭제하며 실제 비밀값은 결과에 저장하지 않는다. 사용자 관리와 B 정책을 함께 쓰려면 backend-b 프로필이 필요하다.
