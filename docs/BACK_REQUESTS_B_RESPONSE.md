# 프론트 BACK_REQUESTS 응답 (B 담당, 2026-11-18)

기준은 docs/openapi.yaml v7.0.0이다. 원본 요청서는 2026-11-17 상태여서 현재 코드와 다른 항목이 있다. 사용자 승인으로 기존 로컬 stf의 Back_B에서 B만 보완했다. A 소스·테스트와 V1~V6, 프론트 파일은 변경하지 않는다.

| 요청 | 반영 내용 또는 프론트 적용 방법 |
|---|---|
| 0 | 기존 v7은 PR63으로 backend(080e4f5)에 병합됐다. 이번 새 보완의 Git 공유 상태는 커밋/PR 이력으로 별도 확인한다. |
| 3 | 기본 WebSocket Origin에 http://192.168.20.11:5173이 있다. 다른 주소는 STF_WS_ORIGINS로 지정한다. |
| 5 | Telegram 요구사항은 FR1410이다. |
| 6 | 서버 읽음 API를 추가하지 않는다. 헤더 카드·읽지 않은 수는 메모리이며 새로고침 시0이다. |
| 7 | 여섯 메시지를 components.schemas의 명명된 타입으로 생성할 수 있다. x-websocket의 같은 정의를 공유하며 wire 형식은 유지한다. |
| 7-1 | API_SPEC 11장의 비교 키·재연결 초기화 규칙을 따른다. 측정값은 equipmentId+dataItemId까지 나눈다. |
| 7-2 | UserNotificationEvent=eventId, sequence, type, alarmId, occurredAt. 현재 API_SPEC과 일치한다. |
| 9 | ALM-003의 assemblyStopExpectedAt·action을 사용한다. 교체부품·오경보는 삭제 범위라 되살리지 않는다. 발송 이력은 ADMIN이 TLG-006의 alarmId와 발송일 date로 조회한다. USER에게 관리자 수신자 목록을 노출하지 않는다. |
| 18 | ALM-001의 yesterdayOccurredCount와 activeCautionCount/activeCriticalCount를 사용한다. ALM-002 data는 페이지 목록이고 차트는 hourlyCounts를 사용한다. |
| 20 | STA-001의 longestActionEquipmentId/Name과 activeCautionCount/activeCriticalCount를 사용한다. 종료 표본이 없으면 최장 정보는 null이다. |
| 26의 Telegram | TLG-004/005 data.message가 실제 테스트 발송 문구다. 422 미연결과 200 result=FAILURE를 모두 실패로 표시한다. |

## A에게 확인할 항목

- 2: RefreshCookieService의 Secure 고정과 LAN HTTP 시연 환경. B가 인증 쿠키 설정을 수정하지 않는다.
- 8/21: 공통 오류 코드 Enum과 현재 비밀번호 오류 응답.
- 16/17/19: 설비·공정 대표 상태, 이상 측정 메타정보, 측정 메시지·추세 메타정보.
- 22/23/24: 설비 종류·통신 방식·사진 제한·공정/라인 이동·항목 수정의 누락 필드 처리.
- 25/26/27: 계산 기준값 ranges, 계정 목록 승인 상태 조건, 변경 이력 itemType.
- 11/12: main의 폴더 구조와 공통 루트 문서 정리는 별도 저장소 통합 작업이다.

AUTH-002/003/004/006은 현재 AuthController에 구현돼 있다. 이번 작업에서 A 전체 API 완료 여부를 새로 인증하거나 A 코드를 수정하지 않는다. 실제 서버 주소·HTTPS·브라우저·PLC·Telegram 연결 검증은 환경에서 따로 확인한다.

## 검증

관련 회귀45건과 전체 Gradle clean test 83클래스539건을 통과했다. 실제 MySQL12건 포함, 실패·오류·건너뜀0이다. 새 회귀8건을 포함하고 실패 실행·수정 후 결과를 기존 Excel과 JSON에 보존했다. OpenAPI lint 오류0, REST 미참조 실시간 스키마6개 경고는 예상된 결과다. openapi-typescript7.13.0으로 여섯 타입과 새 응답 필드를 실제 생성했다. Confluence v7 본문과 기존 openapi.yaml 첨부2판, Backend 기록과 TS-82(4882434)를 동기화했다. 실제 프론트·PLC·봇 발송은 실행하지 않았다.

추가 검증: 누적 스크립트는 과거 JSON을 재직렬화하지 않고 새 실행만 덧붙인다. 격리된 데이터로 과거 타임스탬프 원문 보존·새 JUnit 실행 추가·중복 실행 무변경을 확인했다. 실제539건의 중복 기록 호출에서도 원본 SHA-256이 유지됐다. 이 스크립트 검증을 Gradle 테스트 건수에 더하지 않는다.
