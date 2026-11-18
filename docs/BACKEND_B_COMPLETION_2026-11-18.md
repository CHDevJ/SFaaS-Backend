# B 잔여 구현 보완 (2026-11-18)

기준은 OpenAPI v7, Confluence 분배 1507330과 ERD E2 1146913이다. origin/backend·A master-equipment/master-lines/auth 브랜치에서 조립 정지 저장 구현이 없는 것을 확인했다. A의 현재 설비 상태·설정·카탈로그 Query 계약을 재사용했다. 이번 변경은 B alarm/inventory/statistics/telegram 및 B 조립 설정·검증 파일에 한정한다. A auth/user/common/equipment/measurement/master/system/audit 코드는 수정하지 않는다.

## 조립 정지 기록

ERD의 두 조건은 PART 재고0, ASSEMBLY 설비 FAULT다. B가 기존 Query로 관찰하고 assembly_stop을 기록한다. 현재의 V7Events.EquipmentCommitted·AlarmCommitted·InventoryRecalculated를 받아 재확인하며 외부 재고 변경과 서버 재시작은 1초 주기로 재확인한다. A가 생산/상태 이벤트를 제공하면 기존 이벤트 경계로 연결한다. 새 REST API나 A 수집 코드를 만들지 않는다.

backend_job_lock의 ASSEMBLY_STOP 행을 트랜잭션 안에서 잠근다. 동시 실행과 이벤트 재처리에도 새 연속 정지 한 건을 보장한다. 원인은 가장 이른 근거 시각·알람/라인/설비 ID 순으로 안정 선택하고 시작 때 원인과 HOURLY_OUTPUT·UNIT_PRICE를 복사한다. 단가0도 기록한다. 정지 중 다른 원인이 추가되어도 겹친 구간을 새로 만들지 않는다.

재고 부족과 조립 고장이 모두 해소되면 ended_at과 loss_amount를 같은 트랜잭션에서 저장한다. 통신 끊김·없는 재고·없는 조립 기준정보는 복구로 간주하지 않는다. 시작은 B가 확인한 관찰 시각이다. 서버 중단 중 미기록된 구간·당시 설정을 추정해 과거 손실을 만들지 않는다. 통신이 불명확한 동안은 종료를 확정하지 못하므로 정확한 실제 종료는 유효 상태 재수신 후 확인된다.

기존 assembly_stop·backend_job_lock만 사용해 V1~V6 및 DB 담당 V5를 수정하지 않는다. 공용/수동 DB 마이그레이션·초기화·실제 봇 발송은 수행하지 않는다.

## 실제 손실과 경계

시간 구간을 [from,to)와 현재 시각으로 자르고 겹친 구간을 제거한다. 분할된 0.3원+0.3원은 개별0원으로 버리지 않고 최종1원으로 반올림한다. 날짜 경계·설비 순위·일별 집계에도 같은 계산기를 사용한다. 원인 설비가 없는 재고 부족은 실제 설비를 만들지 않고 해당 부족 라인 손실에 포함한다.

B의 ForecastQuery를 추가해 A가 B Repository/Entity 대신 예측 응답 경계를 사용할 수 있게 했다. 기존 B 알람·재고·통계·Telegram API 26개의 주소와 DTO는 유지한다.

비밀번호 초기화 발송 이력은 JSON 이벤트의 시간대가 UTC로 정규화되어도 KST로 저장한다. 한국 날짜로 조회할 때 발송 건이 빠지던 오류를 보완했다. 임시 비밀번호 원문을 저장하지 않는다.

## 검증 및 남은 외부 준비

최종 전체 Gradle clean test는 83클래스 531건 통과, 실패·오류·건너뜀0, 실제 MySQL12건 포함이다. 실행 근거는 단위테스트 보고서와 test-run-history.json에 누적했다. 조립 고장·재고0·동시 원인·불명확한 통신·이벤트 커밋/롤백·동시 기록·단가 변경·실제 MySQL·자정·중첩·반올림·KST 발송 날짜를 검증한다.

기존 A 관리자 동시 강등 테스트는 먼저 강등된 계정의 토큰 폐기로 401이 반환되면 기대값403/422와 어긋날 수 있다. A 소유 테스트/코드는 이번 범위에서 고치지 않으며 발견 결과를 분리해 전달한다. 실제 최종 실행 결과와 이 재현 가능성을 구분한다.

남은 것은 실제 프론트·PLC/Node-RED·Telegram 봇/외부 재고 공급 연결과 대상 DB 적용, A의 별도 미구현 API다. 이번 B 구현은 실제 현장 신호나 프론트 코드를 대신 만들지 않는다.
