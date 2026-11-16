# STF 설비통합관제 API 명세서

- 문서 버전: 2.0.0-draft
- 작성 기준일: 2026-11-15
- 대상 시스템: 등속조인트 생산 공정 설비통합관제 시스템
- REST Base URL: `/api/v1`
- 내부 수집 Base URL: `/internal/v1`
- 상세 계약: `docs/openapi.yaml`

> 이 문서는 요구사항정의서의 FR/NFR/IFR을 우선 기준으로 삼고, 기능 변경내역과 기능목록 수정본으로 변경 여부를 검증한 뒤, `2chong/dashboard_1` 화면 시안에서 확인되는 사용자 입력과 표시 필드만 보완했다. 실제 공정값과 화면의 예시값은 API의 고정 기준값으로 사용하지 않는다.

## 1. 첨부자료 분석 결과

### 1.1 기준 문서와 우선순위

1. 요구사항정의서 1.0(2026-11-15): FR 86건, NFR 13건, IFR 4건
2. 기능 변경내역(2026-11-15): 추가 6건, 삭제 6건, 변경 42건
3. 기능목록 수정본(2026-11-15): Back 83개, Front 51개 및 주고받는 정보
4. 화면 시안 저장소 `2chong/dashboard_1`, commit `bf4e8ec4bb4bcb9c6d4780057282e483aab10d6e`

### 1.2 이번 명세에 반영한 변경

- 추가: 헤더 통신 상태, 상태값 알람 판정, 담당 지정 카카오톡 알림, 손실 방지 실적, 알람 당번, 상태 설명 도움말
- 삭제: 현황 요약, 생산현황 2개, 기간 비교, 이력 파일 저장, 담당자 없는 라인 확인
- 변경: 직책과 권한 분리, 측정값/상태값/누적값 구분, 설비 사진, 상태값 표시명, 알람 단계별 처리, 기간 선택 방식, 부품 수명 계산 기준, 계산 기준값 단순화
- REST API로 만들지 않은 항목: 임계값 판정, 통신 끊김 판정, 설비 상태 판정, 중복 알람 방지, 재고 계산, 복구/손실 계산, 효과 확인과 자동 종료 등 서버 내부 자동 처리

## 2. 확인이 필요한 사항

| ID | 확인 주체 | 확인 내용 | 현재 처리 |
|---|---|---|---|
| Q-01 | Back | 시간당 생산량과 분당 소비 개수를 수집값으로 자동 계산할 수 있는가 | 생산 이벤트 수집 API를 제안하되 계산식은 보류 |
| Q-02 | Back/기획 | 손실 방지액 계산식 | 결과 필드만 명세, 계산식 보류 |
| Q-03 | 기획 | 재고와 통신 끊김도 알림 전송 대상인가 | 알림 API/토픽에서 제외 |
| Q-04 | 현장 | 실제 PLC 항목 목록과 단위 | 데이터 항목 기준정보로 등록, 고정 목록 미정 |
| Q-05 | 기획 | 재고 주의/위험 단계 기준 | 화면의 30분/10분은 예시값이며 설정값으로 관리 |
| Q-06 | 기획 | 미확인 단계에서 오경보 신고를 허용하는가 | 상태 전이 검증값 보류 |
| Q-07 | 기획 | 통신 현황을 시스템관리에서 설비관리로 이동하는가 | 조회 API는 일반 사용자 허용, 메뉴 위치는 보류 |
| Q-08 | 기획 | 계산 기준값과 판정 설정 화면을 합치는가 | API는 분리 유지 |
| Q-09 | 기획 | 메뉴 알람 수와 헤더 종의 알람 수 정의 | 미확인 알람 수로 제안 |
| Q-10 | 기획 | 누적값을 설비 상세 외 화면에도 표시하는가 | 설비 상세와 내부 계산에만 사용 |
| Q-11 | 보안 | JWT 만료시간과 로그아웃 토큰 폐기 방식 | Access Token만 사용, denylist/tokenVersion 방식은 제안 |
| Q-12 | 운영 | 관리자 비밀번호 초기화 후 전달 방식 | API는 204만 정의, 임시 비밀번호/초기화 링크 방식 보류 |
| Q-13 | 기획 | 목록 기본 page/size와 최대 size | 미정, 구현 전 확정 필요 |

## 3. 문서 간 불일치 사항

| ID | 불일치 | 적용 기준 |
|---|---|---|
| C-01 | 이전 명세는 `SYSTEM_ADMIN/LINE_MANAGER/MAINTENANCE`를 역할로 사용했으나 최신 문서는 직책 3종과 권한 2종을 분리함 | `position` 3종과 `permission` 2종으로 분리 |
| C-02 | 이전 상세 프롬프트는 최종 로그인 일시를 요구했으나 최신 요구사항과 화면에는 없음 | `lastLoginAt` 미포함 |
| C-03 | 이전 명세의 생산량 API, 기간 비교, 파일 저장, 담당자 없는 라인 확인은 최신 변경내역에서 삭제됨 | API 삭제 |
| C-04 | FR0102는 새로고침 후 로그인 유지를 요구하고, 과거 사용자 결정은 매번 로그인으로 기록되어 있음 | 최신 FR0102 적용. 토큰 저장 위치는 프론트 보안 결정 필요 |
| C-05 | 화면에는 통신 현황이 시스템관리 아래 있으나 헤더 일부 끊김에서 모든 사용자가 이동하도록 정의됨 | 데이터 조회 권한은 ADMIN/GENERAL, 메뉴 위치는 Q-07 |
| C-06 | 기능목록의 화면 예시에는 실제처럼 보이는 온도, 유량, 금액이 있으나 요구사항은 실제 공정값을 확정하지 않음 | 예시는 API 제약/기본값으로 사용하지 않음 |

## 4. 작성 시 사용한 명시적 가정

- 식별자는 구현 독립성을 위해 UUID 문자열을 제안한다. 설비의 현장 식별자는 별도 `equipmentCode`로 둔다.
- 등록/수정 시각과 이벤트 시각은 ISO 8601 `date-time`으로 송수신한다.
- 직책 코드는 `SYSTEM_ADMIN`, `LINE_MANAGER`, `MAINTENANCE`, 권한 코드는 `ADMIN`, `GENERAL`로 제안한다.
- 시스템관리 `/system/**`는 `ADMIN`만 접근하고, 그 밖의 사용자 화면 API는 `ADMIN`과 `GENERAL`이 접근한다.
- 로그아웃은 요구사항의 “토큰 폐기”를 충족하기 위해 서버 측 denylist 또는 사용자 tokenVersion 검증이 필요하다.
- 설비 사진은 `multipart/form-data`의 바이너리 파일로 받고, 조회 시 URL을 반환한다.
- 화면의 더미 설비 코드, 수치, 사용자, 기간은 예시일 뿐 검증 규칙으로 고정하지 않는다.

## 5. API 공통 규칙

| 항목 | 규칙 |
|---|---|
| Base URL | 사용자 API `/api/v1`, Node-RED 내부 수집 `/internal/v1` |
| 인증 | `Authorization: Bearer <accessToken>`; Refresh Token 없음 |
| Content-Type | JSON은 `application/json; charset=UTF-8`, 사진 포함 등록/수정은 `multipart/form-data` |
| 이름 규칙 | URL은 복수형 명사, JSON은 camelCase |
| 시간 | ISO 8601 `date-time`, 날짜는 `date` |
| 페이징 | `page`는 0부터, `size` 기본/최대는 미정, `sort=field,asc|desc` |
| 조회 | GET, 등록 POST, 전체 수정 PUT, 부분 수정/상태 변경 PATCH |
| 삭제 | 이력 보존 대상은 비활성화 우선. 라인과 당번만 요구사항상 DELETE 허용 |
| 성공 코드 | 조회/수정 200, 등록 201, 본문 없는 성공 204 |
| 오류 코드 | 400 형식, 401 인증, 403 권한, 404 없음, 409 중복/상태충돌, 422 업무규칙, 500 서버오류 |
| 응답 | 성공 `{success:true,data,meta?}`, 실패 `{success:false,error:{code,message,details?,traceId?}}` |
| 추적 확장 | OpenAPI operation에 `x-api-id`, `x-requirement-ids`, `x-nfr-ids`, `x-interface-ids`, `x-business-rule-ids`, `x-test-ids`, `x-permissions` 사용 |

업무규칙 ID와 테스트 ID는 기준 문서에 정의되어 있지 않으므로 임의 생성하지 않고 빈 배열로 둔다.

## 6. 도메인·변수·Enum 정의

| 구분 | 코드 | 한글 표시 | 출처 |
|---|---|---|---|
| Position | `SYSTEM_ADMIN` | 시스템관리자 | FR1403 |
| Position | `LINE_MANAGER` | 라인 관리자 | FR1403 |
| Position | `MAINTENANCE` | 보전 담당자 | FR1403 |
| Permission | `ADMIN` | 관리자 | FR0103, FR1403 |
| Permission | `GENERAL` | 일반 사용자 | FR0103, FR1403 |
| EquipmentStatus | `RUNNING`, `IDLE`, `CAUTION`, `FAULT`, `COMMUNICATION_LOST` | 가동, 대기, 주의, 고장, 통신 끊김 | FR0301 |
| DataValueType | `MEASUREMENT`, `STATE`, `CUMULATIVE` | 측정값, 상태값, 누적값 | FR0201 |
| AlarmGrade | `CAUTION`, `CRITICAL` | 주의, 경보 | FR0401 |
| AlarmValueStatus | `ABNORMAL`, `RECOVERED` | 이상, 값 정상 복귀 | FR0404 |
| AlarmProcessStatus | `UNACKNOWLEDGED`, `ACKNOWLEDGED`, `ACTION_TAKEN`, `EFFECT_VERIFICATION`, `CLOSED` | 미확인, 확인, 조치, 효과 확인, 종료 | FR0604~FR0610 |
| AlarmResult | `EFFECTIVE`, `FALSE_POSITIVE` | 효과 있음, 오경보 | FR0607, FR0610 |
| FalsePositiveReason | `SENSOR_MALFUNCTION`, `THRESHOLD_MISMATCH`, `INSPECTION_OR_TRIAL`, `OTHER` | 센서 오작동, 기준값 불일치, 점검/시운전, 기타 | FR0607 |
| InventoryStage | `MAINTAIN`, `CAUTION`, `DANGER` | 유지, 주의, 위험 | FR0903 |
| PartLifeBasis | `OPERATING_HOURS`, `ELAPSED_DAYS` | 가동 시간, 경과 일 | FR1101, FR1307 |
| PartUsageStatus | `NORMAL`, `DUE_SOON`, `OVERDUE` | 정상, 임박, 초과 | FR1102 |
| ActivationStatus | `ACTIVE`, `INACTIVE` | 사용, 사용 중지 | FR1303, FR1403 |
| CommunicationStatus | `CONNECTED`, `DISCONNECTED` | 연결, 끊김 | FR0204 |
| HeaderCommunicationStatus | `ALL_NORMAL`, `PARTIAL_DISCONNECTED`, `ALL_DISCONNECTED` | 전체 정상, 일부 끊김, 전체 끊김 | FR0205 |

`ThresholdDirection`, 식별자 타입, 데이터 항목 코드, 오류코드 영문명은 개발을 위해 필요한 제안값이며 OpenAPI에 `x-proposed: true`로 표시한다.

## 7. 전체 API 목록

| API ID | 대분류 | 기능명 | Method | URL | 인증 | 권한 | 관련 요구사항 | 테스트 ID | 우선순위 |
|---|---|---|---|---|---|---|---|---|---|
| AUTH-001 | 인증 | 로그인 | POST | `/auth/login` | 아니오 | ANONYMOUS | FR0101 | 미정 | P0 |
| AUTH-002 | 인증 | 로그아웃 | POST | `/auth/logout` | 예 | ADMIN, GENERAL | FR0104 | 미정 | P0 |
| AUTH-003 | 인증 | 로그인 사용자 조회 | GET | `/auth/me` | 예 | ADMIN, GENERAL | FR0105 | 미정 | P0 |
| COM-001 | 통신 | 통신 현황 조회 | GET | `/communications` | 예 | ADMIN, GENERAL | FR0203, FR0204 | 미정 | P1 |
| COM-002 | 통신 | 헤더 통신 상태 | GET | `/communications/summary` | 예 | ADMIN, GENERAL | FR0205 | 미정 | P0 |
| OVW-001 | 종합현황 | 설비 배치도 | GET | `/overview/equipment-layout` | 예 | ADMIN, GENERAL | FR0701 | 미정 | P0 |
| OVW-002 | 종합현황 | 조립 정지 예측 | GET | `/overview/assembly-stop-forecast` | 예 | ADMIN, GENERAL | FR0903~FR0907 | 미정 | P1 |
| INV-001 | 재고 | 현재 재고 | GET | `/inventory` | 예 | ADMIN, GENERAL | FR0902 | 미정 | P1 |
| INV-002 | 재고 | 재고 추이 | GET | `/inventory/history` | 예 | ADMIN, GENERAL | FR0908 | 미정 | P1 |
| EQP-001 | 설비조회 | 라인 목록 | GET | `/lines` | 예 | ADMIN, GENERAL | FR0701, FR0703 | 미정 | P0 |
| EQP-002 | 설비조회 | 라인 공정 목록 | GET | `/lines/{lineId}/processes` | 예 | ADMIN, GENERAL | FR0703 | 미정 | P0 |
| EQP-003 | 설비조회 | 공정별 설비 | GET | `/processes/{processId}/equipment` | 예 | ADMIN, GENERAL | FR0703 | 미정 | P0 |
| EQP-004 | 설비조회 | 설비 상세 | GET | `/equipment/{equipmentId}` | 예 | ADMIN, GENERAL | FR0704 | 미정 | P0 |
| EQP-005 | 설비조회 | 설비 데이터 항목 | GET | `/equipment/{equipmentId}/data-items` | 예 | ADMIN, GENERAL | FR0801 | 미정 | P0 |
| MEAS-001 | 모니터링 | 측정 추세 | GET | `/equipment/{equipmentId}/measurements` | 예 | ADMIN, GENERAL | FR0802~FR0805 | 미정 | P0 |
| ALM-001 | 알람 | 현재 알람 목록 | GET | `/alarms` | 예 | ADMIN, GENERAL | FR0601, FR0602 | 미정 | P0 |
| ALM-002 | 알람 | 최근 종료 알람 | GET | `/alarms/recent-closed` | 예 | ADMIN, GENERAL | FR0612 | 미정 | P1 |
| ALM-003 | 알람 | 알람 상세 | GET | `/alarms/{alarmId}` | 예 | ADMIN, GENERAL | FR0603, FR0611 | 미정 | P0 |
| ALM-004 | 알람 | 확인 및 담당 지정 | PATCH | `/alarms/{alarmId}/acknowledgement` | 예 | ADMIN, GENERAL | FR0604, FR0508 | 미정 | P0 |
| ALM-005 | 알람 | 과거 조치 추천 | GET | `/alarms/{alarmId}/recommended-actions` | 예 | ADMIN, GENERAL | FR0605 | 미정 | P1 |
| ALM-006 | 알람 | 조치 기록 | POST | `/alarms/{alarmId}/actions` | 예 | ADMIN, GENERAL | FR0606 | 미정 | P0 |
| ALM-007 | 알람 | 오경보 신고 | POST | `/alarms/{alarmId}/false-positive` | 예 | ADMIN, GENERAL | FR0607 | 미정 | P0 |
| ALM-008 | 알람 | 효과 확인 상태 | GET | `/alarms/{alarmId}/effect` | 예 | ADMIN, GENERAL | FR0608~FR0610 | 미정 | P1 |
| HST-001 | 이력 | 알람 이력 | GET | `/alarm-histories` | 예 | ADMIN, GENERAL | FR1201 | 미정 | P1 |
| HST-002 | 이력 | 설비 고장 이력 | GET | `/equipment/{equipmentId}/fault-histories` | 예 | ADMIN, GENERAL | FR1205 | 미정 | P1 |
| PRT-001 | 부품 | 부품 사용량 | GET | `/parts/usage` | 예 | ADMIN, GENERAL | FR1102 | 미정 | P1 |
| PRT-002 | 부품 | 설비 장착 부품 | GET | `/equipment/{equipmentId}/parts` | 예 | ADMIN, GENERAL | FR1102 | 미정 | P1 |
| PRT-003 | 부품 | 부품 교체 등록 | POST | `/equipment/{equipmentId}/parts/{partId}/replacements` | 예 | ADMIN, GENERAL | FR1103 | 미정 | P1 |
| PRT-004 | 부품 | 부품 교체 이력 | GET | `/equipment/{equipmentId}/parts/{partId}/replacements` | 예 | ADMIN, GENERAL | FR1104 | 미정 | P1 |
| STA-001 | 통계 | 대응 속도 | GET | `/statistics/alarm-response` | 예 | ADMIN, GENERAL | FR1202 | 미정 | P1 |
| STA-002 | 통계 | 오경보 | GET | `/statistics/false-positives` | 예 | ADMIN, GENERAL | FR1203 | 미정 | P1 |
| STA-003 | 통계 | 일별 알람 건수 | GET | `/statistics/daily-alarms` | 예 | ADMIN, GENERAL | FR1204 | 미정 | P1 |
| STA-004 | 통계 | 가동률 | GET | `/statistics/availability` | 예 | ADMIN, GENERAL | FR1206 | 미정 | P1 |
| STA-005 | 통계 | 정지 시간 | GET | `/statistics/downtime` | 예 | ADMIN, GENERAL | FR1207 | 미정 | P1 |
| STA-006 | 통계 | 설비 순위 | GET | `/statistics/equipment-rankings` | 예 | ADMIN, GENERAL | FR1208 | 미정 | P1 |
| STA-007 | 통계 | 손실액 | GET | `/statistics/losses` | 예 | ADMIN, GENERAL | FR1209 | 미정 | P2 |
| STA-008 | 통계 | 손실 방지 실적 | GET | `/statistics/loss-avoidance` | 예 | ADMIN, GENERAL | FR1212 | 미정 | P2 |
| MST-001~004 | 기준정보 | 설비 조회/등록/수정/활성 | 복수 | `/master/equipment...` | 예 | ADMIN, GENERAL | FR1301~FR1303 | 미정 | P0 |
| MST-005~008 | 기준정보 | 라인·배치 관리 | 복수 | `/master/lines...` | 예 | ADMIN, GENERAL | FR1304 | 미정 | P1 |
| MST-009~011 | 기준정보 | 데이터 항목·임계값 | 복수 | `/master/.../data-items...` | 예 | ADMIN, GENERAL | FR1305, FR1306 | 미정 | P0 |
| MST-012~014 | 기준정보 | 장착 부품 기준정보 | 복수 | `/master/.../parts...` | 예 | ADMIN, GENERAL | FR1307 | 미정 | P1 |
| SYS-001~004 | 시스템 | 판정/계산 설정 조회·수정 | 복수 | `/system/settings/...` | 예 | ADMIN | FR1401, FR1402 | 미정 | P1 |
| USR-001~006 | 시스템 | 계정 조회·등록·수정·중지·초기화 | 복수 | `/system/users...` | 예 | ADMIN | FR1403 | 미정 | P0 |
| KAK-001~005 | 시스템 | 카카오 연결·시험·전송결과 | 복수 | `/system/kakao...` | 예 | ADMIN | FR0507, FR1405, FR1406 | 미정 | P2 |
| AUD-001 | 시스템 | 변경 이력 | GET | `/system/change-histories` | 예 | ADMIN | FR1407 | 미정 | P1 |
| DUT-001~005 | 시스템 | 알람 당번 CRUD | 복수 | `/system/alarm-duty-schedules...` | 예 | ADMIN | FR1408 | 미정 | P1 |
| INT-001 | 내부수집 | 설비 값 수집 | POST | `/internal/v1/measurements` | 내부키 | Node-RED | FR0201, IFR01 | 미정 | P0 |
| INT-002 | 내부수집 | 생산 이벤트 수집 | POST | `/internal/v1/production-events` | 내부키 | Node-RED | FR0608, FR0901 | 미정 | P1 |

## 8. API 상세 명세

모든 요청은 공통 인증/오류/시간/페이징 규칙을 적용하며, 필드의 타입·필수·nullable·길이·enum·단위·예시·검증·출처는 OpenAPI `components.schemas`를 기준으로 한다.

### 8.1 인증과 통신

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| AUTH-001 | `LoginRequest(loginId,password)` | 토큰, 만료시각, 이름, 직책, 권한 | 사용 계정과 비밀번호 대조, 사용 중지 거부 | AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_INACTIVE | 비밀번호/토큰 로그 금지; 최종 로그인 일시 미저장 |
| AUTH-002 | Bearer Token | 204 | 현재 토큰 폐기 | AUTH_TOKEN_INVALID | JWT 무상태만으로는 폐기 불가하므로 denylist/tokenVersion 필요 |
| AUTH-003 | Bearer Token | `CurrentUser` | 토큰의 사용자 재조회 | AUTH_TOKEN_INVALID | `lastLoginAt` 미포함 |
| COM-001 | lineId, status, page, size, sort | 통신 요약과 설비별 연결/마지막 수신/오늘 끊김 | 끊김 많은 순 정렬 가능 | LINE_NOT_FOUND | 화면 위치는 Q-07 |
| COM-002 | 없음 | 전체/끊긴 설비 수, 헤더 상태, 전체 끊김 시작시각 | 전체 정상/일부/전체 끊김 판정 | - | WebSocket으로 변경 통지 |

### 8.2 종합현황·설비·모니터링·재고

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| OVW-001 | 선택 lineId(선택) | 라인·공정 순서·설비 상태·알람 수·주요 값·상태별 대수 | 등록 기준정보 순서 사용 | LINE_NOT_FOUND | 층 정보는 삭제됨 |
| OVW-002 | 없음 | 라인별 재고, 순소비속도, 소진시간, 정지예상, 복구예상, 여유, 예상손실 | 과거 수리 평균과 자동 생산률 사용 | FORECAST_DATA_INSUFFICIENT | 계산식 일부 Q-01/Q-02 |
| INV-001 | 없음 | 라인별 재고·분당 소비·분당 생산 | 실시간 반영 | - | 최대 재고 비율 삭제 |
| INV-002 | from, to, lineIds | 시각별 라인 재고 | from <= to | INVALID_PERIOD | 긴 기간 집계 간격은 제안값 |
| EQP-001~005 | lineId/processId/equipmentId | 화면 선택 목록과 설비 상세 | 존재/활성 상태 검증 | LINE/PROCESS/EQUIPMENT_NOT_FOUND | 상태값·누적값을 측정값과 분리 반환 |
| MEAS-001 | dataItemId, from, to | 시각별 값, 기준선, 알람 시점, 통신 끊김 구간 | 설비 소속 항목만 허용 | DATA_ITEM_NOT_FOUND, INVALID_PERIOD | 상태값/누적값 차트 표현은 화면 미정 |

### 8.3 알람 처리

| API ID | 요청 | 정상 응답 | 검증·상태 변경 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| ALM-001 | lineId, equipmentId, processStatus, grade, page, size, sort | 미종료 알람 페이지와 발생/미확인 수 | 미확인 → 경보 → 최신 순 기본정렬 | INVALID_FILTER | 새 알람/상태 변경 WebSocket 반영 |
| ALM-002 | limit | 최근 종료 알람 | 최신 종료 순 | - | limit 기본값 미정 |
| ALM-003 | alarmId | 요약, 현재값, 단계시각, 추세, 처리기록, 재발, 효과 | 존재 확인 | ALARM_NOT_FOUND | 값 정상 복귀와 처리 종료를 구분 |
| ALM-004 | assigneeUserId | 확인자/확인시각/담당자/상태 | 미확인에서만 가능, 담당자는 보전 담당자 | ALARM_ALREADY_ACKNOWLEDGED, ASSIGNEE_NOT_MAINTENANCE | 확인자와 담당자는 다를 수 있음 |
| ALM-005 | alarmId | 조치내용, 효과/전체 횟수, 평균수리시간 | 같은 설비·같은 알람 기준 | - | “같은 알람” 식별 기준 추가 정의 필요 |
| ALM-006 | cause, actionContent, replacedPartId? | 조치시각, 상태 | 조치내용 필수, 교체 부품 입력 시 교체 이력 동시 등록 | ALARM_STATE_CONFLICT, ACTION_CONTENT_REQUIRED | 트랜잭션 처리 |
| ALM-007 | reason, detail? | 종료상태와 오경보 결과 | 허용 상태와 1회성 검증 | FALSE_POSITIVE_NOT_ALLOWED | 미확인 단계 허용 여부 Q-06 |
| ALM-008 | alarmId | 목표/현재 생산 수, 대기, 재발, 상태 | 자동 계산 결과만 조회 | - | 수동 통과/종료 API 없음 |

### 8.4 부품·이력·통계

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| PRT-001~004 | line/process/equipment/part/period | 사용량, 교체기준, 잔여, 예상일, 교체이력 | 수명 계산 기준별 계산, 등록 시 사용량 0 초기화 | PART_NOT_FOUND, REQUIRED_FIELD_MISSING | 작업자는 인증 사용자로 자동 기록 |
| HST-001 | 기간, 라인, 설비, 등급, 결과, 페이지, 정렬 | 종료 알람과 전체 건수 | 종료 건만 조회 | INVALID_PERIOD | 읽기 전용 상세 연결 |
| HST-002 | equipmentId, 기간 | 고장 이력, 월 건수, 평균수리, 평균고장간격 | 진행 중 고장 제외 | EQUIPMENT_NOT_FOUND | 평균 산식 추가 정의 필요 |
| STA-001~008 | 기간, 라인, 정렬기준 | 각 통계 집계 | 원천 이력 기반 계산 | INVALID_PERIOD, STATISTICS_DATA_INSUFFICIENT | 실제 손실액과 예상 손실액 구분 |

### 8.5 기준정보와 시스템관리

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| MST-001~004 | 설비 기본/설치/통신/사진 | 설비 기준정보 | 코드 중복 금지, 등록 후 코드 수정 금지, 발생 알람 있으면 중지 거부 | EQUIPMENT_CODE_DUPLICATE, EQUIPMENT_HAS_ACTIVE_ALARM | 이력 보존, 배치/감시 즉시 반영 |
| MST-005~008 | 라인명·표시순서·설비 배치 | 라인/배치 | 설비 존재 라인 삭제 거부 | LINE_HAS_EQUIPMENT | 순서 변경은 일괄 원자 처리 |
| MST-009~011 | 값 종류별 항목/임계값 | 항목과 통계 참고값 | 측정/상태/누적 조건 검증, 주의가 경보보다 먼저 | INVALID_THRESHOLD_ORDER | 임계값 저장 후 다음 판정부터 적용 |
| MST-012~014 | 부품명·규격·관리번호·수명기준·교체기준 | 장착부품 기준정보 | 사용 중지 시 과거 기록 유지 | PART_NOT_FOUND | 단위는 수명 기준과 일치 |
| SYS-001~004 | 판정 5개 설정/개당 금액 | 현재 설정 | 허용범위 검증, 변경 이력 자동 기록 | SETTING_OUT_OF_RANGE | 화면 예시 범위는 요구사항 기반이나 운영값은 DB 설정 |
| USR-001~006 | 이름/아이디/직책/권한/활성 | 계정 목록/상세 | 아이디 중복, 비밀번호 암호화, ADMIN 전용 | USER_LOGIN_ID_DUPLICATE | 최종 로그인 일시 미포함 |
| KAK-001~005 | 사용자/날짜/전송결과 | 연결상태, 연결 URL, 전송결과 | 토큰 암호화 저장·갱신, 실패 1회 재시도 | KAKAO_NOT_CONNECTED, KAKAO_DELIVERY_FAILED | Spring Boot 외부 연동, LLM 없음 |
| AUD-001 | 기간, 항목종류, 검색어, 페이지 | 변경시각·변경자·항목·이전/변경값 | 저장 시 자동 기록 | INVALID_PERIOD | 비밀번호/토큰 값 기록 금지 |
| DUT-001~005 | 시작일·종료일·라인관리자 | 현재/예정 당번 | 기간 공백 금지, 공장 전체 한 조, 라인관리자만 | DUTY_GAP_NOT_ALLOWED, DUTY_OVERLAP | 삭제도 공백 검증 |

## 9. 오류코드 정의

| 영역 | 오류코드 | HTTP | 사용자 메시지 | 발생 조건 | 출처/구분 |
|---|---|---:|---|---|---|
| AUTH | AUTH_INVALID_CREDENTIALS | 401 | 아이디 또는 비밀번호를 확인해 주세요. | 불일치 | FR0101, 제안 코드 |
| AUTH | AUTH_ACCOUNT_INACTIVE | 403 | 사용 중지된 계정입니다. | 비활성 계정 | FR0101, 제안 코드 |
| AUTH | AUTH_TOKEN_INVALID | 401 | 다시 로그인해 주세요. | 누락/만료/폐기 토큰 | FR0102, 제안 코드 |
| AUTH | AUTH_PERMISSION_DENIED | 403 | 접근 권한이 없습니다. | 권한 부족 | FR0103, 제안 코드 |
| LINE | LINE_NOT_FOUND | 404 | 라인을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| LINE | LINE_HAS_EQUIPMENT | 422 | 설비가 있는 라인은 삭제할 수 없습니다. | 삭제 규칙 | FR1304, 제안 코드 |
| EQUIPMENT | EQUIPMENT_NOT_FOUND | 404 | 설비를 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| EQUIPMENT | EQUIPMENT_CODE_DUPLICATE | 409 | 이미 사용 중인 설비 코드입니다. | 코드 중복 | FR1302, 제안 코드 |
| EQUIPMENT | EQUIPMENT_HAS_ACTIVE_ALARM | 422 | 발생 중인 알람이 있어 사용 중지할 수 없습니다. | 중지 규칙 | FR1303, 제안 코드 |
| SENSOR | DATA_ITEM_NOT_FOUND | 404 | 데이터 항목을 찾을 수 없습니다. | 항목 없음 | 제안 코드 |
| SENSOR | INVALID_THRESHOLD_ORDER | 422 | 주의·경보 기준 순서를 확인해 주세요. | 기준 역전 | FR1306, 제안 코드 |
| ALARM | ALARM_NOT_FOUND | 404 | 알람을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| ALARM | ALARM_ALREADY_ACKNOWLEDGED | 409 | 이미 확인된 알람입니다. | 중복 확인 | FR0604, 제안 코드 |
| ALARM | ALARM_STATE_CONFLICT | 409 | 현재 단계에서는 처리할 수 없습니다. | 잘못된 상태전이 | FR0604~FR0610, 제안 코드 |
| ALARM | ACTION_CONTENT_REQUIRED | 400 | 조치 내용을 입력해 주세요. | 빈 조치 | FR0606, 제안 코드 |
| ALARM | FALSE_POSITIVE_NOT_ALLOWED | 422 | 현재 단계에서는 오경보로 신고할 수 없습니다. | 허용 상태 아님 | FR0607, 제안 코드 |
| USER | USER_LOGIN_ID_DUPLICATE | 409 | 이미 사용 중인 아이디입니다. | 아이디 중복 | FR1403, 제안 코드 |
| USER | USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| PART | PART_NOT_FOUND | 404 | 부품을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| SETTING | SETTING_OUT_OF_RANGE | 422 | 허용 범위 안의 값을 입력해 주세요. | 설정 범위 위반 | FR1401, 제안 코드 |
| DUTY | DUTY_GAP_NOT_ALLOWED | 422 | 당번이 없는 기간을 만들 수 없습니다. | 저장/삭제 후 공백 | FR1408, 제안 코드 |
| DUTY | DUTY_OVERLAP | 409 | 같은 기간에 이미 당번이 있습니다. | 기간 중복 | FR1408, 제안 코드 |
| KAKAO | KAKAO_NOT_CONNECTED | 422 | 카카오톡 연결이 필요합니다. | 미연결 발송 | FR1405, 제안 코드 |
| COMMON | INVALID_PERIOD | 400 | 조회 기간을 확인해 주세요. | 시작 > 종료 | 제안 코드 |
| COMMON | VALIDATION_ERROR | 400 | 입력값을 확인해 주세요. | 형식/필수값 | NFR09, 제안 코드 |

## 10. 권한 매트릭스

| 기능 | ADMIN | GENERAL | 직책 추가 조건 |
|---|:---:|:---:|---|
| 로그인/로그아웃/내 정보 | O | O | 없음 |
| 종합현황/설비/모니터링/알람/통계 | O | O | 없음 |
| 알람 확인·조치·오경보 | O | O | 담당자는 MAINTENANCE 중 선택 |
| 설비관리 기준정보 | O | O | 최신 요구사항상 시스템관리와 별도 |
| `/system/**` | O | X | 없음 |
| 알람 당번 지정 | O | X | 대상은 LINE_MANAGER |
| Node-RED 내부 수집 | X | X | 별도 내부 인증키/네트워크 정책 |

화면에서 메뉴나 버튼을 숨기더라도 Spring Security에서 동일한 권한을 검증한다.

## 11. WebSocket STOMP 명세

- 연결 URL 제안: `/ws`
- 인증: STOMP CONNECT 헤더의 Bearer JWT
- REST는 최초 조회·검색·등록·수정·상태 변경, WebSocket은 변경 통지만 담당한다.
- 아래 주소는 요구사항에 실제 토픽이 없어 모두 **제안값**이다.

| 토픽 | 목적 | 발행 조건 | 권한 | Payload 핵심 | 요구사항 | 주기/순서/중복 |
|---|---|---|---|---|---|---|
| `/topic/equipment-status` | 설비 상태 변경 | 상태 변경 시 | ADMIN, GENERAL | eventId,equipmentId,status,statusStartedAt,occurredAt | FR0302 | 이벤트 발생 시, eventId 중복 제거, equipmentId별 occurredAt 순서 |
| `/topic/measurements/{equipmentId}` | 선택 설비 최신 값 | 유효 값 저장 시 | ADMIN, GENERAL | eventId,dataItemId,value,valueType,measuredAt | FR0201, FR1501 | 수집 주기, eventId 중복 제거 |
| `/topic/alarms` | 새 알람/등급/단계 변경 | 알람 변경 시 | ADMIN, GENERAL | eventId,alarmId,eventType,grade,processStatus,occurredAt | FR0501, FR0601 | 이벤트 발생 시 |
| `/topic/alarm-counts` | 미확인 알람 수 | 미확인 수 변경 시 | ADMIN, GENERAL | eventId,unacknowledgedCount | FR0502 | 변경 시 |
| `/topic/inventory` | 재고/소진 예측 변경 | 재계산 시 | ADMIN, GENERAL | eventId,lineId,quantity,stage,remainingMinutes | FR0902, FR1501 | 계산 주기 미정 |
| `/topic/alarm-effects/{alarmId}` | 효과 확인 생산 수 | 생산 이벤트 반영 시 | ADMIN, GENERAL | eventId,alarmId,currentCount,targetCount,waiting,recurred | FR0608, FR1501 | 생산 이벤트마다 |
| `/user/queue/notifications` | 화면 알림 | 새 알람/재알림/재발/담당 지정 | ADMIN, GENERAL | notificationId,type,alarmSummary,createdAt | FR0501, FR0504, FR0505, FR0508 | 사용자별, notificationId 중복 제거 |

재연결은 지수 백오프를 제안한다. 재연결 성공 후 현재 화면의 해당 REST API를 다시 호출해 누락 이벤트를 복구한다. 메시지는 `eventId`로 중복 제거하며, 전역 순서는 보장하지 않고 동일 설비/알람의 `occurredAt`과 서버 버전으로 오래된 이벤트를 무시한다.

## 12. MQTT 명세

Spring Boot는 Mosquitto를 직접 구독하지 않는다. 데이터 흐름은 `설비/PLC -> MQTT/Modbus/OPC UA -> Node-RED -> HTTP REST -> Spring Boot`이다. 따라서 MQTT는 Node-RED 수집 구간의 내부 인터페이스이며, 실제 PLC 토픽은 IFR01에서 확정되지 않았다.

| 구분 | 제안 토픽 | 발행자 | 구독자 | QoS | 주기 | Payload | 중복/오류 |
|---|---|---|---|---:|---|---|---|
| 측정/상태/누적값 | `stf/equipment/{equipmentCode}/values` | PLC/게이트웨이 | Node-RED | 1 제안 | 항목 수집 주기 | equipmentCode,dataItemCode,valueType,value,measuredAt,messageId | messageId 중복 제거, 미등록 설비/항목은 Spring 내부 API에서 422 |
| 생산 이벤트 | `stf/line/{lineCode}/production` | PLC/게이트웨이 | Node-RED | 1 제안 | 이벤트 발생 시 | lineCode,equipmentCode,quantity,producedAt,messageId | messageId 중복 제거 |

토픽, QoS, retained, 실제 PLC 항목, 단위는 모두 추가 정의가 필요하다. Node-RED가 변환한 HTTP 요청은 `INT-001`, `INT-002`를 따른다.

## 13. 요구사항-API 추적표

| 요구사항 ID | 요구사항명 | API/처리 | 내부 처리 | WebSocket | MQTT | 반영 | 비고 |
|---|---|---|:---:|---|---|:---:|---|
| FR0101 | 로그인 | AUTH-001 |  |  |  | O |  |
| FR0102 | 로그인 유지 | 공통 인증 필터 | O |  |  | O | 토큰 저장 위치 보안 결정 필요 |
| FR0103 | 권한별 기능 제한 | Spring Security | O |  |  | O |  |
| FR0104 | 로그아웃 | AUTH-002 |  |  |  | O |  |
| FR0105 | 로그인 사용자 표시 | AUTH-003 |  |  |  | O |  |
| FR0201 | 측정값 수집 | INT-001 | O | measurements | values | O |  |
| FR0202 | 통신 끊김 감지 | 수신시각 스케줄 판정 | O | equipment-status |  | O |  |
| FR0203 | 마지막 수집 시각 | COM-001, EQP-004 |  |  |  | O |  |
| FR0204 | 통신 현황 조회 | COM-001 |  |  |  | O |  |
| FR0205 | 헤더 통신 상태 | COM-002 |  | equipment-status |  | O |  |
| FR0301 | 설비 상태 판정 | 상태 판정 서비스 | O | equipment-status |  | O |  |
| FR0302 | 설비 상태 실시간 전달 | - |  | equipment-status |  | O |  |
| FR0401 | 임계값 판정 | 알람 판정 서비스 | O |  |  | O |  |
| FR0402 | 등급 상향 | 알람 판정 서비스 | O | alarms |  | O |  |
| FR0403 | 중복 알람 방지 | 알람 판정 서비스 | O |  |  | O |  |
| FR0404 | 값 정상 복귀 판정 | 알람 판정 서비스 | O | alarms |  | O |  |
| FR0405 | 상태값 판정 | 알람 판정 서비스 | O | alarms |  | O | 화면 예시 일부 없음 |
| FR0501 | 화면 알림 | - |  | notifications |  | O |  |
| FR0502 | 미확인 알람 수 | ALM-001 |  | alarm-counts |  | O |  |
| FR0503 | 카카오톡 알림 | 카카오 발송 서비스 | O |  |  | O |  |
| FR0504 | 미확인 재알림 | 재알림 스케줄러 | O | notifications |  | O |  |
| FR0505 | 재발 알림 | 재발 처리 서비스 | O | notifications |  | O |  |
| FR0506 | 전송 실패 처리 | 카카오 재시도 서비스 | O |  |  | O |  |
| FR0507 | 전송 결과 조회 | KAK-005 |  |  |  | O |  |
| FR0508 | 담당 지정 알림 | ALM-004 | O | notifications |  | O |  |
| FR0601 | 알람 목록 조회 | ALM-001 |  | alarms |  | O |  |
| FR0602 | 경과 시간 계산 | ALM-001, ALM-003 | O |  |  | O | 화면 표시 경과는 Front 재계산 |
| FR0603 | 알람 상세 조회 | ALM-003 |  |  |  | O |  |
| FR0604 | 알람 확인과 담당 지정 | ALM-004 |  | alarms |  | O |  |
| FR0605 | 과거 조치 제시 | ALM-005 |  |  |  | O |  |
| FR0606 | 조치 기록 | ALM-006 |  | alarms |  | O |  |
| FR0607 | 오경보 신고 | ALM-007 |  | alarms |  | O | 허용 단계 Q-06 |
| FR0608 | 효과 확인 | ALM-008 | O | alarm-effects | production | O |  |
| FR0609 | 재발 처리 | 재발 처리 서비스 | O | alarms, alarm-effects |  | O | 화면 예시 없음 |
| FR0610 | 알람 종료 | 알람 종료 서비스 | O | alarms |  | O | 수동 종료 API 없음 |
| FR0611 | 처리 기록 | ALM-003 | O |  |  | O |  |
| FR0612 | 최근 종료 알람 조회 | ALM-002 |  | alarms |  | O |  |
| FR0701 | 배치도 조회 | OVW-001 |  | equipment-status |  | O |  |
| FR0703 | 공정별 설비 목록 | EQP-001~003 |  | equipment-status |  | O | 상태값 이상 예시 없음 |
| FR0704 | 설비 상세 조회 | EQP-004 |  | measurements |  | O |  |
| FR0801 | 설비와 항목 선택 | EQP-001, EQP-005 |  |  |  | O |  |
| FR0802 | 기간 선택 | MEAS-001 |  |  |  | O |  |
| FR0803 | 추세 차트 조회 | MEAS-001 |  | measurements |  | O | 상태/누적 차트 예시 없음 |
| FR0804 | 차트 값 확인 | MEAS-001 |  |  |  | O | Front 동작 |
| FR0805 | 통신 끊김 구간 | MEAS-001 |  |  |  | O |  |
| FR0901 | 재고 계산 | 재고 계산 서비스 | O | inventory | production | O |  |
| FR0902 | 재고 현황 조회 | INV-001 |  | inventory |  | O |  |
| FR0903 | 재고 소진 시간 예측 | OVW-002 | O | inventory |  | O | 기준값 Q-05 |
| FR0904 | 조립 정지 시각 예측 | OVW-002 | O | inventory |  | O |  |
| FR0905 | 복구 시각 예측 | OVW-002 | O | inventory |  | O |  |
| FR0906 | 여유 시간 판정 | OVW-002 | O | inventory |  | O |  |
| FR0907 | 예상 손실액 | OVW-002 | O | inventory | production | O | Q-01/Q-02 |
| FR0908 | 재고 추이 | INV-002 |  | inventory |  | O |  |
| FR1101 | 부품 사용량 계산 | 부품 사용량 서비스 | O |  |  | O |  |
| FR1102 | 부품 사용량 조회 | PRT-001, PRT-002 |  |  |  | O |  |
| FR1103 | 부품 교체 등록 | PRT-003, ALM-006 |  |  |  | O |  |
| FR1104 | 부품 교체 이력 | PRT-004 |  |  |  | O |  |
| FR1201 | 알람 이력 | HST-001 |  |  |  | O |  |
| FR1202 | 대응 속도 | STA-001 |  |  |  | O |  |
| FR1203 | 오경보 | STA-002 |  |  |  | O |  |
| FR1204 | 일별 알람 건수 | STA-003 |  |  |  | O |  |
| FR1205 | 설비 고장 이력 | HST-002 |  |  |  | O |  |
| FR1206 | 가동률 | STA-004 |  |  |  | O |  |
| FR1207 | 정지 시간 | STA-005 |  |  |  | O |  |
| FR1208 | 설비 순위 | STA-006 |  |  |  | O |  |
| FR1209 | 손실액 | STA-007 |  |  |  | O | 화면 없음 |
| FR1212 | 손실 방지 실적 | STA-008 |  |  |  | O | 계산식 Q-02 |
| FR1301 | 설비 조회 | MST-001 |  |  |  | O |  |
| FR1302 | 설비 등록/수정 | MST-002, MST-003 |  |  |  | O |  |
| FR1303 | 설비 사용 중지 | MST-004 |  |  |  | O |  |
| FR1304 | 라인과 배치 | MST-005~008 |  |  |  | O |  |
| FR1305 | 데이터 항목 | MST-009, MST-010 |  |  |  | O | 일부 화면 없음 |
| FR1306 | 임계값 | MST-011 |  |  |  | O |  |
| FR1307 | 장착 부품/교체 기준 | MST-012~014 |  |  |  | O |  |
| FR1401 | 판정 설정 | SYS-001, SYS-002 |  |  |  | O |  |
| FR1402 | 계산 기준값 | SYS-003, SYS-004 |  |  |  | O |  |
| FR1403 | 계정과 담당자 | USR-001~006 |  |  |  | O |  |
| FR1405 | 카카오 연결 | KAK-001~003 |  |  |  | O |  |
| FR1406 | 테스트 메시지 | KAK-004 |  |  |  | O |  |
| FR1407 | 설정 변경 이력 | AUD-001 | O |  |  | O |  |
| FR1408 | 알람 당번 | DUT-001~005 |  |  |  | O |  |
| FR1501 | 실시간 갱신 | - |  | 모든 실시간 토픽 |  | O |  |
| FR1502 | 조회 조건 처리 | 공통 페이징/필터 |  |  |  | O |  |
| FR1503 | 오류/빈 결과 | 공통 응답 |  |  |  | O | 화면 예시 없음 |
| FR1504 | 화면 간 이동 | API 식별자/링크 필드 |  |  |  | O | Front 동작 |
| FR1505 | 상태 설명 도움말 | Enum 표시명 |  |  |  | O | Front 동작 |

## 14. API 테스트 체크리스트

- [ ] 로그인 성공/실패/사용중지 계정/토큰 만료/로그아웃 후 재사용을 각각 검증한다.
- [ ] ADMIN과 GENERAL의 `/system/**` 200/403을 구분해 검증한다.
- [ ] 설비 코드와 로그인 아이디 중복이 409인지 검증한다.
- [ ] 잘못된 알람 상태 전이와 중복 확인이 409인지 검증한다.
- [ ] 알람 조치와 부품 교체가 한 트랜잭션으로 반영되는지 검증한다.
- [ ] 값 정상 복귀만으로 알람이 종료되지 않는지 검증한다.
- [ ] 통신 끊김 우선순위와 복구, 시작/종료 이력 생성을 검증한다.
- [ ] 상태값/측정값/누적값 수집 형식과 미등록 설비·항목 거부를 검증한다.
- [ ] 임계값 순서와 판정 설정 허용 범위 위반이 422인지 검증한다.
- [ ] 당번 추가/수정/삭제 후 공백과 중복 기간을 거부하는지 검증한다.
- [ ] WebSocket 재연결 후 REST 재조회로 상태가 일치하는지 검증한다.
- [ ] 카카오 실패가 알람 처리 트랜잭션을 롤백하지 않는지 검증한다.
- [ ] 페이징의 전체 건수와 정렬이 동일 조건에서 안정적인지 검증한다.
- [ ] 비밀번호, JWT, 카카오 토큰이 응답/로그/감사이력에 노출되지 않는지 검증한다.

## 15. 추가 정의가 필요한 항목

- 실제 PLC/MQTT 토픽, 데이터 항목 코드, 단위, 수집 주기
- JWT 만료시간, 저장 위치, 토큰 폐기 구현
- 목록 page/size 기본값과 최대값
- 손실 방지액·예상 손실액·평균 고장 간격의 정확한 산식
- 생산 이벤트와 재고 계산의 기준 시점 및 중복 제거 키
- “같은 설비 같은 알람”을 판별할 알람 원인 키
- 재고 주의/위험 단계 운영값
- 미확인 단계 오경보 허용 여부
- 관리자 비밀번호 초기화 전달 방식
- 사진 파일 크기·확장자·저장소·URL 만료 정책
- WebSocket 실제 endpoint/topic과 메시지 버전
- MQTT QoS/retained/재전송/오프라인 버퍼 정책

정확한 명세가 불가능한 항목은 전체 API의 40% 미만이며, 핵심 REST 계약 작성은 계속 진행할 수 있다고 판단했다.

## 16. OpenAPI 3.0.3 YAML

Spring Boot 구현의 기계 판독 기준은 `docs/openapi.yaml`이다. API 주소, Method, DTO 필드, Enum, 오류 응답을 변경할 때 이 문서와 YAML을 함께 수정한다.
