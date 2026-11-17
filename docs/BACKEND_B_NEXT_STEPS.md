# Back_B 통합 결과와 남은 구현·준비물 (2026-11-17)

기존 로컬 `C:/Users/한국전파진흥협회/Desktop/스마트팩토리/SFaas/stf`에서 작업했다. A의 `0a6e0e2`를 원본 이력과 함께 통합하며, 별도 복제본을 만들지 않았다. 기준은 로컬 v5 OpenAPI와 이번 사용자의 대체 당번 우선 정책이다.

## 완료한 범위

- 최신 추가: 일반 당번 등록·수정·삭제의 필드 감사, 대체 당번 우선 보전 직책 변경, 비밀정보를 제외한 비밀번호 초기화 발송 결과 이력 구현. 상세는 [BACKEND_B_DUTY_RESET_HISTORY.md](BACKEND_B_DUTY_RESET_HISTORY.md).

- A: AUTH-001~006, APR-001~003, USR-001~005, 총 14개 HTTP operation을 B 40개와 함께 제공한다. 수동 실행 Swagger는 54개다.
- B: AccountDeletedEvent를 계정 삭제 트랜잭션 안에서 동기 처리한다. 현재·예정 일정의 다른 사용 중인 MAINTENANCE가 없으면 422 DUTY_REPLACEMENT_REQUIRED로 삭제를 거절한다.
- 관리자는 기존 DUT-004로 대체 담당자를 먼저 지정한다. 허용된 삭제에서는 해당 계정의 현재·예정 구성원만 제외하고 duty 기간과 완료된 과거 구성원은 보존한다.
- 계정 삭제·당번 제외·감사 기록은 모두 커밋되거나 모두 롤백된다. 같은 이벤트 재처리는 제외 내역을 중복 기록하지 않는다.
- 과거 캘린더는 삭제된 계정의 이력용 이름으로 조회한다. 과거 담당자가 삭제됐다는 이유로 새 일정 등록을 막지 않으며, 삭제 계정을 새 담당자로 지정하는 요청은 거절한다.
- 당번 신규·수정은 후보 계정 잠금을 먼저 잡고 공통 당번 잠금을 잡아 계정 삭제와 잠금 순서를 맞췄다.
- JWT 로컬 파일 읽기·32바이트 검증, 단일 보안 체인·JWT·Clock, SessionStore, STOMP 구독 보호와 응답 토큰 readOnly를 유지했다.
- 새 마이그레이션은 없다. V1/V2 원본과 V3/V4를 수정하지 않았다. 공유 DB에는 적용하지 않았다.

## 검증

| 검증 | 실제 결과 |
|---|---|
| Gradle clean test --no-daemon | 453개 통과, 72개 클래스, 실패·오류·건너뜀 0 |
| 실제 MySQL 8.4 | 위 결과에 6개 포함. 전체 V1~V4, B 읽기/쓰기, 삭제 거절·대체자 존재 시 구성원만 제외 검증 |
| A/B 삭제 통합 테스트 | 대체 담당자 없으면 거절, 모든 일정 검사, 비활성/다른 직책 제외, 과거 이력·기간 보존, 감사 실패 전체 롤백, 이벤트 재처리, 삭제 후 B HTTP·WebSocket 거절 |
| 실제 서버 HTTP | 65개 통과. 사용자 목록·상세, 삭제 422 → 첫 일정 재지정 → 남은 일정 때문에 422 → 다음 일정 재지정 → 삭제 204 → 일반 조회 404 포함 |
| 실제 STOMP | CONNECT·재고 구독/메시지·로그아웃·1008 종료 4개 확인 통과 |
| OpenAPI | Redocly 2.79.0 lint 통과. 기존 STOMP 전용 7개 unused 위치별 제외 유지 |

실제 HTTP 검증은 전용 `3308/stf_b_manual`·`6380`만 사용했다. 임시 일정은 검증 후 정리하고 임시 계정은 논리 삭제해 비밀값을 지웠다. 실제 Telegram/PLC 통신과 브라우저 화면 검증은 수행하지 않았다. 기존 Excel 보고서도 453개 테스트·HTTP 65개·STOMP 4개 확인으로 갱신했다. 최신 결과는 이 문서와 Gradle HTML/XML을 따른다.

## 남은 구현과 준비물

| 구분 | 남은 작업 | 필요한 준비물·선행 조건 |
|---|---|---|
| A HTTP API | 통신 2개, 설비 조회 5개, 추세 1개, 기준정보 17개, 시스템 설정 4개, 감사 조회 1개 | A 담당 Controller·저장/조회 어댑터와 통합 테스트. B 계산/읽기 기능은 현재 JDBC 포트로 동작하며 이 30개가 B 미구현 API라는 뜻은 아님 |
| 내부 수집 연결 | INT-001 어댑터는 구현돼 있으나 manual-test에서는 꺼져 있음 | backend-b + stf.backend-b.ingestion-enabled=true, INTERNAL_API_KEY 32바이트 이상, Node-RED URL·전송 규격. A 수집 Controller가 생기면 하나만 활성화 |
| 계정 계약 정합성 | 이름 길이의 단일 기준 정리 | v5 SignupRequest/UserUpdateRequest는 최대 100자, 기존 DB account.name과 A 서비스 검증은 50자. A·DB 담당자가 기준을 확정해 계약/검증 또는 신규 마이그레이션을 함께 맞춰야 함 |
| 공유 DB | V3/V4 검토·순서 적용 | DB 담당자 검토, 대상 DB/현재 Flyway 이력, 백업과 비공개 접속 설정. V1→V2→V3→V4 순서, 기존 V1/V2 수정·볼륨 초기화 금지 |
| 프론트 연결 | Back_B의 최신 API·오류 코드 반영 및 브라우저 검증 | Back_B→backend PR 검토/병합 후 frontend api:sync·check. STF_BACKEND_URL, localhost/HTTPS의 Secure 쿠키 구성, DUTY_REPLACEMENT_REQUIRED 안내 및 재지정 화면 흐름 |
| Telegram 실환경 | 연결·알림·초기화의 실제 전송 검증 | TELEGRAM_BOT_USERNAME·TELEGRAM_BOT_TOKEN 비공개 설정, 봇과 /start 연결한 테스트 계정, 알림 수신 직책·당번. 외부 메시지 발송은 별도 지시 후 진행 |
| 현장 데이터 | PLC·Node-RED 실제 수집부터 복구·효과 확인까지 시연 | PLC 주소/레지스터/배율, 확정 설비·항목 코드, 내부 키·서버 주소, 실제 임계값·카운터 초기화 규칙·운영 시간·생산 단가 |
| B 계약 확정 후 확장 | 알람 상세 추가 표시, 다중 발생 비교, 종 알림 과거 목록·읽음 | 아래 미정 계약을 먼저 확정. 현재 v5 동작은 유지 |

## B 확장에 필요한 설계 결정

| 항목 | 지금 동작 | 확정해야 할 내용 |
|---|---|---|
| 알람 상세 | v5의 요약·추세·타임라인·재발 횟수 등 | 정지 예상 시각, 발송 목록/건수, 원인·조치·교체·오경보 상세를 어떤 응답 필드로 제공할지 |
| 알람 비교 | MEAS-002 단일 알람 전후 10분 | 프론트의 여러 발생 전후 30분 비교를 채택할지, 발생 선택/정렬·응답 모양·최대 건수 |
| 종 알림 | 개인 실시간 /user/queue/notifications | 과거 조회 REST, 사용자별 읽음 저장, 보관 기간·페이징·중복 처리 |

## 현재 실행 방법

Swagger: http://localhost:8081/swagger-ui.html. 기존 전용 서버는 backend-b,manual-test 프로필로 실행한다. 일반 IDE/서버에서도 B 당번 삭제 정책을 쓰려면 backend-b를 활성화해야 한다.

PowerShell(기존 stf 폴더):

```powershell
.\scripts\Setup-LocalJwt.ps1
.\scripts\manual-test\Start-ManualTest.ps1
.\scripts\manual-test\Run-Tests.ps1
.\scripts\manual-test\Smoke-Test.ps1
.\scripts\manual-test\Test-WebSocketSession.ps1
```

실제 키·비밀번호는 .env 또는 secrets의 Git 제외 파일에만 둔다. 저장소에는 생성 스크립트와 환경변수 이름만 공유한다.

## 아직 HTTP 구현이 없는 A API 목록

아래는 현재 실행 Swagger와 v5 paths의 method/path를 비교한 결과다. `/internal/v1/measurements`는 별도 서버 주소를 쓰고 수동 프로필에서 의도적으로 제외되므로 이 30개에 넣지 않는다.

| API ID | Method | 경로 | 내용 |
|---|---|---|---|
| COM-001 | GET | `/api/v1/communications` | 설비별 통신 현황 조회 |
| COM-002 | GET | `/api/v1/communications/summary` | 헤더 통신 상태 조회 |
| EQP-001 | GET | `/api/v1/lines` | 조회용 라인 목록 |
| EQP-002 | GET | `/api/v1/lines/{lineId}/processes` | 라인의 공정 목록 조회 |
| EQP-003 | GET | `/api/v1/processes/{processId}/equipment` | 공정별 설비 목록 조회 |
| EQP-004 | GET | `/api/v1/equipment/{equipmentId}` | 설비 상세 조회 |
| EQP-005 | GET | `/api/v1/equipment/{equipmentId}/data-items` | 설비 데이터 항목 목록 조회 |
| MEAS-001 | GET | `/api/v1/equipment/{equipmentId}/measurements` | 데이터 항목 추세 조회 |
| MST-001 | GET | `/api/v1/master/equipment` | 기준정보 설비 조회 |
| MST-002 | POST | `/api/v1/master/equipment` | 설비 등록 |
| MST-003 | PUT | `/api/v1/master/equipment/{equipmentId}` | 설비 수정 |
| MST-004 | DELETE | `/api/v1/master/equipment/{equipmentId}` | 설비 논리 삭제 |
| MST-005 | GET | `/api/v1/master/lines` | 라인과 배치 기준정보 조회 |
| MST-006 | POST | `/api/v1/master/lines` | 라인 등록 |
| MST-007 | PUT | `/api/v1/master/lines/{lineId}` | 라인 이름과 표시순서 수정 |
| MST-008 | DELETE | `/api/v1/master/lines/{lineId}` | 빈 라인 삭제 |
| MST-009 | PATCH | `/api/v1/master/lines/{lineId}/layout` | 라인 설비 배치 일괄 변경 |
| MST-010 | GET | `/api/v1/master/equipment/{equipmentId}/data-items` | 설비 데이터 항목 관리 목록 |
| MST-011 | POST | `/api/v1/master/equipment/{equipmentId}/data-items` | 설비 데이터 항목 등록 |
| MST-012 | PUT | `/api/v1/master/data-items/{dataItemId}` | 데이터 항목 수정 |
| MST-013 | PUT | `/api/v1/master/data-items/{dataItemId}/threshold` | 측정값 또는 상태값 알람 기준 수정 |
| MST-014 | GET | `/api/v1/master/equipment/{equipmentId}/parts` | 설비 장착 부품 기준정보 조회 |
| MST-015 | POST | `/api/v1/master/equipment/{equipmentId}/parts` | 장착 부품 기준정보 등록 |
| MST-016 | PUT | `/api/v1/master/parts/{partId}` | 장착 부품 기준정보 수정 |
| MST-017 | DELETE | `/api/v1/master/parts/{partId}` | 장착 부품 논리 삭제 |
| SYS-001 | GET | `/api/v1/system/settings/judgement` | 판정 설정 조회 |
| SYS-002 | PUT | `/api/v1/system/settings/judgement` | 판정 설정 수정 |
| SYS-003 | GET | `/api/v1/system/settings/calculation` | 계산 기준값 조회 |
| SYS-004 | PUT | `/api/v1/system/settings/calculation` | 계산 기준값 수정 |
| AUD-001 | GET | `/api/v1/system/change-histories` | 설정 변경 이력 조회 |

참고: Confluence 분배표는 v4의 예전 36/46개와 카카오/UUID 표현을 포함한다. 이번 상태 집계는 로컬 v5의 85개 operation(공개 84개+내부 수집 1개), 실제 Controller와 사용자가 확정한 정책을 기준으로 한다.
