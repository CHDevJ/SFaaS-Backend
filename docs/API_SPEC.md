# STF 설비통합관제 API 명세서 v6

- 문서 버전: 6.0.0
- 작성 기준일: 2026-11-17 (Asia/Seoul)
- 사용자 REST: `/api/v1`, 내부 수집: `/internal/v1`, 실시간: `/ws`
- OpenAPI: 3.0.3 / 59개 경로 / 69개 operation
- 계약 상태: approved-project-proposal
- 구현 상태: 기존 v5 구현에서 전환 필요. 문서 검증은 v6 API 구현 완료를 뜻하지 않는다.

## 1. 기준 자료와 개정 원칙

2026-11-17 자료 6개를 모두 검토했다. 요구사항정의서 19쪽(기능 75건, NFR 13건, IFR 4건), 기능목록 수정본 25쪽, 기능 변경 내역 10쪽, 화면설계서 75쪽, 공통동작 정의서 6쪽, ZIP의 HTML 71개와 PNG 71개가 기준이다.

우선순위는 최신 요구사항 → 기능목록·변경 내역 → 화면설계서·공통동작 → HTML/PNG다. 명시된 새 동작이 과거 v4/v5 제안과 충돌하면 새 동작을 적용한다. 화면 숫자 예시는 실제 공장값으로 쓰지 않는다. 아래에서 제안값으로 표시한 부분은 이전 사용자 지시에 따라 구현 가능한 계약으로 정했으며 현장 검증 후 변경할 수 있다.

기능 변경 내역의 추가 9·삭제 20·변경 57은 원본 86개 기능과 비교한 수치다. OpenAPI operation 삭제 수와는 집계 단위가 다르다. 같은 문서의 과거 용어표에 남은 '직책'은 본문과 요구사항의 관리자/사용자보다 우선하지 않는다.

## 2. v5에서 달라진 계약

| 구분 | v6 적용 |
|---|---|
| 권한 | ADMIN(관리자), USER(사용자). position·파생 permission 모델 제거 |
| 알람 처리 | ACTIVE → 조치 완료 POST → CLOSED. 값 정상 복귀는 valueStatus만 RECOVERED |
| 제거 기능 | 확인·담당 지정·조치 추천·오경보·효과 확인·재발·재알림·부품 교체·당번·손실 방지 |
| 알람 목록 | 발생 중·종료 통합, 발생 중 우선. 최근 24시간 이력은 양쪽 상태 포함 |
| 알람 상세 | 발생 전후 각 30분, 같은 설비의 지난 알람·조치, 조치 완료 즉시 종료 |
| 조치 입력 | equipmentText와 actionContent 필수. cause 공백이면 alarmMessage 저장 |
| 분석 | 다중 항목 추세, 경보 상위 설비와 정지 요인 STA-009/010 추가 |
| 재고 | 누적 생산 카운터 - 조립 투입 카운터. INT-002와 생산 delta 이벤트는 사용하지 않음 |
| 설정 | 재알림·효과 확인값 제거. 속도 구간·복구 기본값·시간당 생산량을 관리자 입력 |
| 알림 | 메뉴 activeCount와 개인 unreadCount 분리. 텔레그램은 연결한 계정 전체 수신 |

제거 API ID: `ALM-004`, `ALM-005`, `ALM-007`, `ALM-008`, `MEAS-002`, `PRT-001`, `PRT-002`, `PRT-003`, `PRT-004`, `STA-002`, `STA-008`, `MST-014`, `MST-015`, `MST-016`, `MST-017`, `DUT-001`, `DUT-002`, `DUT-003`, `DUT-004`, `DUT-005`, `DUT-006`. 삭제된 번호는 재사용하지 않는다. ALM-002 경로는 `/alarms/recent-closed`에서 `/alarms/recent-24-hours`로 변경한다. v4 Confluence의 INT-002 합의는 이번 누적 카운터 요구사항으로 대체하며 과거 페이지는 이력으로 남긴다.

## 3. 공통 규칙

| 항목 | 계약 |
|---|---|
| 식별자 | 기존 v5/ERD와 동일한 BIGINT, API integer/int64 최소 1. 현장 코드는 별도 문자열 |
| 인증 | Access 30분, Refresh 7일 Rotation. Access는 메모리, Refresh는 stfRefreshToken HttpOnly·Secure·SameSite=Strict 쿠키, Path=/api/v1/auth |
| 토큰 폐기 | 로그아웃·비밀번호 변경·권한 변경·삭제 시 기존 세션 폐기. Refresh 재사용 시 family 폐기 |
| 계정 | PENDING_APPROVAL → 승인 ACTIVE. 초기화 후 PASSWORD_CHANGE_REQUIRED는 비밀번호 변경 관련 API만 허용 |
| 비밀번호 | 8~64자, 영문·숫자·특수문자 각각 1개 이상. loginId 소문자/숫자 4~20자 |
| 시간 | ISO 8601 offset 포함 date-time. 집계 날짜 Asia/Seoul. 기간은 [from,to), from < to |
| 조회 구간 | 기본 오늘. 최근 7일은 오늘 포함 7일. 사용자 날짜 종료일은 다음날 00:00으로 변환 |
| 페이지 | page 0부터, size 기본 20·최대 100, 전체 건수 포함. 표는 고정 정렬, 일반 sort 파라미터 없음 |
| 고정 순서 | 통신: 오늘 끊김 횟수 DESC→끊김 시간 DESC→ID ASC. 알람: ACTIVE 우선→발생 시각 DESC→ID DESC. 일반 목록: 생성시각 DESC→ID DESC. 라인/공정: 표시순서 ASC→ID ASC |
| 숫자 | 수량 개, 속도 개/분, 시간 초 또는 필드명에 명시한 분, 손실 KRW. 손실은 최종 단계에서 원 단위 반올림 |
| 응답 | 성공 {success:true,data,meta?}, 204 본문 없음. 실패 {success:false,error:{code,message,details?,traceId?}} |
| 오류 | 400 형식/기간, 401 인증, 403 권한, 404 없음, 409 중복/상태충돌, 422 업무규칙 |
| 빈 결과 | 200 + 빈 배열 및 totalElements=0. 입력 오류 details에 field/reason 반환 |
| 삭제 | API DELETE, DB deletedAt/deletedBy. 지난 알람·조치·감사 기록 보존. 발생 중 알람 설비와 설비가 있는 라인은 삭제 거부 |
| 사진 | JPG/PNG/WebP, 최대 5MB, multipart. signed URL 10분(제안값) |

일반 표 헤더 정렬은 제공하지 않는다. 통계 순위의 sortBy는 화면에서 고르는 집계 기준이며 표 헤더 정렬과 구분한다. 모든 리스트는 동일 값의 마지막 비교로 ID를 사용해 페이지 결과를 안정화한다.

## 4. 핵심 업무규칙

### 4.1 알람과 상태

- 미종료 동일 equipmentId+dataItemId 한 건만 허용한다. 3회 연속 이탈 발생, 3회 정상 복귀가 초기 제안값이다. 정상 복귀 횟수는 alarmConsecutiveCount와 동일하게 적용한다.
- 측정값 임계값은 ABOVE면 caution < critical, BELOW면 caution > critical. 상태값은 alarmValue 0 또는 1과 등급을 사용한다. 누적값은 알람 임계값을 두지 않는다.
- ACTIVE 알람에 조치를 입력하면 조치·처리자·종료시각·상태를 한 트랜잭션으로 저장한다. 처리자는 토큰에서 정하고 클라이언트가 보내지 않는다. 중복 종료는 409 ALARM_STATE_CONFLICT다.
- CLOSED 알람이 같은 항목에 있어도 새 이탈이면 새 알람이다. 별도 재발 상태나 효과 확인 단계는 없다.
- 설비 상태 우선순위는 COMMUNICATION_LOST → FAULT → CAUTION → IDLE → RUNNING이다. 복귀한 값 자체는 고장으로 판정하지 않고, 미종료 알람은 조치 전까지 화면 목록에 남는다.
- 경과시간=현재-발생, 소요시간=종료-발생. 현재 값은 최신 수집값, 발생 때 값은 occurredValue로 별도 보존한다.

### 4.2 재고·정지·복구·손실

- 검증된 기초값 이후 생산 누적 증가량과 조립 투입 누적 증가량을 사용한다. 초기 카운터의 기준점이 일치함을 확인한 경우 재고=라인 끝 생산 누적-조립 소비 누적이다. 1분마다 및 설비 상태 변경 시 재계산한다.
- 속도=최근 speedWindowMinutes(기본 10분)의 증가량/구간 분. 고장 설비가 있는 라인의 공급속도는 0이다. 순소비속도=소비-공급. 0 이하이면 remainingMinutes와 정지예상은 null이다.
- 소진분=재고/순소비속도. 정지예상=서버현재+소진분. 복수 라인은 가장 빠른 정지가 대표다.
- 복구예상=고장발생+예상 조치시간. 예상 조치시간은 다음 순서로 정한다(사용자 제공 4.2 표 반영).
  1. 같은 설비·같은 항목의 종료 알람 기록이 3건 이상이면 그 기록의 평균 조치시간을 사용한다.
  2. 1번 기록이 3건 미만이면 같은 설비의 종료 알람 전체 평균을 사용한다. 전체 기록은 1건 이상이면 된다.
  3. 같은 설비의 종료 기록이 없으면 defaultRecoveryMinutes를 사용한다. 초기 기본값은 30분이다.
- 평균의 표본은 현재 고장 발생 이전에 종료된 알람이며 조치시간은 closedAt-occurredAt이다. 진행 중 기록과 종료 시각이 잘못된 기록은 제외한다. 동일 항목은 dataItemId로 구분하며 원인 문구로 묶지 않는다.
- 여유분=정지예상-복구예상. 예상손실= max(0,복구예상-정지예상)의 분 / 60 × hourlyProduction × unitPrice. 실제손실은 실제 조립 정지 구간에 동일 단위 환산을 적용한다.
- 재고 주의 30분·위험 10분은 제안값이다. inventoryWarningMinutes는 화면의 남은 시간 기준 5~120분, 위험은 그 값의 1/3로 계산해 기본10분을 유지한다. 화면에 없는 위험값 별도 입력은 제공하지 않는다.
- 적정재고=ceil(소비속도 × 주의분), cycleTimeSeconds=60/소비속도(0이면 null)는 제안 산식이다. 화면의 120개·66초 예시는 기본값이 아니다.
- PLC 초기 기준 불명, 리셋·역순·음수 차이는 0으로 꾸미지 않는다. INSUFFICIENT_DATA/COUNTER_RESET_PENDING/COUNTER_INCONSISTENT와 null을 반환하고 검증된 기준점으로 재개한다. 리셋 전후 카운터는 epoch를 분리한다. 별도 실사 입력 API는 이번 범위에 추가하지 않는다.

### 4.3 알림과 Telegram

- 발생·등급상승만 텔레그램을 연결한 모든 미삭제 계정에 발송한다. 값 정상 복귀·조치 완료·통신 끊김·재고는 텔레그램 발송 대상에서 제외한다(뒤의 두 항목은 제안 결정).
- 발송은 도메인 트랜잭션 완료 후 처리하고 3초 timeout, 1초 후 1회 재시도한다. 실패 사유는 쉬운 메시지와 공급자 코드로 기록한다.
- 가입 완료 응답에 일회용 10분 연결 URL/QR payload를 제공한다. 인증 전 신청자는 이 링크로만 연결한다. 로그인 후에는 본인만 TLG-001~004를 사용한다. update_id와 발송 eventId+recipient를 중복 제거한다.
- ADMIN은 TLG-005로 대상 계정 테스트, TLG-006으로 발송·수신자별 집계를 조회한다. 휴대전화 번호는 수집하지 않는다.
- 알림 읽음은 NTF-002, 알람 종료는 ALM-006이다. GET은 읽음 상태를 바꾸지 않는다. 종료 알람은 화면 알림과 unreadCount에서 제외한다.
- 비밀번호 초기화는 12자리 임시값을 연결된 텔레그램으로 전달하고 30분 후 만료(제안값). 미연결 계정은 422, 계정은 변경하지 않는다. 전달 재시도 실패는 감사 기록과 결과에 남긴다.

### 4.4 분석·정렬·통계

- MEAS-001은 같은 설비 최대10개 항목을 받는다. 24시간 이하 원본, 7일 이하 1분, 그 이상 1시간 간격(제안값). 측정값 평균, 상태값 마지막값, 누적값 마지막값을 사용하고 통신 미수신 구간은 null로 분리한다.
- 알람 상세는 전후 각30분이며 미래 구간에는 값을 만들지 않는다. 임계값·등급별 이탈 구간·알람 마커와 동일 설비 최근 종료20건을 제공한다.
- 평균/최장 조치시간은 CLOSED 표본에서 계산, 표본 없으면 null. 고장 이력은 종료 CRITICAL만 제공한다. MTBF는 인접 발생시각 차의 평균이며 2건 미만이면 null이다.
- 가동률=RUNNING 시간/계획시간 ×100, 최대100. 계획시간 미정 시 조회 구간 길이를 사용하는 것은 제안값이며 영업 달력 확정 시 교체한다.
- 정지 요인은 조회 구간과 겹친 부분만 합산하고 설비별 중복 구간은 통신 끊김 우선 후 가장 이른 고장 원인에 한 번만 배분한다.

## 5. 전체 API 목록

| API ID | 소유자 | Method | URL | 기능 | 권한 | 요구사항 |
|---|---|---|---|---|---|---|
| AUTH-001 | A | POST | /api/v1/auth/login | 로그인 | ANONYMOUS | FR0101 |
| AUTH-002 | A | POST | /api/v1/auth/refresh | Access/Refresh Token 갱신 | ANONYMOUS | FR0102 |
| AUTH-003 | A | POST | /api/v1/auth/logout | 로그아웃과 현재 Access Token 폐기 | ADMIN, USER | FR0104 |
| AUTH-004 | A | GET | /api/v1/auth/me | 로그인 사용자 정보 조회 | ADMIN, USER | FR0105 |
| AUTH-005 | A | POST | /api/v1/auth/signups | 회원가입 신청 | ANONYMOUS | FR0106 |
| AUTH-006 | A | PUT | /api/v1/auth/me/password | 본인 비밀번호 변경 | ADMIN, USER | FR0108 |
| COM-001 | A | GET | /api/v1/communications | 설비별 통신 현황 조회 | ADMIN, USER | FR0203, FR0204 |
| COM-002 | A | GET | /api/v1/communications/summary | 헤더 통신 상태 조회 | ADMIN, USER | FR0205 |
| OVW-001 | B | GET | /api/v1/overview/equipment-layout | 공장 설비 배치도 조회 | ADMIN, USER | FR0701 |
| OVW-002 | B | GET | /api/v1/overview/assembly-stop-forecast | 조립 정지·복구·손실 예측 조회 | ADMIN, USER | FR0903, FR0904, FR0905, FR0906, FR0907 |
| INV-001 | B | GET | /api/v1/inventory | 라인별 현재 재고 조회 | ADMIN, USER | FR0902 |
| INV-002 | B | GET | /api/v1/inventory/history | 재고 추이 조회 | ADMIN, USER | FR0908 |
| INV-003 | B | GET | /api/v1/inventory/lines/{lineId} | 라인별 재고 상세 조회 | ADMIN, USER | FR0909 |
| EQP-001 | A | GET | /api/v1/lines | 조회용 라인 목록 | ADMIN, USER | FR0701, FR0703, FR0801 |
| EQP-002 | A | GET | /api/v1/lines/{lineId}/processes | 라인의 공정 목록 조회 | ADMIN, USER | FR0703 |
| EQP-003 | A | GET | /api/v1/processes/{processId}/equipment | 공정별 설비 목록 조회 | ADMIN, USER | FR0703 |
| EQP-004 | A | GET | /api/v1/equipment/{equipmentId} | 설비 상세 조회 | ADMIN, USER | FR0203, FR0704 |
| EQP-005 | A | GET | /api/v1/equipment/{equipmentId}/data-items | 설비 데이터 항목 목록 조회 | ADMIN, USER | FR0801 |
| MEAS-001 | A | GET | /api/v1/equipment/{equipmentId}/measurements | 데이터 항목 추세 조회 | ADMIN, USER | FR0802, FR0803, FR0804, FR0805 |
| ALM-001 | B | GET | /api/v1/alarms | 발생 중·종료 알람 통합 목록 | ADMIN, USER | FR0502, FR0601, FR0602 |
| ALM-003 | B | GET | /api/v1/alarms/{alarmId} | 알람 상세 조회 | ADMIN, USER | FR0603, FR0611 |
| ALM-006 | B | POST | /api/v1/alarms/{alarmId}/actions | 조치 기록 및 알람 즉시 종료 | ADMIN, USER | FR0606, FR0610, FR0611 |
| HST-001 | B | GET | /api/v1/alarm-histories | 종료 알람 이력 조회 | ADMIN, USER | FR1201 |
| HST-002 | B | GET | /api/v1/equipment/{equipmentId}/fault-histories | 설비 고장 이력 조회 | ADMIN, USER | FR1205 |
| STA-001 | B | GET | /api/v1/statistics/alarm-response | 알람 대응 속도 집계 | ADMIN, USER | FR1202 |
| STA-003 | B | GET | /api/v1/statistics/daily-alarms | 일별 알람 건수 | ADMIN, USER | FR1204 |
| STA-004 | B | GET | /api/v1/statistics/availability | 설비·라인 가동률 집계 | ADMIN, USER | FR1206 |
| STA-005 | B | GET | /api/v1/statistics/downtime | 설비 정지 시간 집계 | ADMIN, USER | FR1207 |
| STA-006 | B | GET | /api/v1/statistics/equipment-rankings | 문제 설비 순위 조회 | ADMIN, USER | FR1208 |
| STA-007 | B | GET | /api/v1/statistics/losses | 실제 손실액 집계 | ADMIN, USER | FR1209 |
| MST-001 | A | GET | /api/v1/master/equipment | 기준정보 설비 조회 | ADMIN, USER | FR1301 |
| MST-002 | A | POST | /api/v1/master/equipment | 설비 등록 | ADMIN, USER | FR1302 |
| MST-003 | A | PUT | /api/v1/master/equipment/{equipmentId} | 설비 수정 | ADMIN, USER | FR1302 |
| MST-004 | A | DELETE | /api/v1/master/equipment/{equipmentId} | 설비 논리 삭제 | ADMIN, USER | FR1303 |
| MST-005 | A | GET | /api/v1/master/lines | 라인과 배치 기준정보 조회 | ADMIN, USER | FR1304 |
| MST-006 | A | POST | /api/v1/master/lines | 라인 등록 | ADMIN, USER | FR1304 |
| MST-007 | A | PUT | /api/v1/master/lines/{lineId} | 라인 이름과 표시순서 수정 | ADMIN, USER | FR1304 |
| MST-008 | A | DELETE | /api/v1/master/lines/{lineId} | 빈 라인 삭제 | ADMIN, USER | FR1304 |
| MST-009 | A | PATCH | /api/v1/master/lines/{lineId}/layout | 라인 설비 배치 일괄 변경 | ADMIN, USER | FR1304 |
| MST-010 | A | GET | /api/v1/master/equipment/{equipmentId}/data-items | 설비 데이터 항목 관리 목록 | ADMIN, USER | FR1305, FR1306 |
| MST-011 | A | POST | /api/v1/master/equipment/{equipmentId}/data-items | 설비 데이터 항목 등록 | ADMIN, USER | FR1305, FR1306 |
| MST-012 | A | PUT | /api/v1/master/data-items/{dataItemId} | 데이터 항목 수정 | ADMIN, USER | FR1305 |
| MST-013 | A | PUT | /api/v1/master/data-items/{dataItemId}/threshold | 측정값 또는 상태값 알람 기준 수정 | ADMIN, USER | FR1306 |
| SYS-001 | A | GET | /api/v1/system/settings/judgement | 판정 설정 조회 | ADMIN | FR1401 |
| SYS-002 | A | PUT | /api/v1/system/settings/judgement | 판정 설정 수정 | ADMIN | FR1401 |
| SYS-003 | A | GET | /api/v1/system/settings/calculation | 계산 기준값 조회 | ADMIN | FR1402 |
| SYS-004 | A | PUT | /api/v1/system/settings/calculation | 계산 기준값 수정 | ADMIN | FR1402 |
| USR-001 | A | GET | /api/v1/system/users | 등록 사용자 목록 조회 | ADMIN | FR1403 |
| USR-002 | A | GET | /api/v1/system/users/{userId} | 사용자 상세 조회 | ADMIN | FR1403 |
| USR-003 | A | PUT | /api/v1/system/users/{userId} | 사용자 이름·권한 수정 | ADMIN | FR1403 |
| USR-004 | A | DELETE | /api/v1/system/users/{userId} | 사용자 계정 논리 삭제 | ADMIN | FR1403 |
| USR-005 | A | POST | /api/v1/system/users/{userId}/password-reset | 관리자 비밀번호 초기화 | ADMIN | FR1403 |
| APR-001 | A | GET | /api/v1/system/signup-requests | 회원가입 승인 대기 목록 조회 | ADMIN | FR0107 |
| APR-002 | A | POST | /api/v1/system/signup-requests/{requestId}/approval | 회원가입 승인 | ADMIN | FR0107 |
| APR-003 | A | POST | /api/v1/system/signup-requests/{requestId}/rejection | 회원가입 거절 | ADMIN | FR0107 |
| TLG-001 | B | POST | /api/v1/telegram/link-tokens | 내 텔레그램 연결 URL 발급 | ADMIN, USER | FR1410 |
| TLG-002 | B | GET | /api/v1/telegram/me/connection | 내 텔레그램 연결 상태 | ADMIN, USER | FR1410 |
| TLG-003 | B | DELETE | /api/v1/telegram/me/connection | 내 텔레그램 연결 해제 | ADMIN, USER | FR1410 |
| TLG-004 | B | POST | /api/v1/telegram/me/test-messages | 내 텔레그램 시험 발송 | ADMIN, USER | FR1410 |
| TLG-005 | B | POST | /api/v1/system/telegram/test-messages | 테스트 메시지 발송 | ADMIN | FR1406 |
| TLG-006 | B | GET | /api/v1/system/telegram/deliveries | 텔레그램 전송 결과 조회 | ADMIN | FR0507 |
| AUD-001 | A | GET | /api/v1/system/change-histories | 설정 변경 이력 조회 | ADMIN | FR1407 |
| INT-001 | A | POST | /internal/v1/measurements | Node-RED 설비 데이터 수집 | NODE_RED | FR0201 |
| ALM-002 | B | GET | /api/v1/alarms/recent-24-hours | 최근 24시간 알람 이력 | ADMIN, USER | FR0612 |
| STA-009 | B | GET | /api/v1/statistics/critical-equipment | 경보 상위 설비 집계 | ADMIN, USER | FR1213 |
| STA-010 | B | GET | /api/v1/statistics/stop-causes | 정지 요인 집계 | ADMIN, USER | FR1213 |
| NTF-001 | B | GET | /api/v1/notifications | 본인 알림과 읽지 않은 수 조회 | ADMIN, USER | FR0501, FR0502 |
| NTF-002 | B | PATCH | /api/v1/notifications/{notificationId}/read | 본인 알림 읽음 처리 | ADMIN, USER | FR0502 |
| TLG-007 | B | POST | /internal/v1/telegram/updates | Telegram 봇 시작 연결 수신 | TELEGRAM_BOT | FR1410 |

## 6. API별 상세 계약

모든 operation은 BR-{API ID}, TC-{API ID}-01(정상), TC-{API ID}-02(오류)로 추적한다. 이 식별자는 구현 완료 표시가 아니라 작성할 테스트 계약이다. 아래 스키마의 필드별 타입·필수·길이·허용값은 12절 및 OpenAPI components와 연결된다.

### AUTH-001 로그인

- 주소: POST /api/v1/auth/login
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0101
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / LoginRequest
- 응답: 200 LoginResponse; 400 ValidationError; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-AUTH-001, TC-AUTH-001-01/02

### AUTH-002 Access/Refresh Token 갱신

- 주소: POST /api/v1/auth/refresh
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0102
- 처리: HttpOnly Secure SameSite=Strict 쿠키의 Refresh Token을 회전하고 이전 토큰을 폐기한다.

- 요청: application/json / RefreshTokenRequest
- 응답: 200 TokenResponse; 401 Unauthorized
- 규칙/테스트: BR-AUTH-002, TC-AUTH-002-01/02

### AUTH-003 로그아웃과 현재 Access Token 폐기

- 주소: POST /api/v1/auth/logout
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0104
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 204 로그아웃 성공; 401 Unauthorized
- 규칙/테스트: BR-AUTH-003, TC-AUTH-003-01/02

### AUTH-004 로그인 사용자 정보 조회

- 주소: GET /api/v1/auth/me
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0105
- 처리: 로그인 사용자와 성공한 최종 로그인 시각을 반환한다.

- 요청: 본문 없음
- 응답: 200 CurrentUserResponse; 401 Unauthorized
- 규칙/테스트: BR-AUTH-004, TC-AUTH-004-01/02

### AUTH-005 회원가입 신청

- 주소: POST /api/v1/auth/signups
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0106
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / SignupRequest
- 응답: 201 SignupResponse; 400 ValidationError; 409 Conflict
- 규칙/테스트: BR-AUTH-005, TC-AUTH-005-01/02

### AUTH-006 본인 비밀번호 변경

- 주소: PUT /api/v1/auth/me/password
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0108
- 처리: 변경 성공 시 현재 세션을 포함한 기존 토큰을 모두 폐기한다.

- 요청: application/json / PasswordChangeRequest
- 응답: 204 비밀번호 변경 및 기존 토큰 폐기 성공; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-AUTH-006, TC-AUTH-006-01/02

### COM-001 설비별 통신 현황 조회

- 주소: GET /api/v1/communications
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0203, FR0204
- 처리: todayDisconnectionCount DESC, todayDisconnectionMinutes DESC, equipmentId ASC 고정 정렬. 연결 0대/전체 0대는 ALL_NORMAL과 0건으로 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |
| query | status | N | CommunicationStatus | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 CommunicationPageResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-COM-001, TC-COM-001-01/02

### COM-002 헤더 통신 상태 조회

- 주소: GET /api/v1/communications/summary
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0205
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 CommunicationSummaryResponse; 401 Unauthorized
- 규칙/테스트: BR-COM-002, TC-COM-002-01/02

### OVW-001 공장 설비 배치도 조회

- 주소: GET /api/v1/overview/equipment-layout
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0701
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 EquipmentLayoutResponse; 401 Unauthorized
- 규칙/테스트: BR-OVW-001, TC-OVW-001-01/02

### OVW-002 조립 정지·복구·손실 예측 조회

- 주소: GET /api/v1/overview/assembly-stop-forecast
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0903, FR0904, FR0905, FR0906, FR0907
- 처리: 1분마다 및 설비 상태 변경 시 재계산. 누적 카운터의 기초값이 검증되지 않았으면 quantity/예측은 null과 calculationStatus를 반환한다. 고장 라인 공급 속도는 0, 소비<=공급이면 소진 예상 없음.

- 요청: 본문 없음
- 응답: 200 AssemblyStopForecastResponse; 401 Unauthorized
- 규칙/테스트: BR-OVW-002, TC-OVW-002-01/02

### INV-001 라인별 현재 재고 조회

- 주소: GET /api/v1/inventory
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0902
- 처리: 1분마다 및 설비 상태 변경 시 재계산. 누적 카운터의 기초값이 검증되지 않았으면 quantity/예측은 null과 calculationStatus를 반환한다. 고장 라인 공급 속도는 0, 소비<=공급이면 소진 예상 없음.

- 요청: 본문 없음
- 응답: 200 InventoryResponse; 401 Unauthorized
- 규칙/테스트: BR-INV-001, TC-INV-001-01/02

### INV-002 재고 추이 조회

- 주소: GET /api/v1/inventory/history
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0908
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineIds | N | array<Identifier> | - |

- 요청: 본문 없음
- 응답: 200 InventoryHistoryResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-INV-002, TC-INV-002-01/02

### INV-003 라인별 재고 상세 조회

- 주소: GET /api/v1/inventory/lines/{lineId}
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0909
- 처리: 1분마다 및 설비 상태 변경 시 재계산. 누적 카운터의 기초값이 검증되지 않았으면 quantity/예측은 null과 calculationStatus를 반환한다. 고장 라인 공급 속도는 0, 소비<=공급이면 소진 예상 없음.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 InventoryDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-INV-003, TC-INV-003-01/02

### EQP-001 조회용 라인 목록

- 주소: GET /api/v1/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0701, FR0703, FR0801
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 LineListResponse; 401 Unauthorized
- 규칙/테스트: BR-EQP-001, TC-EQP-001-01/02

### EQP-002 라인의 공정 목록 조회

- 주소: GET /api/v1/lines/{lineId}/processes
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0703
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 ProcessListResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-002, TC-EQP-002-01/02

### EQP-003 공정별 설비 목록 조회

- 주소: GET /api/v1/processes/{processId}/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0703
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | processId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 ProcessEquipmentResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-003, TC-EQP-003-01/02

### EQP-004 설비 상세 조회

- 주소: GET /api/v1/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0203, FR0704
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 EquipmentDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-004, TC-EQP-004-01/02

### EQP-005 설비 데이터 항목 목록 조회

- 주소: GET /api/v1/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0801
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DataItemListResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-005, TC-EQP-005-01/02

### MEAS-001 데이터 항목 추세 조회

- 주소: GET /api/v1/equipment/{equipmentId}/measurements
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0802, FR0803, FR0804, FR0805
- 처리: 기간 내 다중 항목. MEASUREMENT는 평균, STATE는 마지막 값, CUMULATIVE는 마지막 누적값으로 다운샘플링. 각 항목 최대 2000점, 통신 끊김은 null 구간. 원본 간격과 적용 집계 간격을 반환.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | dataItemIds | Y | array<Identifier> | 최소 1개; 최대 10개 |

- 요청: 본문 없음
- 응답: 200 MultiMeasurementResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MEAS-001, TC-MEAS-001-01/02

### ALM-001 발생 중·종료 알람 통합 목록

- 주소: GET /api/v1/alarms
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0502, FR0601, FR0602
- 처리: 발생 중 우선, 각 상태 안에서 occurredAt DESC, alarmId DESC로 고정 정렬. 기간·등급·설비·상태로 조회. 요약은 동일 조회 조건 기준이며 today 집계는 Asia/Seoul 오늘과 조회 범위의 교집합이다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |
| query | equipmentId | N | Identifier | - |
| query | grade | N | AlarmGrade | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |
| query | processStatus | N | AlarmProcessStatus | - |
| query | from | N | string(date-time) | - |
| query | to | N | string(date-time) | - |

- 요청: 본문 없음
- 응답: 200 AlarmPageResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-ALM-001, TC-ALM-001-01/02

### ALM-003 알람 상세 조회

- 주소: GET /api/v1/alarms/{alarmId}
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0603, FR0611
- 처리: 발생 전후 각 30분의 측정값, 같은 설비의 최근 종료 알람 최대 20건, 조치 기록, 조립 정지 예상 시각. 아직 도래하지 않은 구간은 빈 값으로 유지하며 데이터가 없는 구간을 보간하지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | alarmId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AlarmDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-ALM-003, TC-ALM-003-01/02

### ALM-006 조치 기록 및 알람 즉시 종료

- 주소: POST /api/v1/alarms/{alarmId}/actions
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0606, FR0610, FR0611
- 처리: 설비 글과 조치 상세를 검증하고 원인 기본값을 적용한 후 한 트랜잭션으로 조치·처리자·종료 시각·CLOSED를 저장한다. 이미 종료된 알람은 409. 값 정상 여부와 관계없이 종료한다. 종료 이후 별도의 판정 주기를 충족하면 새 알람을 생성할 수 있다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | alarmId | Y | Identifier | - |

- 요청: application/json / AlarmActionRequest
- 응답: 201 AlarmActionResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound; 409 Conflict
- 규칙/테스트: BR-ALM-006, TC-ALM-006-01/02

### HST-001 종료 알람 이력 조회

- 주소: GET /api/v1/alarm-histories
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1201
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |
| query | equipmentId | N | Identifier | - |
| query | grade | N | AlarmGrade | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 AlarmHistoryPageResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-HST-001, TC-HST-001-01/02

### HST-002 설비 고장 이력 조회

- 주소: GET /api/v1/equipment/{equipmentId}/fault-histories
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1205
- 처리: 종료된 CRITICAL 고장만 제공. 평균 조치시간=closedAt-occurredAt 평균. 평균 고장 간격=연속된 두 고장 발생 시각 차의 평균. 표본 2건 미만이면 평균 고장 간격은 null.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |
| query | from | N | string(date-time) | - |
| query | to | N | string(date-time) | - |

- 요청: 본문 없음
- 응답: 200 FaultHistoryResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-HST-002, TC-HST-002-01/02

### STA-001 알람 대응 속도 집계

- 주소: GET /api/v1/statistics/alarm-response
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1202
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AlarmResponseStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-001, TC-STA-001-01/02

### STA-003 일별 알람 건수

- 주소: GET /api/v1/statistics/daily-alarms
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1204
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DailyAlarmStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-003, TC-STA-003-01/02

### STA-004 설비·라인 가동률 집계

- 주소: GET /api/v1/statistics/availability
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1206
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AvailabilityStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-004, TC-STA-004-01/02

### STA-005 설비 정지 시간 집계

- 주소: GET /api/v1/statistics/downtime
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1207
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DowntimeStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-005, TC-STA-005-01/02

### STA-006 문제 설비 순위 조회

- 주소: GET /api/v1/statistics/equipment-rankings
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1208
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |
| query | sortBy | Y | string | 허용 faultCount, downtimeMinutes, averageRepairMinutes, lossAmount |

- 요청: 본문 없음
- 응답: 200 EquipmentRankingResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-006, TC-STA-006-01/02

### STA-007 실제 손실액 집계

- 주소: GET /api/v1/statistics/losses
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1209
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 LossStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-007, TC-STA-007-01/02

### MST-001 기준정보 설비 조회

- 주소: GET /api/v1/master/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1301
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |
| query | equipmentId | N | Identifier | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 MasterEquipmentPageResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-001, TC-MST-001-01/02

### MST-002 설비 등록

- 주소: POST /api/v1/master/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1302
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: multipart/form-data / EquipmentWriteRequest
- 응답: 201 MasterEquipmentResponse; 400 ValidationError; 401 Unauthorized; 409 Conflict
- 규칙/테스트: BR-MST-002, TC-MST-002-01/02

### MST-003 설비 수정

- 주소: PUT /api/v1/master/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1302
- 처리: equipmentCode는 등록 후 수정할 수 없다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: multipart/form-data / EquipmentUpdateRequest
- 응답: 200 MasterEquipmentResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-003, TC-MST-003-01/02

### MST-004 설비 논리 삭제

- 주소: DELETE /api/v1/master/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1303
- 처리: deletedAt과 deletedBy를 기록하고 일반 조회·판정·알림 대상에서 제외한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 논리 삭제 성공; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-004, TC-MST-004-01/02

### MST-005 라인과 배치 기준정보 조회

- 주소: GET /api/v1/master/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 MasterLineListResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-005, TC-MST-005-01/02

### MST-006 라인 등록

- 주소: POST /api/v1/master/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / LineWriteRequest
- 응답: 201 MasterLineResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-MST-006, TC-MST-006-01/02

### MST-007 라인 이름과 표시순서 수정

- 주소: PUT /api/v1/master/lines/{lineId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: application/json / LineWriteRequest
- 응답: 200 MasterLineResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-007, TC-MST-007-01/02

### MST-008 빈 라인 삭제

- 주소: DELETE /api/v1/master/lines/{lineId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 라인 삭제 성공; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-008, TC-MST-008-01/02

### MST-009 라인 설비 배치 일괄 변경

- 주소: PATCH /api/v1/master/lines/{lineId}/layout
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: application/json / LineLayoutUpdateRequest
- 응답: 200 MasterLineResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-009, TC-MST-009-01/02

### MST-010 설비 데이터 항목 관리 목록

- 주소: GET /api/v1/master/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305, FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 MasterDataItemListResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-010, TC-MST-010-01/02

### MST-011 설비 데이터 항목 등록

- 주소: POST /api/v1/master/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305, FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: application/json / DataItemWriteRequest
- 응답: 201 MasterDataItemResponse; 400 ValidationError; 401 Unauthorized; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-011, TC-MST-011-01/02

### MST-012 데이터 항목 수정

- 주소: PUT /api/v1/master/data-items/{dataItemId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | dataItemId | Y | Identifier | - |

- 요청: application/json / DataItemWriteRequest
- 응답: 200 MasterDataItemResponse; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-012, TC-MST-012-01/02

### MST-013 측정값 또는 상태값 알람 기준 수정

- 주소: PUT /api/v1/master/data-items/{dataItemId}/threshold
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | dataItemId | Y | Identifier | - |

- 요청: application/json / ThresholdWriteRequest
- 응답: 200 ThresholdResponse; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-013, TC-MST-013-01/02

### SYS-001 판정 설정 조회

- 주소: GET /api/v1/system/settings/judgement
- 소유자: A; 권한: ADMIN; 요구사항: FR1401
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 JudgementSettingsResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-SYS-001, TC-SYS-001-01/02

### SYS-002 판정 설정 수정

- 주소: PUT /api/v1/system/settings/judgement
- 소유자: A; 권한: ADMIN; 요구사항: FR1401
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / JudgementSettings
- 응답: 200 JudgementSettingsResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-SYS-002, TC-SYS-002-01/02

### SYS-003 계산 기준값 조회

- 주소: GET /api/v1/system/settings/calculation
- 소유자: A; 권한: ADMIN; 요구사항: FR1402
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 CalculationSettingsResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-SYS-003, TC-SYS-003-01/02

### SYS-004 계산 기준값 수정

- 주소: PUT /api/v1/system/settings/calculation
- 소유자: A; 권한: ADMIN; 요구사항: FR1402
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / CalculationSettingsUpdateRequest
- 응답: 200 CalculationSettingsResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-SYS-004, TC-SYS-004-01/02

### USR-001 등록 사용자 목록 조회

- 주소: GET /api/v1/system/users
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | keyword | N | string | 길이≤100 |
| query | permission | N | Permission | - |
| query | accountStatus | N | AccountStatus | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 UserPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-USR-001, TC-USR-001-01/02

### USR-002 사용자 상세 조회

- 주소: GET /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 UserResponse; 401 Unauthorized; 403 Forbidden; 404 NotFound
- 규칙/테스트: BR-USR-002, TC-USR-002-01/02

### USR-003 사용자 이름·권한 수정

- 주소: PUT /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 이름·권한만 수정. 마지막 ADMIN 계정의 USER 강등은 LAST_ADMIN_REQUIRED 422. 권한 변경 시 기존 토큰을 폐기하고 감사 기록을 남긴다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: application/json / UserUpdateRequest
- 응답: 200 UserResponse; 400 ValidationError; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-USR-003, TC-USR-003-01/02

### USR-004 사용자 계정 논리 삭제

- 주소: DELETE /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 자기 삭제 및 마지막 ADMIN 삭제는 422. 논리 삭제와 토큰 폐기·텔레그램 연결 해제·감사 기록을 한 트랜잭션으로 처리하고 지난 조치 기록은 보존한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 논리 삭제 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-USR-004, TC-USR-004-01/02

### USR-005 관리자 비밀번호 초기화

- 주소: POST /api/v1/system/users/{userId}/password-reset
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 12자리 임시 비밀번호를 생성해 연결된 텔레그램으로 전달(30분 유효, 프로젝트 제안값). 미연결 또는 재시도까지 발송 실패면 422로 응답하고 기존 비밀번호·세션을 유지한다. 전달 성공 시 새 해시·강제변경 상태 저장 및 기존 세션 폐기. 동시 초기화는 계정 잠금으로 직렬화한다. 첫 로그인은 비밀번호 변경 화면만 허용한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 비밀번호 초기화 처리 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 ErrorResponse
- 규칙/테스트: BR-USR-005, TC-USR-005-01/02

### APR-001 회원가입 승인 대기 목록 조회

- 주소: GET /api/v1/system/signup-requests
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 SignupPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-APR-001, TC-APR-001-01/02

### APR-002 회원가입 승인

- 주소: POST /api/v1/system/signup-requests/{requestId}/approval
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 권한을 지정해 ACTIVE로 승인하고 연결된 신청자에게 결과를 발송한다. 발송 장애는 승인 트랜잭션을 되돌리지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | requestId | Y | Identifier | - |

- 요청: application/json / SignupApprovalRequest
- 응답: 200 UserResponse; 401 Unauthorized; 403 Forbidden; 404 NotFound; 409 Conflict
- 규칙/테스트: BR-APR-002, TC-APR-002-01/02

### APR-003 회원가입 거절

- 주소: POST /api/v1/system/signup-requests/{requestId}/rejection
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 신청을 제거하고 연결 토큰을 폐기한다. 신청자에게 보낼 결과는 제거 전 수신 정보를 이벤트로 보존해 발송한다. 반복 승인·거절 충돌은409.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | requestId | Y | Identifier | - |

- 요청: application/json / SignupRejectionRequest
- 응답: 204 거절 처리 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound
- 규칙/테스트: BR-APR-003, TC-APR-003-01/02

### TLG-001 내 텔레그램 연결 URL 발급

- 주소: POST /api/v1/telegram/link-tokens
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 로그인한 본인 계정만 발급. 익명 가입자는 AUTH-005 응답의 10분 링크를 사용한다. 새 토큰 발급 시 이전 미사용 토큰을 폐기.

- 요청: 본문 없음
- 응답: 200 TelegramLinkResponse; 401 Unauthorized
- 규칙/테스트: BR-TLG-001, TC-TLG-001-01/02

### TLG-002 내 텔레그램 연결 상태

- 주소: GET /api/v1/telegram/me/connection
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 TelegramConnectionResponse; 401 Unauthorized
- 규칙/테스트: BR-TLG-002, TC-TLG-002-01/02

### TLG-003 내 텔레그램 연결 해제

- 주소: DELETE /api/v1/telegram/me/connection
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 204 연결 해제; 401 Unauthorized
- 규칙/테스트: BR-TLG-003, TC-TLG-003-01/02

### TLG-004 내 텔레그램 시험 발송

- 주소: POST /api/v1/telegram/me/test-messages
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 TelegramTestMessageResponse; 401 Unauthorized; 422 BusinessRuleViolation
- 규칙/테스트: BR-TLG-004, TC-TLG-004-01/02

### TLG-005 테스트 메시지 발송

- 주소: POST /api/v1/system/telegram/test-messages
- 소유자: B; 권한: ADMIN; 요구사항: FR1406
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

- 요청: application/json / TelegramTestMessageRequest
- 응답: 200 TelegramTestMessageResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-TLG-005, TC-TLG-005-01/02

### TLG-006 텔레그램 전송 결과 조회

- 주소: GET /api/v1/system/telegram/deliveries
- 소유자: B; 권한: ADMIN; 요구사항: FR0507
- 처리: 비밀번호 초기화 발송 성공·실패도 포함한다. 해당 메시지는 임시 비밀번호 발송 결과(비밀정보 제외)라는 고정 문구로 제공하며 비밀번호 원문은 포함하지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | date | N | string(date) | - |
| query | result | N | DeliveryResult | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 TelegramDeliveryPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-TLG-006, TC-TLG-006-01/02

### AUD-001 설정 변경 이력 조회

- 주소: GET /api/v1/system/change-histories
- 소유자: A; 권한: ADMIN; 요구사항: FR1407
- 처리: 공통 인증·검증·업무규칙을 적용하고 해당 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | itemType | N | string | - |
| query | keyword | N | string | 길이≤100 |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 ChangeHistoryPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-AUD-001, TC-AUD-001-01/02

### INT-001 Node-RED 설비 데이터 수집

- 주소: POST /internal/v1/measurements
- 소유자: A; 권한: NODE_RED; 요구사항: FR0201
- 처리: 등록 설비/항목의 묶음 값 수집. 3초 주기, messageId 24시간 중복 제거. STATE 0/1, CUMULATIVE 비음수 정수. 한 항목이라도 잘못되면 전체 거절. prod_count와 input_count 누적값을 재고 입력으로 사용. INT-002 생산 이벤트는 제공하지 않는다.

- 요청: application/json / MeasurementIngestionRequest
- 응답: 202 IngestionAcceptedResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-INT-001, TC-INT-001-01/02

### ALM-002 최근 24시간 알람 이력

- 주소: GET /api/v1/alarms/recent-24-hours
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0612
- 처리: 서버 현재 시각 직전 24시간 안에 발생한 알람. ACTIVE/CLOSED를 모두 포함하고 occurredAt DESC, alarmId DESC로 정렬.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 RecentAlarmListResponse; 401 Unauthorized
- 규칙/테스트: BR-ALM-002, TC-ALM-002-01/02

### STA-009 경보 상위 설비 집계

- 주소: GET /api/v1/statistics/critical-equipment
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1213
- 처리: 경보 건수 DESC, equipmentId ASC. 선택 기간과 겹치는 경보 발생 건수(등급 상향 포함).

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 CriticalEquipmentResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-STA-009, TC-STA-009-01/02

### STA-010 정지 요인 집계

- 주소: GET /api/v1/statistics/stop-causes
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1213
- 처리: 조회 구간과 겹치는 정지시간을 분으로 집계. 통신 끊김을 별도 요인으로 포함. 겹친 정지 구간은 설비별 통신 끊김 우선 후 가장 이른 고장 원인 한 건에만 배분해 중복 합산하지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 StopCauseResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-STA-010, TC-STA-010-01/02

### NTF-001 본인 알림과 읽지 않은 수 조회

- 주소: GET /api/v1/notifications
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0501, FR0502
- 처리: 본인 알림만 최신 순으로 반환. 종료된 알람은 목록·unreadCount에서 제외. GET은 읽음 상태를 변경하지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 NotificationPageResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-NTF-001, TC-NTF-001-01/02

### NTF-002 본인 알림 읽음 처리

- 주소: PATCH /api/v1/notifications/{notificationId}/read
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0502
- 처리: 본인 알림만 멱등 읽음 처리. 다른 사용자 알림은 404. 읽음은 ACTIVE 알람 종료나 접기와 별개다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | notificationId | Y | Identifier | - |

- 요청: application/json / NotificationReadRequest
- 응답: 200 NotificationResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-NTF-002, TC-NTF-002-01/02

### TLG-007 Telegram 봇 시작 연결 수신

- 주소: POST /internal/v1/telegram/updates
- 소유자: B; 권한: TELEGRAM_BOT; 요구사항: FR1410
- 처리: 등록한 봇 Webhook만 접근. /start <일회용 토큰>을 검증해 계정 연결. update_id 24시간 중복 제거. 토큰 10분·일회성·해시 보관. 사용자에게 chat_id 입력을 받지 않는다. 지원하지 않는 update는 200 processed=false. 만료·사용된 토큰은 연결하지 않고 processed=false로 응답한다.

- 요청: application/json / TelegramUpdate
- 응답: 200 TelegramUpdateResult; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-TLG-007, TC-TLG-007-01/02

## 7. 오류코드

| 코드 | HTTP | 발생조건 |
|---|---|---|
| VALIDATION_ERROR | 400 | 필수·형식·범위 입력 오류 |
| INVALID_PERIOD | 400 | from≥to 또는 기간 입력 오류 |
| AUTH_INVALID_CREDENTIALS | 401 | 아이디/비밀번호 불일치 |
| AUTH_TOKEN_INVALID | 401 | 만료·폐기 토큰 |
| AUTH_REFRESH_TOKEN_INVALID | 401 | 갱신 토큰 오류/재사용 |
| AUTH_PERMISSION_DENIED | 403 | ADMIN 필요 또는 비밀번호 변경 전 업무 접근 |
| AUTH_APPROVAL_PENDING | 403 | 미승인 로그인 |
| USER_LOGIN_ID_DUPLICATE | 409 | 로그인 ID 중복 |
| SIGNUP_REQUEST_STATE_CONFLICT | 409 | 이미 승인/거절 |
| USER_NOT_FOUND | 404 | 계정 없음 |
| USER_SELF_DELETE_FORBIDDEN | 422 | 자기 삭제 |
| LAST_ADMIN_REQUIRED | 422 | 마지막 관리자 삭제/강등 |
| EQUIPMENT_CODE_DUPLICATE | 409 | 설비 코드 중복 |
| EQUIPMENT_NOT_FOUND | 404 | 설비 없음 |
| EQUIPMENT_HAS_ACTIVE_ALARM | 422 | 발생 중 알람 설비 삭제 |
| LINE_HAS_EQUIPMENT | 422 | 설비가 있는 라인 삭제 |
| DATA_ITEM_NOT_FOUND | 404 | 수집/조회 항목 미등록 |
| INVALID_THRESHOLD_ORDER | 422 | 주의/경보 방향 순서 오류 |
| ALARM_NOT_FOUND | 404 | 알람 없음 |
| ALARM_STATE_CONFLICT | 409 | 이미 종료된 알람에 조치 |
| ACTION_CONTENT_REQUIRED | 400 | 조치 상세 공백 |
| SETTING_OUT_OF_RANGE | 422 | 설정값 범위·관계 오류 |
| TELEGRAM_NOT_CONNECTED | 422 | 미연결 테스트/초기화 |
| TELEGRAM_DELIVERY_FAILED | 422 | 재시도 발송 실패 |

404는 다른 사용자 소유 개인 알림에도 사용한다. error.details는 필드별 검증을, traceId는 서버 로그 연계를 위한 식별자를 제공한다. 비밀번호·토큰·연결 원문 비밀값은 로그·감사에서 제외한다.

## 8. WebSocket STOMP

/ws, CONNECT Authorization Bearer, 송수신 heartbeat10초. 재연결 1,2,4,8,16,30초 백오프 후 현재 화면 REST 재조회. eventId 중복 제거, 집계 키별 sequence 이전/동일값 무시. DB 커밋 후 발행하며 장기 이력 전달 수단으로 사용하지 않는다.

| 토픽 | 발행 | 핵심 |
|---|---|---|
| /topic/equipment-status | 변경 시 | eventId, sequence, equipmentId, status, statusStartedAt, occurredAt |
| /topic/measurements/{equipmentId} | 유효 수집 3초 | eventId, sequence, equipmentId, dataItemId, valueType, value, measuredAt |
| /topic/alarms | 변경 시 | eventId, sequence, alarmId, eventType, grade, processStatus, occurredAt |
| /topic/alarm-counts | 변경 시 | eventId, sequence, activeCount |
| /topic/inventory | 60초 및 상태변경 | eventId, sequence, lineId, quantity, stage, remainingMinutes, occurredAt |
| /user/queue/notifications | 변경 시 | notificationId, type, alarmId, unreadCount, createdAt |

ACTIVE→CLOSED 시 알람/발생 중 수/개인 알림 수를 함께 갱신한다. 개인 큐는 인증된 본인만, 공용 topic은 로그인 계정만 구독한다. 헤더 종의 unreadCount와 메뉴의 activeCount를 섞지 않는다.

## 9. MQTT와 내부 수집

Spring Boot는 브로커를 직접 구독하지 않는다. PLC/MQTT → Node-RED → POST /internal/v1/measurements다. 값 묶음 주기3초, QoS1, retained=false, 토픽 stf/equipment/{equipmentCode}/values, UTF-8 JSON. messageId는 기존 v5 문자열 형식을 유지하고 재전송만 같은 ID, 24시간 중복 제거한다.

prod_count와 조립 input_count는 CUMULATIVE 비음수 정수로 등록한다(코드명은 제안값, 현장 매핑으로 교체 가능). 실제 값은 등록된 항목만 받는다. STATE는0/1, MEASUREMENT는 유한 숫자. 1~500개 중 하나라도 오류면 전체 거절. 재고는 누적 증가량을 기준으로 계산하며 기존 생산 delta INT-002는 제거된 상태를 유지한다. out-of-order 측정은 이력에 보존하되 최신값·카운터 기준을 역행시키지 않는다.

통신은 수신 기준으로30초 초과 시 끊김(설정10~300초), 새 수신 시 해제하고 시작·끝을 기록한다. 내부 API Key는90일 회전·직전 키24시간 중첩(제안값), TLS·내부망 사용. 실제 키는 환경변수에 둔다.

## 10. 요구사항 추적표

| FR | 요구사항 | API / 처리 |
|---|---|---|
| FR0101 | 로그인 등록된 계정으로 사용자를 확인한다 | AUTH-001 |
| FR0102 | 로그인 유지 로그인 상태를 계속 유지한다 | AUTH-002, AUTH-002, 인증 필터 |
| FR0103 | 권한별 기능 제한 권한에 따라 쓸 수 있는 화면과 기능을 | Spring Security ADMIN/USER |
| FR0104 | 로그아웃 로그인 상태를 끝낸다 | AUTH-003 |
| FR0105 | 로그인 사용자 표시 지금 로그인한 사람을 보여 준다 | AUTH-004 |
| FR0106 | 회원가입 사용자가 직접 계정을 신청한다 | AUTH-005 |
| FR0107 | 가입 승인 관리자가 신청한 계정에 권한을 정해 승 | APR-001, APR-002, APR-003 |
| FR0108 | 비밀번호 변경 사용자가 자기 비밀번호를 바꾼다 | AUTH-006 |
| FR0201 | 측정값 수집 설비가 보낸 측정값과 상태값과 누적값 | INT-001 |
| FR0202 | 통신 끊김 감지 값이 오지 않는 설비를 찾는다 없음 없음 | A 통신 스케줄러 |
| FR0203 | 마지막 수집 시각 제공 데이터가 최신인지 알 수 있게 한다 | COM-001, EQP-004 |
| FR0204 | 통신 현황 조회 설비별 통신 상태를 조회한다 | COM-001 |
| FR0205 | 헤더 통신 상태 제공 전체 설비의 통신 상태를 한눈에 알 수 | COM-002 |
| FR0301 | 설비 상태 판정 설비의 현재 상태를 정한다 없음 없음 | A 설비 상태 판정 |
| FR0302 | 설비 상태 실시간 전달 상태 변화를 화면에 바로 알린다 | A STOMP equipment-status |
| FR0401 | 임계값 판정 측정값이 기준을 벗어났는지 판정한다 없음 없음 | B 연속 이탈 판정 |
| FR0402 | 등급 상향 주의 알람이 더 나빠지면 경보로 올린다 없음 없음 | B 등급 상향 |
| FR0403 | 중복 알람 방지 같은 원인의 알람이 여러 건 생기지 않게 | B 동일 설비·항목 미종료 유일성 |
| FR0404 | 값 정상 복귀 판정 측정값이 기준 안으로 돌아왔는지 판정 | B 값 정상 복귀(종료 안 함) |
| FR0405 | 상태값 판정 상태값이 알람 조건에 해당하는지 판정 | B STATE 0/1 판정 |
| FR0501 | 화면 알림 접속 중인 사용자에게 새 알람을 바로 알 | NTF-001 |
| FR0502 | 발생 중 알람 수 제공 발생 중인 알람이 몇 건인지 항상 보여 | ALM-001, NTF-001, NTF-002 |
| FR0503 | 텔레그램 알림 텔레그램으로 알람을 보낸다 없음 없음 | B Telegram 비동기 발송 |
| FR0506 | 발송 실패 처리 텔레그램 발송이 실패해도 알람 처리가 | B 1회 재시도·실패 사유 기록 |
| FR0507 | 발송 결과 조회 텔레그램 발송 기록을 조회한다 없음 없음 | TLG-006 |
| FR0601 | 알람 목록 조회 발생 중인 알람과 지난 알람을 한 목록으 | ALM-001 |
| FR0602 | 경과 시간 계산 알람이 얼마나 오래됐는지 보여 준다 | ALM-001 |
| FR0603 | 알람 상세 조회 알람 한 건의 요약과 값 추이와 지난 알 | ALM-003 |
| FR0606 | 조치 기록 조치를 적고 조치 완료를 누르면 알람을 | ALM-006 |
| FR0610 | 알람 종료 처리가 끝난 알람을 닫는다 없음 없음 | ALM-006 |
| FR0611 | 처리 기록 누가 언제 조치했는지 남긴다 | ALM-003, ALM-006 |
| FR0612 | 최근 24시간 알람 이력 조 | ALM-002 |
| FR0701 | 배치도 조회 설비가 어디에 있고 어떤 상태인지 한눈 | OVW-001, EQP-001 |
| FR0703 | 공정별 설비 목록 조회 라인을 골라 공정 순서대로 설비를 본다 | EQP-001, EQP-002, EQP-003 |
| FR0704 | 설비 상세 조회 설비 한 대의 정보를 모두 본다 | EQP-004 |
| FR0801 | 설비와 항목 선택 볼 설비와 데이터 항목을 고른다 | EQP-001, EQP-005 |
| FR0802 | 기간 선택 볼 기간을 고른다 | MEAS-001 |
| FR0803 | 추세 차트 조회 선택한 기간의 측정값을 선 차트로 본다 | MEAS-001 |
| FR0804 | 차트 값 확인 차트의 한 지점 값을 정확히 본다 없음 | MEAS-001, Front 차트 툴팁 |
| FR0805 | 통신 끊김 구간 제공 값을 받지 못한 구간을 차트에서 구분한 | MEAS-001 |
| FR0901 | 재고 계산 라인별 부품 재고를 계산한다 없음 없음 | B 누적 카운터 재고 계산 |
| FR0902 | 재고 현황 조회 라인별 재고와 줄어드는 속도를 본다 없음 없음 | INV-001 |
| FR0903 | 재고 소진 시간 예측 재고가 바닥나기까지 남은 시간을 계산 | OVW-002, B 재고 소진 예측 |
| FR0904 | 조립 정지 시각 예측 조립이 멈출 것으로 예상되는 시각을 계 | OVW-002, B 조립 정지 예측 |
| FR0905 | 복구 시각 예측 고장 난 설비가 언제 복구될지 예상한다 없음 없음 | OVW-002, B 과거 조치 평균 복구 예측 |
| FR0906 | 여유 시간 판정 조립이 멈추기 전에 복구되는지 판정한 | OVW-002, B 여유 시간 계산 |
| FR0907 | 예상 손실액 계산 지금 고장으로 생길 손실을 금액으로 계 | OVW-002, B 예상 손실액 |
| FR0908 | 재고 추이 차트 조회 재고가 어떻게 변해 왔는지 선 차트로 본 | INV-002 |
| FR0909 | 재고 상세 조회 라인 재고가 늘고 주는 속도를 본다 | INV-003 |
| FR1201 | 알람 이력 조회 종료된 알람을 기간별로 조회한다 | HST-001 |
| FR1202 | 대응 속도 집계 알람을 얼마나 빨리 조치했는지 본다 | STA-001 |
| FR1204 | 일별 알람 건수 차트 조회 날짜별 알람 건수를 막대 차트로 본다 없음 | STA-003 |
| FR1205 | 설비 고장 이력 조회 설비 한 대의 지난 고장을 본다 없음 없음 | HST-002 |
| FR1206 | 가동률 집계 설비와 라인이 계획한 시간 중 얼마나 가 | STA-004 |
| FR1207 | 정지 시간 집계 설비가 왜 멈춰 있었는지 나눠 본다 | STA-005 |
| FR1208 | 설비 순위 조회 어느 설비가 가장 문제인지 본다 | STA-006 |
| FR1209 | 손실액 집계 지난 고장으로 실제 생긴 손실을 본다 | STA-007 |
| FR1213 | 경보 상위 설비와 정지 요 | STA-009, STA-010 |
| FR1301 | 설비 조회 등록된 설비를 찾는다 없음 없음 | MST-001 |
| FR1302 | 설비 등록과 수정 설비를 새로 넣거나 정보를 바꾼다 | MST-002, MST-003 |
| FR1303 | 설비 삭제 쓰지 않는 설비를 지운다 | MST-004 |
| FR1304 | 라인과 배치 관리 라인 구성과 설비 배치를 바꾼다 없음 없음 | MST-005, MST-006, MST-007, MST-008, MST-009 |
| FR1305 | 데이터 항목 관리 설비마다 어떤 값을 받을지 정한다 | MST-010, MST-011, MST-012 |
| FR1306 | 임계값 관리 항목마다 주의와 경보의 기준을 정한다 없음 없음 | MST-010, MST-011, MST-013 |
| FR1401 | 판정 설정 판정에 쓰는 시간과 횟수와 개수를 바꾼 | SYS-001, SYS-002 |
| FR1402 | 계산 기준값 관리 손실액 계산에 쓰는 값을 넣는다 없음 없음 | SYS-003, SYS-004 |
| FR1403 | 계정 관리 승인된 계정의 권한과 텔레그램 연결 여 | USR-001, USR-002, USR-003, USR-004, USR-005 |
| FR1406 | 테스트 메시지 발송 텔레그램 연결이 되는지 확인한다 | TLG-005 |
| FR1407 | 설정 변경 이력 누가 무엇을 바꿨는지 남기고 조회한다 없음 없음 | AUD-001 |
| FR1410 | 텔레그램 연결 사용자가 자기 계정에 텔레그램을 연결 | TLG-001, TLG-002, TLG-003, TLG-004, TLG-007 |
| FR1501 | 실시간 갱신 새로 고침 없이 바뀐 값을 화면에 반영한 | STOMP·재연결 REST 재조회 |
| FR1502 | 조회 조건 처리 이력과 통계의 조회 방식을 통일한다 | 고정 정렬·페이지·필터 |
| FR1503 | 오류와 빈 결과 안내 조회나 저장이 되지 않았을 때 알려 준다 | 공통 성공/오류 응답 |
| FR1504 | 화면 간 이동 관련된 정보로 바로 넘어간다 없음 | Front 관련 화면 이동 |
| FR1505 | 상태 설명 도움말 범례와 상태 설명을 필요할 때만 본다 없음 범례와 상태 설명은 화면에 늘어놓지 않음 | Front 상태 도움말 |

75개 FR 전체를 API·서버 처리·Front 동작에 연결했다. NFR01~02=비밀번호·토큰·권한, NFR03~04=STOMP·재조회, NFR05=발송 장애 분리, NFR06=조치·감사, NFR07~09=기준정보·보존·검증, NFR10~13=오류·표시·화면 크기·ECharts(Front)다. IFR01=INT-001, IFR02=AUTH-005/TLG-001~004/007, IFR03=TLG-005/006·발송, IFR04=STOMP다.

## 11. 구현 전환과 검증 항목

현재 Back_B 브랜치의 구현·DB는 v5 기준이다. 이번 작업은 명세 개정이며 다음 전환이 별도 구현되어야 한다.

- 계정 직책3종을 권한2종으로 마이그레이션, 승인 대기 저장 및 토큰 권한 변경. 과거 SYSTEM_ADMIN→ADMIN, LINE_MANAGER/MAINTENANCE→USER.
- UNACKNOWLEDGED/ACKNOWLEDGED/ACTION 계열 미종료는 ACTIVE로 매핑하고 종료는 CLOSED 유지. 기존 조치·사용자·종료시각 보존. 효과·당번·부품 기록은 이력 보존 후 새 API 노출 중단.
- ALM-006 즉시 종료와 optimistic lock/조건부 update, 새 목록·최근24시간·다중 추세·새 통계·개인 알림 읽음 구현.
- 재고 입력을 누적 카운터로 연결하고 리셋/기초값 검증. 초기 실사값을 넣는 별도 화면은 기획 확인 전 추가하지 않음.
- Telegram 가입 승인 결과 및 Webhook 연결 구현 확인. 미연결/만료/중복 update와 실패 격리 검증.
- 각 operation 정상/오류, 권한403, 미승인403, 만료401, 입력400, 충돌409, 업무422, 빈 결과200을 계약 테스트로 확인.
- 동시 조치2회 중1회만 종료, 정상 복귀가 종료를 만들지 않음, 발생 중 수와 unread 분리, 정지 요인 중복합산 방지, 금액의 분/시간 환산을 검증.

현장 교체가 필요한 값은 실제 PLC 항목·초기 카운터 기준·적정재고/사이클 타임 산식·위험 기준·복구 기본값이다. 본 문서의 제안값으로 개발하되 데이터 미확정은 null/status로 명시한다.

## 12. 필드 사전

아래는 OpenAPI components에서 생성한 필드 정의다. Y=필수, N=선택. Nullable은 명시된 경우만 허용한다. allOf는 참조와 추가 필드를 모두 적용한다. 참조형의 필드·허용값은 해당 스키마를 따른다.

### Identifier

최종 ERD의 BIGINT 자동 증가 식별자


### Permission

ADMIN=관리자, USER=사용자. 직접 저장·권한 검사하며 직책에서 파생하지 않는다.

허용값: `ADMIN`, `USER`

### AccountStatus

허용값: `PENDING_APPROVAL`, `ACTIVE`, `PASSWORD_CHANGE_REQUIRED`

### EquipmentStatus

허용값: `RUNNING`, `IDLE`, `CAUTION`, `FAULT`, `COMMUNICATION_LOST`

### DataValueType

허용값: `MEASUREMENT`, `STATE`, `CUMULATIVE`

### AlarmGrade

허용값: `CAUTION`, `CRITICAL`

### AlarmValueStatus

허용값: `ABNORMAL`, `RECOVERED`

### AlarmProcessStatus

발생 중 / 조치 완료 종료. 값 정상 복귀는 processStatus를 바꾸지 않는다.

허용값: `ACTIVE`, `CLOSED`

### CommunicationStatus

허용값: `CONNECTED`, `DISCONNECTED`

### HeaderCommunicationStatus

허용값: `ALL_NORMAL`, `PARTIAL_DISCONNECTED`, `ALL_DISCONNECTED`

### ThresholdDirection

기준 이상 또는 기준 이하에서 알람을 발생시키는 제안 코드

허용값: `ABOVE`, `BELOW`

### DeliveryResult

허용값: `SUCCESS`, `FAILURE`

### PageMeta

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| page | integer | Y | N | 값≥0 | - |
| size | integer | Y | N | 값≥1 | - |
| totalElements | integer(int64) | Y | N | 값≥0 | - |
| totalPages | integer | Y | N | 값≥0 | - |

### ErrorDetail

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| code | string | Y | N | - | - |
| message | string | Y | N | - | - |
| details | array<object> | N | Y | - | - |
| traceId | string | N | Y | - | - |

### ErrorResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 false | - |
| error | ErrorDetail | Y | N | - | - |

### LoginRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| loginId | string | Y | N | 길이≥4; 길이≤20; 패턴 `^[a-z0-9]{4,20}$` | 영문 소문자와 숫자만 허용하는 로그인 아이디 |
| password | string(password) | Y | N | 길이≥8; 길이≤64; 패턴 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$`; 비밀 입력 | 영문·숫자·특수문자를 각각 1개 이상 포함 |

### CurrentUser

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| userId | Identifier | Y | N | - | - |
| loginId | string | Y | N | 길이≥4; 길이≤20 | - |
| name | string | Y | N | 길이≤100 | - |
| telegramConnected | boolean | N | N | - | - |
| permission | Permission | Y | N | - | - |
| accountStatus | AccountStatus | Y | N | - | - |
| lastLoginAt | string(date-time) | Y | Y | - | - |

### LoginData

Refresh Token은 Set-Cookie(stfRefreshToken, HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth, Max-Age=604800)로만 전달. accountStatus=PASSWORD_CHANGE_REQUIRED면 auth/me,password,logout 외 업무 API는 403.

상속: CurrentUser

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| accessToken | string | Y | N | 읽기 전용 | - |
| tokenType | string | Y | N | 허용 Bearer | - |
| expiresAt | string(date-time) | Y | N | - | - |

### RefreshTokenRequest

브라우저가 아닌 클라이언트용 대체 입력. 브라우저는 HttpOnly 쿠키를 사용한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| refreshToken | string | N | N | 비밀 입력 | - |

### TokenData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| accessToken | string | Y | N | 읽기 전용 | - |
| tokenType | string | Y | N | 허용 Bearer | - |
| expiresAt | string(date-time) | Y | N | - | - |

### TokenResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | TokenData | Y | N | - | - |

### SignupRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| loginId | string | Y | N | 길이≥4; 길이≤20; 패턴 `^[a-z0-9]{4,20}$` | - |
| password | string(password) | Y | N | 길이≥8; 길이≤64; 패턴 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$`; 비밀 입력 | - |
| name | string | Y | N | 길이≥1; 길이≤100 | - |

### Signup

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| requestId | Identifier | Y | N | - | - |
| loginId | string | Y | N | - | - |
| name | string | Y | N | - | - |
| telegramLink | TelegramLink | Y | N | - | - |
| accountStatus | AccountStatus | Y | N | - | - |
| requestedAt | string(date-time) | Y | N | - | - |

### SignupResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | Signup | Y | N | - | - |

### PasswordChangeRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| currentPassword | string(password) | Y | N | 길이≥8; 길이≤64; 비밀 입력 | - |
| newPassword | string(password) | Y | N | 길이≥8; 길이≤64; 패턴 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$`; 비밀 입력 | - |

### LoginResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | LoginData | Y | N | - | - |

### CurrentUserResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | CurrentUser | Y | N | - | - |

### CommunicationSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| status | HeaderCommunicationStatus | Y | N | - | - |
| totalEquipmentCount | integer | Y | N | 값≥0 | - |
| disconnectedEquipmentCount | integer | Y | N | 값≥0 | - |
| allDisconnectedSince | string(date-time) | N | Y | - | - |

### CommunicationSummaryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | CommunicationSummary | Y | N | - | - |

### CommunicationEquipment

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentCode | string | Y | N | 길이≤100 | - |
| equipmentName | string | Y | N | 길이≤200 | - |
| lineName | string | Y | N | 길이≤100 | - |
| communicationMethod | string | Y | N | 길이≤100 | - |
| status | CommunicationStatus | Y | N | - | - |
| lastReceivedAt | string(date-time) | Y | N | - | - |
| disconnectedSince | string(date-time) | N | Y | - | - |
| todayDisconnectionCount | integer | Y | N | 값≥0 | - |
| todayDisconnectionMinutes | integer | Y | N | 값≥0 | 분 |

### CommunicationPageData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| summary | CommunicationSummary | Y | N | - | - |
| items | array<CommunicationEquipment> | Y | N | - | - |

### CommunicationPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | CommunicationPageData | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### LineSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | 길이≤100 | - |
| displayOrder | integer | Y | N | 값≥1 | - |

### LineListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<LineSummary> | Y | N | - | - |

### ProcessSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| processId | Identifier | Y | N | - | - |
| processName | string | Y | N | 길이≤100 | - |
| processOrder | integer | Y | N | 값≥1 | - |
| hasAbnormalEquipment | boolean | Y | N | - | - |

### ProcessListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<ProcessSummary> | Y | N | - | - |

### EquipmentStatusCount

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| status | EquipmentStatus | Y | N | - | - |
| count | integer | Y | N | 값≥0 | - |

### CurrentDataItem

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemId | Identifier | Y | N | - | - |
| dataItemName | string | Y | N | 길이≤100 | - |
| valueType | DataValueType | Y | N | - | - |
| value | number / string | Y | N | - | - |
| displayValue | string | N | Y | - | - |
| unit | string | N | Y | 길이≤30 | - |
| valueStatus | string | N | Y | 허용 NORMAL, CAUTION, CRITICAL | - |
| cautionThreshold | number | N | Y | - | - |
| criticalThreshold | number | N | Y | - | - |
| measuredAt | string(date-time) | Y | N | - | - |

### AbnormalMeasurement

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemId | Identifier | Y | N | - | - |
| dataItemName | string | Y | N | - | - |
| currentValue | number | Y | N | - | - |
| unit | string | N | Y | - | - |
| valueStatus | string | Y | N | 허용 CAUTION, CRITICAL | - |
| threshold | number | N | N | - | - |

### EquipmentSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentCode | string | Y | N | 길이≤100 | - |
| equipmentName | string | Y | N | 길이≤200 | - |
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| processId | Identifier | Y | N | - | - |
| processName | string | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |
| status | EquipmentStatus | Y | N | - | - |
| statusStartedAt | string(date-time) | Y | N | - | - |
| activeAlarmCount | integer | Y | N | 값≥0 | - |
| mainMeasurements | array<CurrentDataItem> | N | N | - | - |
| abnormalMeasurements | array<AbnormalMeasurement> | N | N | - | - |

### ProcessEquipmentResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<EquipmentSummary> | Y | N | - | - |

### EquipmentLayoutData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| statusCounts | array<EquipmentStatusCount> | Y | N | - | - |
| equipment | array<EquipmentSummary> | Y | N | - | - |
| processGroups | array<object> | N | N | - | - |
| suggestedEquipmentId | integer(int64) | N | Y | 값≥1 | FAULT/CAUTION 중 가장 최근 상태 변경 설비. 없으면 null. |

### EquipmentLayoutResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | EquipmentLayoutData | Y | N | - | - |

### EquipmentBasicInfo

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentCode | string | Y | N | - | - |
| equipmentName | string | Y | N | - | - |
| equipmentType | string | Y | N | - | - |
| serialNumber | string | N | Y | - | - |
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| processId | Identifier | Y | N | - | - |
| processName | string | Y | N | - | - |
| processOrder | integer | Y | N | - | - |
| communicationMethod | string | Y | N | - | - |
| photoUrl | string(uri) | N | Y | - | - |
| registeredAt | string(date-time) | N | N | - | - |

### EquipmentDetailData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| basicInfo | EquipmentBasicInfo | Y | N | - | - |
| status | EquipmentStatus | Y | N | - | - |
| statusStartedAt | string(date-time) | Y | N | - | - |
| lastReceivedAt | string(date-time) | Y | N | - | - |
| delayed | boolean | N | N | - | - |
| measurements | array<CurrentDataItem> | Y | N | - | - |
| stateValues | array<CurrentDataItem> | Y | N | - | - |
| cumulativeValues | array<CurrentDataItem> | Y | N | - | - |

### EquipmentDetailResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | EquipmentDetailData | Y | N | - | - |

### DataItemListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<CurrentDataItem> | Y | N | - | - |

### MeasurementPoint

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| measuredAt | string(date-time) | Y | N | - | - |
| value | number | Y | Y | - | 미수신 구간은 null. STATE 0/1, CUMULATIVE 0 이상 정수 |

### AlarmMarker

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| alarmId | Identifier | Y | N | - | - |
| occurredAt | string(date-time) | Y | N | - | - |
| value | number | Y | N | - | - |
| grade | AlarmGrade | Y | N | - | - |

### DisconnectionInterval

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| startedAt | string(date-time) | Y | N | - | - |
| endedAt | string(date-time) | N | Y | - | - |

### MeasurementSeriesData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| dataItemId | Identifier | Y | N | - | - |
| dataItemName | string | N | N | - | - |
| valueType | DataValueType | Y | N | - | - |
| unit | string | N | Y | - | - |
| cautionThreshold | number | N | Y | - | - |
| criticalThreshold | number | N | Y | - | - |
| points | array<MeasurementPoint> | Y | N | - | - |
| alarmMarkers | array<AlarmMarker> | Y | N | - | - |
| disconnections | array<DisconnectionInterval> | Y | N | - | - |
| currentlyConnected | boolean | Y | N | - | - |
| abnormalIntervals | array<object> | N | N | - | - |
| bucketSeconds | integer | N | N | 값≥1 | - |

### InventoryLine

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| quantity | integer(int64) | Y | Y | 값≥0 | 개 |
| consumptionPerMinute | number(double) | Y | Y | 값≥0 | 개/분 |
| productionPerMinute | number(double) | Y | Y | 값≥0 | 개/분 |
| calculatedAt | string(date-time) | N | N | - | - |
| calculationStatus | string | N | N | 허용 READY, INSUFFICIENT_DATA, COUNTER_RESET_PENDING, COUNTER_INCONSISTENT | - |

### InventoryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<InventoryLine> | Y | N | - | - |

### InventoryDetail

상속: InventoryLine

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| stage | string | Y | Y | 허용 MAINTAIN, CAUTION, DANGER | - |
| remainingMinutes | number | Y | Y | 값≥0 | - |
| expectedDepletionAt | string(date-time) | Y | Y | - | - |
| expectedRecoveryAt | string(date-time) | Y | Y | - | - |
| appropriateInventory | integer | Y | Y | 값≥0 | 프로젝트 제안: 소비 속도 × 주의 기준 분을 올림. 데이터 부족 시 null |
| cycleTimeSeconds | number | Y | Y | 값≥0 | 프로젝트 제안: 소비 속도 > 0이면 60/소비 속도. 그 외 null |
| calculationStatus | string | Y | N | 허용 READY, INSUFFICIENT_DATA, COUNTER_RESET_PENDING, COUNTER_INCONSISTENT | - |

### InventoryDetailResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | InventoryDetail | Y | N | - | - |

### InventoryHistoryPoint

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| recordedAt | string(date-time) | Y | N | - | - |
| lineId | Identifier | Y | N | - | - |
| lineName | string | N | N | - | - |
| quantity | integer(int64) | Y | N | 값≥0 | - |

### InventoryHistoryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<InventoryHistoryPoint> | Y | N | - | - |

### LineStopForecast

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| currentInventory | integer(int64) | Y | Y | 값≥0 | - |
| netConsumptionPerMinute | number(double) | N | Y | - | - |
| remainingMinutes | integer | N | Y | 값≥0 | - |
| stage | string | Y | Y | 허용 MAINTAIN, CAUTION, DANGER | - |
| assemblyStopExpectedAt | string(date-time) | N | Y | - | - |
| sourceEquipmentId | integer(int64) | N | Y | - | - |
| activeAlarmId | integer(int64) | N | Y | - | - |
| recoveryExpectedAt | string(date-time) | N | Y | - | - |
| marginMinutes | integer | N | Y | - | - |
| marginStatus | string | N | Y | 허용 AVAILABLE, SHORTAGE | - |
| hourlyProduction | number | N | Y | 값≥0 | - |
| unitPrice | number | N | Y | 값≥0 | 원/개 |
| expectedLossAmount | number | N | Y | 값≥0 | 원 |

### AssemblyStopForecastData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| representativeLineId | integer(int64) | N | Y | - | - |
| earliestAssemblyStopExpectedAt | string(date-time) | N | Y | - | - |
| lines | array<LineStopForecast> | Y | N | - | - |

### AssemblyStopForecastResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | AssemblyStopForecastData | Y | N | - | - |

### AlarmSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| alarmId | Identifier | Y | N | - | - |
| equipmentId | Identifier | Y | N | - | - |
| equipmentCode | string | N | N | 길이≤100 | - |
| equipmentName | string | Y | N | 길이≤200 | - |
| lineId | Identifier | N | N | - | - |
| lineName | string | N | N | 길이≤100 | - |
| dataItemId | Identifier | N | N | - | - |
| dataItemName | string | N | N | 길이≤100 | - |
| alarmMessage | string | Y | N | 길이≤1000 | - |
| cause | string | N | N | 길이≤1000 | - |
| currentValue | number | N | Y | - | - |
| occurredValue | number | N | N | - | - |
| thresholdValue | number | N | Y | - | - |
| unit | string | N | Y | - | - |
| grade | AlarmGrade | Y | N | - | - |
| valueStatus | AlarmValueStatus | Y | N | - | - |
| processStatus | AlarmProcessStatus | Y | N | - | - |
| occurredAt | string(date-time) | Y | N | - | - |
| recoveredAt | string(date-time) | N | Y | - | - |
| closedAt | string(date-time) | N | Y | - | - |
| elapsedSeconds | integer | Y | N | 값≥0 | - |
| durationSeconds | integer | N | Y | 값≥0 | - |
| actionContent | string | N | Y | - | - |
| sparkline | array<MeasurementPoint> | N | N | - | - |

### AlarmPageData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| activeCount | integer | Y | N | 값≥0 | - |
| todayOccurredCount | integer | Y | N | 값≥0 | - |
| todayClosedCount | integer | Y | N | 값≥0 | - |
| averageActionMinutes | number | Y | Y | 값≥0 | - |
| items | array<AlarmSummary> | Y | N | - | - |

### AlarmPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | AlarmPageData | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### UserBrief

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| userId | Identifier | Y | N | - | - |
| name | string | Y | N | 길이≤100 | - |
| permission | Permission | Y | N | - | - |

### AlarmDetailData

상속: AlarmSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| assemblyStopExpectedAt | string(date-time) | Y | Y | - | - |
| trendBeforeAndAfter | array<MeasurementPoint> | Y | N | - | - |
| cautionThreshold | number | Y | Y | - | - |
| criticalThreshold | number | Y | Y | - | - |
| equipmentHistories | array<AlarmSummary> | Y | N | - | - |
| action | AlarmActionData | Y | Y | - | - |

### AlarmDetailResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | AlarmDetailData | Y | N | - | - |

### AlarmActionRequest

equipmentText는 조치 화면의 설비 자유 입력값이다. alarmId의 설비 연관관계는 변경하지 않는다. 공백만 있는 필수값은 거부한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentText | string | Y | N | 길이≥1; 길이≤200 | - |
| cause | string | N | Y | 길이≤1000 | 누락·공백이면 알람 내용을 그대로 저장 |
| actionContent | string | Y | N | 길이≥1; 길이≤4000 | - |

### AlarmActionData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| alarmId | Identifier | Y | N | - | - |
| processStatus | string | Y | N | 허용 CLOSED | - |
| equipmentText | string | Y | N | 길이≤200 | - |
| cause | string | Y | N | 길이≤1000 | - |
| actionContent | string | Y | N | 길이≤4000 | - |
| closedAt | string(date-time) | Y | N | - | - |
| handledBy | UserBrief | Y | N | - | - |
| durationSeconds | integer | Y | N | 값≥0 | - |

### AlarmActionResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | AlarmActionData | Y | N | - | - |

### AlarmHistoryItem

참조: AlarmSummary

### AlarmHistoryPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<AlarmHistoryItem> | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### FaultHistoryItem

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| alarmId | Identifier | Y | N | - | - |
| occurredAt | string(date-time) | Y | N | - | - |
| closedAt | string(date-time) | Y | N | - | - |
| cause | string | Y | N | 길이≤1000 | - |
| actionContent | string | Y | N | 길이≤4000 | - |
| durationSeconds | integer | Y | N | 값≥0 | - |
| handledBy | UserBrief | Y | N | - | - |

### FaultHistoryData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| monthlyFaultCount | integer | Y | N | 값≥0 | - |
| averageRepairMinutes | number(double) | Y | N | 값≥0 | - |
| averageFailureIntervalMinutes | number(double) | Y | Y | 값≥0 | - |
| items | array<FaultHistoryItem> | Y | N | - | - |

### FaultHistoryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | FaultHistoryData | Y | N | - | - |

### AlarmResponseStatistics

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| totalCount | integer | Y | N | 값≥0 | - |
| cautionCount | integer | Y | N | 값≥0 | - |
| criticalCount | integer | Y | N | 값≥0 | - |
| activeCount | integer | Y | N | 값≥0 | - |
| averageActionMinutes | number | Y | Y | 값≥0 | - |
| longestActionMinutes | number | Y | Y | 값≥0 | - |

### AlarmResponseStatisticsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | AlarmResponseStatistics | Y | N | - | - |

### DailyAlarmCount

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| date | string(date) | Y | N | - | - |
| criticalCount | integer | Y | N | 값≥0 | - |
| cautionCount | integer | Y | N | 값≥0 | - |

### DailyAlarmStatisticsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<DailyAlarmCount> | Y | N | - | - |

### AvailabilityMetric

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| targetType | string | Y | N | 허용 LINE, EQUIPMENT | - |
| targetId | Identifier | Y | N | - | - |
| targetName | string | Y | N | - | - |
| availabilityRate | number(double) | Y | N | 값≥0; 값≤100 | - |
| operatingMinutes | number(double) | Y | N | 값≥0 | - |
| plannedMinutes | number(double) | Y | N | 값≥0 | - |

### AvailabilityStatisticsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<AvailabilityMetric> | Y | N | - | - |

### DowntimeMetric

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentName | string | Y | N | - | - |
| faultMinutes | number(double) | Y | N | 값≥0 | - |
| idleMinutes | number(double) | Y | N | 값≥0 | - |
| communicationLostMinutes | number(double) | Y | N | 값≥0 | - |

### DowntimeStatisticsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<DowntimeMetric> | Y | N | - | - |

### EquipmentRankingItem

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| rank | integer | Y | N | 값≥1 | - |
| equipmentId | Identifier | Y | N | - | - |
| equipmentName | string | Y | N | - | - |
| faultCount | integer | Y | N | 값≥0 | - |
| downtimeMinutes | number(double) | Y | N | 값≥0 | - |
| averageRepairMinutes | number(double) | Y | N | 값≥0 | - |
| lossAmount | number | Y | N | 값≥0 | 원 |

### EquipmentRankingResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<EquipmentRankingItem> | Y | N | - | - |

### DailyLoss

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| date | string(date) | Y | N | - | - |
| lossAmount | number | Y | N | 값≥0 | - |
| assemblyStopMinutes | number(double) | Y | N | 값≥0 | - |
| causeEquipmentId | integer(int64) | N | Y | - | - |
| causeEquipmentName | string | N | Y | - | - |

### LossStatistics

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| todayLossAmount | number | Y | N | 값≥0 | - |
| weeklyLossAmount | number | Y | N | 값≥0 | - |
| daily | array<DailyLoss> | Y | N | - | - |

### LossStatisticsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | LossStatistics | Y | N | - | - |

### EquipmentWriteRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentCode | string | Y | N | 길이≥1; 길이≤100 | 등록 후 수정 불가 |
| equipmentName | string | Y | N | 길이≥1; 길이≤200 | - |
| equipmentType | string | Y | N | 길이≥1; 길이≤100 | - |
| serialNumber | string | N | Y | 길이≤200 | - |
| lineId | Identifier | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |
| communicationMethod | string | Y | N | 길이≥1; 길이≤100 | - |
| photo | string(binary) | N | Y | - | - |
| registeredAt | string(date-time) | N | N | - | 등록 화면 입력값. 등록 시 생략하면 서버 현재 시각. 수정 시 생략하면 기존 값 유지. |

### EquipmentUpdateRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentName | string | Y | N | 길이≥1; 길이≤200 | - |
| equipmentType | string | Y | N | 길이≥1; 길이≤100 | - |
| serialNumber | string | N | Y | 길이≤200 | - |
| lineId | Identifier | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |
| communicationMethod | string | Y | N | 길이≥1; 길이≤100 | - |
| photo | string(binary) | N | Y | - | - |
| removePhoto | boolean | N | N | 기본 false | - |
| registeredAt | string(date-time) | N | N | - | 등록 화면 입력값. 등록 시 생략하면 서버 현재 시각. 수정 시 생략하면 기존 값 유지. |

### MasterEquipment

상속: EquipmentBasicInfo

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lastReceivedAt | string(date-time) | Y | Y | - | - |

### MasterEquipmentPageData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| registeredCount | integer | Y | N | 값≥0 | - |
| monitoredCount | integer | Y | N | 값≥0 | - |
| items | array<MasterEquipment> | Y | N | - | - |

### MasterEquipmentPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | MasterEquipmentPageData | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### MasterEquipmentResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | MasterEquipment | Y | N | - | - |

### LineWriteRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineName | string | Y | N | 길이≥1; 길이≤100 | - |
| displayOrder | integer | Y | N | 값≥1 | - |

### LineLayoutEquipment

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |

### LineLayoutUpdateRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipment | array<LineLayoutEquipment> | Y | N | 최소 1개 | - |

### MasterLine

상속: LineSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipment | array<EquipmentSummary> | Y | N | - | - |

### MasterLineListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<MasterLine> | Y | N | - | - |

### MasterLineResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | MasterLine | Y | N | - | - |

### MeasurementThreshold

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| cautionValue | number | Y | N | - | - |
| criticalValue | number | Y | N | - | - |
| direction | ThresholdDirection | Y | N | - | - |

### StateAlarmCondition

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| alarmValue | integer | Y | N | 허용 0, 1 | 수집 STATE와 같은 숫자 0 또는 1 |
| grade | AlarmGrade | Y | N | - | - |

### DataItemWriteRequest

valueType에 따라 측정값/상태값/누적값 전용 필드를 검증한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemName | string | Y | N | 길이≥1; 길이≤100 | - |
| valueType | DataValueType | Y | N | - | - |
| unit | string | N | Y | 길이≤30 | - |
| collectionIntervalSeconds | integer | Y | N | 값≥1 | - |
| decimalPlaces | integer | N | Y | 값≥0 | - |
| zeroDisplayName | string | N | Y | 길이≤100 | - |
| oneDisplayName | string | N | Y | 길이≤100 | - |
| measurementThreshold | MeasurementThreshold | N | Y | - | - |
| stateAlarmCondition | StateAlarmCondition | N | Y | - | - |

### ThresholdWriteRequest


### ThresholdStatistics

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| recentMinimum | number | N | Y | - | - |
| recentAverage | number | N | Y | - | - |
| recentMaximum | number | N | Y | - | - |
| alarmCountLast30Days | integer | N | N | 값≥0 | - |

### MasterDataItem

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemId | Identifier | Y | N | - | - |
| dataItemName | string | Y | N | - | - |
| valueType | DataValueType | Y | N | - | - |
| unit | string | N | Y | - | - |
| collectionIntervalSeconds | integer | Y | N | 값≥1 | - |
| decimalPlaces | integer | N | Y | 값≥0 | - |
| zeroDisplayName | string | N | Y | - | - |
| oneDisplayName | string | N | Y | - | - |
| latestValue | number / string | N | N | - | - |
| lastReceivedAt | string(date-time) | N | Y | - | - |
| measurementThreshold | MeasurementThreshold | N | Y | - | - |
| stateAlarmCondition | StateAlarmCondition | N | Y | - | - |
| statistics | ThresholdStatistics | N | N | - | - |

### MasterDataItemListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<MasterDataItem> | Y | N | - | - |

### MasterDataItemResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | MasterDataItem | Y | N | - | - |

### ThresholdData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemId | Identifier | Y | N | - | - |
| measurementThreshold | MeasurementThreshold | N | Y | - | - |
| stateAlarmCondition | StateAlarmCondition | N | Y | - | - |
| appliedAt | string(date-time) | Y | N | - | - |

### ThresholdResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | ThresholdData | Y | N | - | - |

### JudgementSettings

정상 복귀에도 alarmConsecutiveCount를 사용한다. 남은 시간 구분 기준은 주의분이며 위험분은 주의분/3으로 계산(프로젝트 제안값).

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| communicationLossSeconds | integer | Y | N | 값≥10; 값≤300; 기본 30 | - |
| alarmConsecutiveCount | integer | Y | N | 값≥1; 값≤10; 기본 3 | - |
| inventoryWarningMinutes | integer | Y | N | 값≥5; 값≤120; 기본 30 | - |

### JudgementSettingRange

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| field | string | Y | N | - | - |
| minimum | number | Y | N | - | - |
| maximum | number | Y | N | - | - |

### JudgementSettingsData

상속: JudgementSettings

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| ranges | array<JudgementSettingRange> | Y | N | - | - |

### JudgementSettingsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | JudgementSettingsData | Y | N | - | - |

### CalculationSettingsUpdateRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| unitPrice | number | Y | N | 값≥0; 값≤100000000 | 원/개. 상한은 프로젝트 제안값 |
| speedWindowMinutes | integer | Y | N | 값≥5; 값≤60; 기본 10 | - |
| defaultRecoveryMinutes | integer | Y | N | 값≥5; 값≤240; 기본 30 | - |
| hourlyProduction | integer | Y | N | 값≥1; 값≤1000 | 개/시간, 관리자가 입력 |

### CalculationSettings

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| unitPrice | number | Y | N | 값≥0; 값≤100000000 | 원/개. 상한은 프로젝트 제안값 |
| speedWindowMinutes | integer | Y | N | 값≥5; 값≤60; 기본 10 | - |
| defaultRecoveryMinutes | integer | Y | N | 값≥5; 값≤240; 기본 30 | - |
| hourlyProduction | integer | Y | N | 값≥1; 값≤1000 | 개/시간, 관리자가 입력 |

### CalculationSettingsResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | CalculationSettings | Y | N | - | - |

### User

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| userId | Identifier | Y | N | - | - |
| loginId | string | Y | N | 길이≥4; 길이≤20 | - |
| name | string | Y | N | 길이≤100 | - |
| telegramConnected | boolean | Y | N | - | - |
| telegramLinkedAt | string(date-time) | N | Y | - | - |
| permission | Permission | Y | N | - | - |
| accountStatus | AccountStatus | Y | N | - | - |
| lastLoginAt | string(date-time) | N | Y | - | - |
| createdAt | string(date-time) | Y | N | - | - |
| updatedAt | string(date-time) | Y | N | - | - |

### UserUpdateRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| name | string | Y | N | 길이≥1; 길이≤100 | - |
| permission | Permission | Y | N | - | - |

### SignupApprovalRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| permission | Permission | Y | N | - | - |

### SignupRejectionRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| reason | string | N | N | 길이≥1; 길이≤500 | - |

### SignupRequestSummary

승인 대기 목록의 한 항목. 가입 응답(Signup)과 달리 telegramLink 가 없다. 연결 토큰은 해시로만 저장하고
10분 뒤 만료되어 이미 접수된 신청의 연결 주소는 만들 수 없으며, 승인 화면에는 연결 여부만 필요하다(FR0107).


| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| requestId | Identifier | Y | N | - | - |
| loginId | string | Y | N | - | - |
| name | string | Y | N | - | - |
| accountStatus | AccountStatus | Y | N | - | - |
| requestedAt | string(date-time) | Y | N | - | - |
| telegramConnected | boolean | Y | N | - | - |

### SignupPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<SignupRequestSummary> | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### UserResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | User | Y | N | - | - |

### UserPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<User> | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### TelegramLink

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| linkUrl | string(uri) | Y | N | - | 봇 deep link |
| qrPayload | string(uri) | Y | N | - | QR 코드에 넣을 동일 URL |
| expiresAt | string(date-time) | Y | N | - | - |

### TelegramLinkResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | TelegramLink | Y | N | - | - |

### TelegramConnection

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| connected | boolean | Y | N | - | - |
| linkedAt | string(date-time) | N | Y | - | - |

### TelegramConnectionResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | TelegramConnection | Y | N | - | - |

### TelegramTestMessageRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| userId | Identifier | Y | N | - | - |

### TelegramTestMessageResult

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| userId | Identifier | Y | N | - | - |
| result | DeliveryResult | Y | N | - | - |
| failureReason | string | N | Y | - | - |
| sentAt | string(date-time) | Y | N | - | - |

### TelegramTestMessageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | TelegramTestMessageResult | Y | N | - | - |

### TelegramDelivery

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| deliveryId | Identifier | Y | N | - | - |
| sentAt | string(date-time) | Y | N | - | - |
| recipient | UserBrief | Y | Y | - | - |
| recipientName | string | N | N | - | 발송 시점 이름 스냅샷. 삭제·거절 계정도 보존 |
| alarmId | integer(int64) | N | Y | - | - |
| messageSummary | string | Y | N | - | - |
| result | DeliveryResult | Y | N | - | - |
| failureReason | string | N | Y | - | - |

### TelegramDeliverySummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| totalCount | integer | Y | N | 값≥0 | - |
| successCount | integer | Y | N | 값≥0 | - |
| failureCount | integer | Y | N | 값≥0 | - |

### TelegramDeliveryPageData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| summary | TelegramDeliverySummary | Y | N | - | - |
| items | array<TelegramDelivery> | Y | N | - | - |
| recipientCounts | array<TelegramRecipientCount> | Y | N | - | - |

### TelegramRecipientCount

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| recipient | UserBrief | Y | N | - | - |
| totalCount | integer | Y | N | 값≥0 | - |
| successCount | integer | Y | N | 값≥0 | - |
| failureCount | integer | Y | N | 값≥0 | - |

### TelegramDeliveryPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | TelegramDeliveryPageData | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### ChangeHistory

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| historyId | Identifier | Y | N | - | - |
| changedAt | string(date-time) | Y | N | - | - |
| changer | UserBrief | Y | N | - | - |
| itemType | string | Y | N | - | - |
| itemName | string | Y | N | - | - |
| previousValue | string | Y | Y | - | 비밀번호와 토큰 제외 |
| changedValue | string | Y | Y | - | 비밀번호와 토큰 제외 |

### ChangeHistoryPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<ChangeHistory> | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### MeasurementValueIngestion

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemCode | string | Y | N | 길이≥1; 길이≤50; 패턴 `^[a-z][a-z0-9_]*$` | V2 data_item.item_code와 동일 |
| valueType | DataValueType | Y | N | - | - |
| value | number | Y | N | - | STATE는 0 또는 1, CUMULATIVE는 0 이상의 정수 |

### MeasurementIngestionRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| messageId | string | Y | N | 길이≥1; 길이≤200 | 설비 코드와 측정 시각을 합친 중복 제거 키 |
| equipmentCode | string | Y | N | 길이≥1; 길이≤20; 패턴 `^[A-Z]{2}-[0-9]{2}-[1-9][0-9]*$` | - |
| measuredAt | string(date-time) | Y | N | - | Node-RED가 기록한 시각 |
| values | array<MeasurementValueIngestion> | Y | N | 최소 1개; 최대 500개 | - |

### IngestionAccepted

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| messageId | string | Y | N | - | - |
| acceptedAt | string(date-time) | Y | N | - | - |
| duplicate | boolean | Y | N | - | - |

### IngestionAcceptedResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | IngestionAccepted | Y | N | - | - |

### RecentAlarmListResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<AlarmSummary> | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### MultiMeasurementData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| series | array<MeasurementSeriesData> | Y | N | - | - |
| disconnections | array<DisconnectionInterval> | Y | N | - | - |
| currentlyConnected | boolean | Y | N | - | - |

### MultiMeasurementResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | MultiMeasurementData | Y | N | - | - |

### CriticalEquipmentCount

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentName | string | Y | N | 길이≤200 | - |
| criticalCount | integer | Y | N | 값≥0 | - |

### CriticalEquipmentResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<CriticalEquipmentCount> | Y | N | - | - |

### StopCause

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| cause | string | Y | N | 길이≤1000 | - |
| source | string | Y | N | 허용 ALARM, COMMUNICATION_LOST | - |
| downtimeMinutes | number | Y | N | 값≥0 | - |

### StopCauseResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<StopCause> | Y | N | - | - |

### Notification

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| notificationId | Identifier | Y | N | - | - |
| alarmId | Identifier | Y | N | - | - |
| eventType | string | Y | N | 허용 CREATED, GRADE_ESCALATED | - |
| createdAt | string(date-time) | Y | N | - | - |
| readAt | string(date-time) | Y | Y | - | - |
| alarm | AlarmSummary | Y | N | - | - |

### NotificationPageResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | object | Y | N | - | - |
| meta | PageMeta | Y | N | - | - |

### NotificationReadRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| read | boolean | Y | N | 허용 true | - |

### NotificationResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | Notification | Y | N | - | - |

### TelegramUpdate

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| update_id | integer(int64) | Y | N | - | - |
| message | object | Y | N | - | - |

### TelegramUpdateResult

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | object | Y | N | - | - |

## 13. 이번 명세 개정의 검증 결과

- OpenAPI 3.0.3 lint: 오류0·경고0. 경로59·operation69·API ID중복0.
- 최신 FR75건 모두 API 또는 내부/화면 처리에 연결. 삭제 FR을 operation에 연결하지 않음.
- 기존 코드의 전체 테스트 재실행: 453건 중446통과·1실패·6건너뜀. 실패는 OpenApiStompContractTest.sevenStompSchemasMatchTheActualPublishedRecordFields의 expected7/actual6이다. v6에서 효과 확인 topic을 삭제한 계약과 v5의7개 topic 기대가 충돌한다. MySqlBackendBTest 6건은 건너뜀이다.
- 기존 테스트/구현은 이번 문서 변경 범위에서 수정하지 않았다. v6로 전환할 때 STOMP 이벤트 Record·테스트를 포함해11절의 변경을 함께 구현해야 한다.
