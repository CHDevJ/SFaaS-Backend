# 백엔드 v7 전환 검증

현재 B 추가 보완은 [프론트 요청 응답](BACK_REQUESTS_B_RESPONSE.md)을 따른다. 전체539건·MySQL12건 통과. [조립 정지 보완531건](BACKEND_B_COMPLETION_2026-11-18.md)과 아래506건은 이전 검증 이력이다.

2026-11-17, 기존 로컬 stf의 Back_B에서 구현했다. 기준은 approved-project-proposal인 openapi.yaml 7.0.0, API_SPEC.md, Confluence v7(3604594)·분배(1507330)·조립 정지 계산 최종 결정(4259864)이다. 기존 v5 구현 기록은 이번 완료 근거와 구분한다.

## 구현 범위

- B 담당 26개 operation의 Controller·명세 DTO·서비스·JDBC 조회/명령·이벤트를 v7로 전환했다. 아래 전부를 실제 보안 체인과 H2에서 호출하고 응답 필수값·Enum·nullable·범위·시각 형식을 검사했다. TLG 실제 전송기는 테스트 대역이다.
- A 인증·가입 승인·사용자 관리의 ADMIN/USER 전환과 공통 HTTP/STOMP 인증을 반영했다. DB에서 활성·권한·비밀번호 변경 필요·폐기를 재확인한다. 권한 변경·계정 삭제 시 토큰을 폐기한다. 동일 초 재로그인이 잘못 폐기되지 않도록 JWT 발급 시각과 Redis 사용자 컷오프의 정밀도를 보존한다.
- A/B 경계에 EquipmentCatalog·MeasurementQuery·SettingsQuery·CounterSnapshot·AlarmQuery 포트를 두었다. 수집 트랜잭션에서 새 알람 3회 연속 판정, 값 복귀 후 ACTIVE 유지, 조치 완료 즉시 CLOSED, 종료 후 새로운 3회 판정, 동시 완료 409, 롤백을 검증했다.
- 현재 재고는 stock_history 최신값이다. DEMO_PRODUCTION만 끝 설비 증분을 반영하며 초기화·역순·중복 측정으로 재고를 재생성하지 않는다. 외부 운영 재고는 INVENTORY_SYSTEM을 선택하고 실제 데이터 공급을 준비한다.
- 같은 설비·항목의 과거 CLOSED CRITICAL 평균과 표본 수를 사용한다. 0건은 기본 30분으로 대체하지 않는다. 1건부터 사용한다. 경계·소수 시간을 유지하고 원 단위에서 최종 반올림한다.
- 통신은 마지막 수신 후 30초 초과 기준이다. 통신 끊김이면 예측은 INSUFFICIENT_DATA다. 주기 검사와 상태 변경 시 즉시 재계산, 60초 예측 갱신을 연결했다.
- STOMP는 v7의 6개 destination을 사용한다. 개인 큐에 서버 읽음 저장·unreadCount를 추가하지 않았다. 텔레그램은 신규/상향에 연결된 삭제되지 않은 계정 모두를 대상으로 하며 발송 실패·이벤트 재처리와 원 알람 커밋을 분리한다.
- 삭제된 v5 당번·부품 교체·알람 확인·효과 확인·재발/재알림 API는 기본 런타임에서 제공하지 않는다. 명시적인 legacy-v5-enabled=true 회귀 테스트에만 과거 동작을 격리했다. 과거 DB 이력은 보존한다.

## B API 검증 목록

| API ID | Method | 경로 |
|---|---|---|
| OVW-001 | GET | /api/v1/overview/equipment-layout |
| OVW-002 | GET | /api/v1/overview/assembly-stop-forecast |
| INV-001 | GET | /api/v1/inventory |
| INV-002 | GET | /api/v1/inventory/history |
| INV-003 | GET | /api/v1/inventory/lines/{lineId} |
| ALM-001 | GET | /api/v1/alarms |
| ALM-003 | GET | /api/v1/alarms/{alarmId} |
| ALM-006 | POST | /api/v1/alarms/{alarmId}/actions |
| HST-001 | GET | /api/v1/alarm-histories |
| HST-002 | GET | /api/v1/equipment/{equipmentId}/fault-histories |
| STA-001 | GET | /api/v1/statistics/alarm-response |
| STA-003 | GET | /api/v1/statistics/daily-alarms |
| STA-004 | GET | /api/v1/statistics/availability |
| STA-005 | GET | /api/v1/statistics/downtime |
| STA-006 | GET | /api/v1/statistics/equipment-rankings |
| STA-007 | GET | /api/v1/statistics/losses |
| TLG-001 | POST | /api/v1/telegram/link-tokens |
| TLG-002 | GET | /api/v1/telegram/me/connection |
| TLG-003 | DELETE | /api/v1/telegram/me/connection |
| TLG-004 | POST | /api/v1/telegram/me/test-messages |
| TLG-005 | POST | /api/v1/system/telegram/test-messages |
| TLG-006 | GET | /api/v1/system/telegram/deliveries |
| ALM-002 | GET | /api/v1/alarms/recent-24-hours |
| STA-009 | GET | /api/v1/statistics/critical-equipment |
| STA-010 | GET | /api/v1/statistics/stop-causes |
| TLG-007 | POST | /internal/v1/telegram/updates |

## 마이그레이션과 문서 정합성

V1~V4는 변경하지 않았다. V5__forecast_settings_and_initial_stock.sql은 DB 담당 origin/feature/SF5-18-v5-forecast-settings 원본 그대로이며 Git blob b734d05835df46255f78875e78658bdf02401977이 일치한다. 새 변경은 V6__v7_roles_alarm_actions_and_counter_quality.sql이다.

V6는 ADMIN/USER·ACTIVE/CLOSED, 계정 이름 100자와 삭제자, 조치 설비 자유 입력, ACTIVE 설비/항목 유일 인덱스, 누적 카운터 품질·초기화 시각을 반영한다. 기존 중복 ACTIVE 이력이 있으면 자동 삭제하지 않고 마이그레이션이 중단된다. 담당자가 중복을 검토해야 한다. 실제 검증은 전용 MySQL의 임시 스키마에만 수행했으며 수동/공유 DB에는 적용하지 않았다.

표본 0건 평균을 null로 제공하는 명시 규칙에 맞춰 FaultHistoryData·EquipmentRankingItem의 averageRepairMinutes nullable을 YAML·설명 명세·DTO에 동기화했다. 명세에 없는 새 API나 NTF 읽음 저장소는 만들지 않았다.

## 테스트

명령: .\scripts\manual-test\Run-Tests.ps1 → gradlew.bat clean test --no-daemon

최종 전체 80클래스 506건 PASS, 실패 0·오류 0·건너뜀 0. 실제 MySQL은 과거 회귀 6건과 v7 마이그레이션/명령/조회 5건, 합계 11건이다. scripts/ci/report-tests.py도 두 MySQL 클래스의 실행과 건너뜀 0을 강제한다.

주요 검증: 재고300·소비6/분·과거 수리70분 → 소진50분·부족20분·예상손실6,000,000원. 이력 없음·1건/2건 평균·단가0·순소비0·임계값·카운터 초기화·중복/역순·롤백·동시 조치·계정 권한/삭제/폐기·비밀번호 변경 제한·Webhook 독립 비밀값·공개 응답·STOMP 필드를 검사했다.

기존 엑셀의 453건과 HTTP65·STOMP4는 v5 검증 이력으로 보존한다. 누적실행/실행상세에는 이번 실패·수정 후 전체 실행을 추가하고 실행 ID로 재실행을 구분한다. test-run-history.json에는 JUnit 메서드·판정·시간만 저장하며 요청·비밀번호·토큰·실패 스택 원문은 제외한다.

## 환경에서 확인할 사항

- 이번 작업은 기존 수동 서버를 재시작하거나 수동/공유 DB를 V6로 올리지 않았다. V5 이후 적용 순서와 ACTIVE 중복을 검토한 뒤 해당 환경에서 실행한다.
- 프론트엔드 코드는 수정하지 않았다. v7 명세 동기화와 브라우저 실제 연동 검증은 별도다. Secure Refresh 쿠키 검증은 HTTPS 환경이 필요하다.
- 실제 Telegram 봇·Webhook 주소와 독립 TELEGRAM_WEBHOOK_SECRET, PLC/Node-RED 항목·카운터 초기값, 운영 재고 공급, 실제 UNIT_PRICE·HOURLY_OUTPUT은 환경에서 준비한다. 이번 테스트는 실제 외부 메시지를 보내지 않았다.
- A 담당 전체 41개 API를 완료했다고 의미하지 않는다. 이번 A 범위는 인증·계정·공통 보안과 B 연결용 메타정보/수집 경계다. B의 assembly_stop 자동 기록·복구와 실제 손실 연결은 2026-11-18에 보완했다. 실제 A/수집 측 상태·재고 입력이 있어야 운영 데이터가 표시된다.
