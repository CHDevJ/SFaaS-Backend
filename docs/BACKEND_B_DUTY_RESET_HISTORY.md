# 당번 감사·직책 변경 보호·비밀번호 초기화 발송 이력 (2026-11-17)

기존 로컬 `C:/Users/한국전파진흥협회/Desktop/스마트팩토리/SFaas/stf`의 `Back_B`에서 구현했다. 별도 복제본은 만들지 않았다. 기준은 v5 OpenAPI와 사용자가 확정한 대체 당번 우선 정책이다.

## 구현 결과

| 기능 | 처리 |
|---|---|
| 일반 당번 등록·수정·삭제 | A AuditRecorder로 시작일·종료일·담당자의 이전/변경 값, 당번 번호·변경자·시각을 같은 트랜잭션에 저장. 같은 값 수정은 감사 중복 제외, 감사 실패는 당번도 롤백 |
| 보전 직책 변경 | MAINTENANCE→다른 직책의 AccountPositionChangedEvent를 동기 처리. 현재·예정 일정 모두에 다른 활성 승인 보전 담당자가 있어야 함. 없으면 422 DUTY_REPLACEMENT_REQUIRED. 먼저 DUT-004로 재지정 후 변경 가능 |
| 과거 당번 보존 | 완료된 구성원과 일정 기간 보존. 허용된 직책 변경에서는 현재·예정 구성원만 제외하고 계정·당번·감사 원자성 보장 |
| 비밀번호 초기화 발송 이력 | A의 성공 확인 후 비밀번호 변경 정책 유지. B 우선 전송기가 공통 Telegram 전송기를 사용하고, 초기화 커밋/롤백 후 안전한 결과만 별도 트랜잭션으로 저장 |
| 비밀정보 제외 | notification_log는 PASSWORD_RESET 종류·수신자/이름 스냅샷·시각·결과·재시도 횟수만 기록. message는 고정 문구, 실패 사유도 고정 문구. 임시 비밀번호 원문·원문 메시지·chat ID·외부 응답은 결과 이벤트/Outbox/이력/로그에 저장하지 않음 |
| 이력 재처리 | Outbox에서 결과 저장만 재시도. 비밀번호를 다시 전송하지 않으며 같은 결과는 중복 저장하지 않음. Outbox 자체를 저장할 DB가 중단되면 비밀정보 없는 운영 오류 기록 |

새 HTTP 경로나 DTO, 마이그레이션은 추가하지 않았다. V1~V4와 A 단독 실행 전송기는 보존했다. B 기능은 backend-b 프로필에서 활성화한다.

## 실제 검증

| 검증 | 결과 |
|---|---|
| 관련 Gradle 테스트 | 70개 통과 |
| 전체 Gradle clean test --no-daemon | 453개·72개 클래스, 실패·오류·건너뜀 모두 0. BUILD SUCCESSFUL |
| 실제 MySQL | 전체 결과에 6개 포함. V1~V4, 당번 감사 저장, 계정 잠금 해제 후 초기화 성공/롤백의 발송 이력 저장 확인 |
| 실제 HTTP | 65개 통과. 직책 변경 422 → 일부 재지정 후 422 → 전체 재지정 후 200. 일반 당번 등록/수정/삭제 감사 7개 필드 기록도 확인 |
| 실제 STOMP | 인증 CONNECT·재고 구독/메시지·로그아웃·기존 연결 1008 종료, 4개 확인 통과 |
| OpenAPI | Redocly 2.79.0 lint 통과. 기존 STOMP 전용 7개 위치별 제외 유지 |
| Excel 보고서 | 기존 결과요약·테스트상세·서버확인 3개 시트를 최신 453개/65개로 갱신. 집계 수식 재계산, FAIL 전환/복구, 오류·표 범위·미리보기 검증 |

새 자동 테스트 20개: 감사·직책 변경 10개, 비밀번호 초기화 발송 결과 9개, 실제 MySQL 잠금·이력 1개다. 전체 실행에는 이전 계정 삭제 테스트 9개와 A 인증·사용자 관리 회귀도 포함된다. Telegram은 모의 전송기로만 검증했고 실제 외부 메시지는 보내지 않았다. 공유 DB·프론트 화면·PLC 실기기는 이번 검증 범위에 없다.

## 주요 변경 파일

- `operations/service/DutyApplicationService.java`, `operations/config/BackendBConfiguration.java`: 당번 감사 주입·필드별 기록
- `user/service/UserAdminService.java`, `user/event/AccountPositionChangedEvent.java`, `operations/event/AccountDutyCleanup.java`: 직책 변경 이벤트·대체 당번 검사
- `operations/adapter/BAuditedTemporaryPasswordSender.java`, `operations/event/PasswordResetDeliveryRecorded.java`, `PasswordResetDeliveryHistory.java`, `JdbcBEventOutbox.java`: 안전한 발송 결과 저장·재처리
- `AccountDutyCleanupIntegrationTest.java`, `PasswordResetDeliveryIntegrationTest.java`, `JdbcBFlowTest.java`, `MySqlBackendBTest.java`: 통합·롤백·민감정보 제외 검증
- `docs/API_SPEC.md`, `docs/openapi.yaml`, `scripts/manual-test/Smoke-Test.ps1`, 실행/남은 작업 문서, `docs/test-reports/STF_BackendB_단위테스트_보고서_2026-11-17.xlsx`

## 직접 확인

Swagger: http://localhost:8081/swagger-ui.html. 일반 당번 감사는 DB change_log의 DUTY 종류로 확인한다. 감사 조회 AUD-001 HTTP 구현은 A의 남은 범위이며 이번 작업에 추가하지 않았다. 비밀번호 초기화 결과는 관리자 GET `/api/v1/system/telegram/deliveries`에서 비밀정보 제외 고정 문구로 조회한다.

최신 준비물과 담당 구분은 [BACKEND_B_NEXT_STEPS.md](BACKEND_B_NEXT_STEPS.md)를 따른다. frontend 최신 1fa57fc(PR #16)는 통신 상태 표시 변경이며 이번 기능의 새 백엔드 계약은 요구하지 않았다. frontend 코드는 수정하지 않았다.
