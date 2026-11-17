# STF 설비통합관제 API 명세서

- 문서 버전: 5.0.0
- 작성 기준일: 2026-11-16
- 대상 시스템: 등속조인트 생산 공정 설비통합관제 시스템
- REST Base URL: `/api/v1`
- 내부 수집 Base URL: `/internal/v1`
- 상세 계약: `docs/openapi.yaml`

> 이 문서는 요구사항정의서의 FR/NFR/IFR을 우선 기준으로 삼고, 화면설계서와 기능 변경 내역으로 보완했다. 요구사항에 없는 구현 필수값은 사용자의 2026-11-16 지시에 따라 **프로젝트 제안값**으로 확정했다. 실제 공장 수치나 현대위아의 실제 기준을 의미하지 않는다.

## 1. 첨부자료 분석 결과

### 1.1 기준 문서와 우선순위

1. 최신 요구사항정의서(2026-11-16): FR 92건, NFR 13건, IFR 4건
2. 최신 화면설계서(2026-11-16): 화면 94개
3. 최신 기능 변경 내역(2026-11-16): 추가 13건, 삭제 7건, 변경 61건
4. Jira SF5-19 댓글과 Confluence 메시지 규격: 내부 수집 형식
5. 최종 ERD 설계서 v0.3 및 Flyway V1/V2 브랜치: 저장 구조와 초기 코드

위 문서가 v4와 충돌하면 최신 요구사항·기능 변경·화면설계를 우선하고, SF5-19의 확정된 메시지 규격과 최종 ERD를 반영한다. 그 밖의 빈 값에 한해 v4의 프로젝트 제안값을 유지한다.

### 1.2 이번 명세에 반영한 변경

- 추가: 회원가입, 가입 승인·거절, 본인 비밀번호 변경, 헤더 통신 상태, 상태값 알람 판정, 담당 지정 알림, 알람 비교, 재고 상세, 손실 방지 실적, 알람 당번, 당번 캘린더, 상태 도움말
- 삭제: 현황 요약, 생산현황 2개, 기간 비교, 이력 파일 저장, 담당자 없는 라인 확인, 카카오 로그인 연결
- 변경: 직책 3종을 권한 판단 기준으로 사용, 측정값/상태값/누적값 구분, 설비 사진, 알람 4단계, 기간 선택 방식, 부품 수명 계산 기준, 계정 삭제 방식
- REST API로 만들지 않은 항목: 임계값 판정, 통신 끊김 판정, 설비 상태 판정, 중복 알람 방지, 재고 계산, 복구/손실 계산, 효과 확인과 자동 종료 등 서버 내부 자동 처리

## 2. 프로젝트 제안값 확정

| ID | 항목 | 확정한 제안값 |
|---|---|---|
| P-01 | 식별자 | 최종 ERD의 내부 식별자는 BIGINT 자동 증가, 현장 코드는 별도 문자열 |
| P-02 | 페이징 | page 0부터, 기본 size 20, 최대 100 |
| P-03 | JWT | Access Token 30분, Refresh Token 7일, Refresh Rotation 사용 |
| P-04 | 로그아웃 | Access Token jti denylist와 Refresh Token family 폐기 |
| P-05 | 로그인 기록 | 성공 시 `lastLoginAt` 갱신, 실패 시 유지 |
| P-06 | 비밀번호 | 8~64자, 영문·숫자·특수문자 각각 1개 이상 |
| P-07 | 계정 | 회원가입은 승인대기, 승인 시 활성. 초기화 시 `PASSWORD_CHANGE_REQUIRED` |
| P-08 | 수집·통신 | 설비별 묶음 메시지 3초, 마지막 수신 후 30초 초과 시 통신 끊김 |
| P-09 | 알람 판정 | 기준 이탈 3회 연속 발생, 정상 범위 3회 연속 복귀 |
| P-10 | 효과 확인 | 라인 끝 검사기 통과 20개 동안 미재발 시 효과 있음 |
| P-11 | 재고 단계 | 30분 이하 주의, 10분 이하 위험 |
| P-12 | 속도 계산 | 최근 10분 이동 구간의 생산·소비 건수로 분당 속도 계산 |
| P-13 | 같은 알람 | 종료되지 않은 동일 `dataItemId` 한 건(ERD `open_item_id` 유일 제약) |
| P-14 | 텔레그램 | 본인 연결용 일회용 토큰 10분, 발송 실패 1초 뒤 1회 재시도, 연결된 `telegram_chat_id`로 전송 |
| P-15 | WebSocket | `/ws`, STOMP heartbeat 10초, 재연결 최대 30초 지수 백오프 |
| P-16 | MQTT | QoS 1, retained false, 설비별 3초 묶음 메시지, messageId 24시간 중복 제거 |
| P-17 | 내부 수집 | `X-Internal-Api-Key`, 설비별 값 1~500개, 하나라도 미등록·형식 오류면 메시지 전체 거절 |
| P-18 | 사진 | JPG/PNG/WebP, 최대 5MB |

## 3. 문서 간 불일치 사항

| ID | 불일치 | 적용 기준 |
|---|---|---|
| C-01 | 화면 규칙은 직책 3종과 권한 2종을 분리하나 요구사항은 직책으로 권한을 판정함 | 직책 3종을 인증 기준으로 사용하고 `permission`은 직책에서 파생해 읽기 전용 제공 |
| C-02 | 사용자 요청은 최종 로그인 일시를 요구하지만 요구사항에는 없음 | 프로젝트 제안값으로 포함. 성공 시에만 갱신 |
| C-03 | 이전 명세의 생산량 API, 기간 비교, 파일 저장, 담당자 없는 라인 확인은 최신 변경내역에서 삭제됨 | API 삭제 |
| C-04 | FR0102는 새로고침 후 로그인 유지를 요구하고, 과거 사용자 결정은 매번 로그인으로 기록되어 있음 | 최신 FR0102 적용. Access Token은 프론트 메모리, Refresh Token은 HttpOnly·Secure·SameSite=Strict 쿠키에 저장 |
| C-05 | 화면에는 통신 현황이 설비관리 아래 있고 헤더에서 모든 사용자가 이동함 | 세 직책 모두 조회 허용 |
| C-06 | 기능목록의 화면 예시에는 실제처럼 보이는 온도, 유량, 금액이 있으나 요구사항은 실제 공정값을 확정하지 않음 | 예시는 API 제약/기본값으로 사용하지 않음 |
| C-07 | 공통 규칙은 비활성 우선이나 FR1303·FR1307·FR1403은 삭제를 명시함 | DELETE API를 제공하되 DB에서는 감사·이력 보존을 위해 논리 삭제 |
| C-08 | v4는 카카오·휴대전화, 최신 문서는 텔레그램 본인 연결 | 카카오 계약과 전화번호 필드를 제거하고 텔레그램 연결·전송 계약으로 교체 |
| C-09 | v4의 UUID와 최종 ERD의 BIGINT 충돌 | API 내부 ID는 int64, 설비·항목 코드는 문자열 |
| C-10 | v4의 2초 수집·15초 끊김과 SF5-19·V2의 3초·30초 충돌 | 설비별 3초 묶음, 30초 통신 끊김 적용 |
| C-11 | v4의 INT-002와 SF5-19의 누적 생산 수집 충돌 | `prod_count`를 INT-001에 포함하고 INT-002를 제거 |
| C-12 | API 상태 ACTION과 ERD 상태 ACTION_TAKEN | 공개 API는 ACTION, DB 저장값은 ACTION_TAKEN으로 변환 |

## 4. 작성 시 사용한 명시적 가정

- 식별자는 최종 ERD의 BIGINT를 사용하고 설비의 현장 식별자는 별도 `equipmentCode`로 둔다.
- 등록/수정 시각과 이벤트 시각은 ISO 8601 `date-time`으로 송수신한다.
- 직책 코드는 `SYSTEM_ADMIN`, `LINE_MANAGER`, `MAINTENANCE`로 확정한다. `permission`은 `SYSTEM_ADMIN -> ADMIN`, 나머지는 `GENERAL`로 파생한다.
- 시스템관리 `/system/**`와 당번 수정은 `SYSTEM_ADMIN`만 접근하고, 그 밖의 화면 API는 세 직책이 접근한다.
- 로그아웃은 Access Token jti denylist와 Refresh Token family 폐기로 처리한다.
- 설비 사진은 `multipart/form-data`의 바이너리 파일로 받고, 조회 시 URL을 반환한다.
- 화면의 더미 설비 코드, 수치, 사용자, 기간은 예시일 뿐 검증 규칙으로 고정하지 않는다.

## 5. API 공통 규칙

| 항목 | 규칙 |
|---|---|
| Base URL | 사용자 API `/api/v1`, Node-RED 내부 수집 `/internal/v1` |
| 인증 | `Authorization: Bearer <accessToken>`; Access 30분, Refresh 7일·Rotation |
| Content-Type | JSON은 `application/json; charset=UTF-8`, 사진 포함 등록/수정은 `multipart/form-data` |
| 이름 규칙 | URL은 복수형 명사, JSON은 camelCase |
| 시간 | ISO 8601 `date-time`, 날짜는 `date` |
| 페이징 | `page`는 0부터, `size` 기본 20·최대 100, `sort=field,asc|desc` |
| 조회 | GET, 등록 POST, 전체 수정 PUT, 부분 수정/상태 변경 PATCH |
| 삭제 | 요구사항의 삭제 동작은 DELETE. DB에서는 `deletedAt` 논리 삭제로 이력 보존 |
| 성공 코드 | 조회/수정 200, 등록 201, 본문 없는 성공 204 |
| 오류 코드 | 400 형식, 401 인증, 403 권한, 404 없음, 409 중복/상태충돌, 422 업무규칙, 500 서버오류 |
| 응답 | 성공 `{success:true,data,meta?}`, 실패 `{success:false,error:{code,message,details?,traceId?}}` |
| 추적 확장 | OpenAPI operation에 `x-api-id`, `x-requirement-ids`, `x-nfr-ids`, `x-interface-ids`, `x-business-rule-ids`, `x-test-ids`, `x-permissions` 사용 |

업무규칙 ID는 `BR-{영역}-{번호}`, 테스트 ID는 `TC-{API ID}-{번호}` 형식으로 제안한다. OpenAPI의 `x-business-rule-ids`, `x-test-ids`에 연결한다.

## 6. 도메인·변수·Enum 정의

| 구분 | 코드 | 한글 표시 | 출처 |
|---|---|---|---|
| UserRole | `SYSTEM_ADMIN` | 시스템관리자 | FR0103, FR1403 |
| UserRole | `LINE_MANAGER` | 라인 관리자 | FR0103, FR1403 |
| UserRole | `MAINTENANCE` | 보전 담당자 | FR0103, FR1403 |
| DerivedPermission | `ADMIN` | 관리자 | SYSTEM_ADMIN에서 파생 |
| DerivedPermission | `GENERAL` | 일반 사용자 | 나머지 직책에서 파생 |
| AccountStatus | `PENDING_APPROVAL` | 승인 대기 | FR0106 |
| AccountStatus | `ACTIVE` | 사용 | FR0107 |
| AccountStatus | `PASSWORD_CHANGE_REQUIRED` | 비밀번호 변경 필요 | FR0108, FR1403 |
| EquipmentStatus | `RUNNING`, `IDLE`, `CAUTION`, `FAULT`, `COMMUNICATION_LOST` | 가동, 대기, 주의, 고장, 통신 끊김 | FR0301 |
| DataValueType | `MEASUREMENT`, `STATE`, `CUMULATIVE` | 측정값, 상태값, 누적값 | FR0201 |
| AlarmGrade | `CAUTION`, `CRITICAL` | 주의, 경보 | FR0401 |
| AlarmValueStatus | `ABNORMAL`, `RECOVERED` | 이상, 값 정상 복귀 | FR0404 |
| AlarmProcessStatus | `UNACKNOWLEDGED`, `ACKNOWLEDGED`, `ACTION`, `CLOSED` | 미확인, 확인, 조치, 종료 | FR0601, FR0604~FR0610 |
| EffectStatus | `WAITING_FOR_RECOVERY`, `IN_PROGRESS`, `PASSED`, `RECURRED` | 정상 복귀 대기, 확인 중, 통과, 재발 | FR0608, FR0609 |
| AlarmResult | `EFFECTIVE`, `FALSE_POSITIVE` | 효과 있음, 오경보 | FR0607, FR0610 |
| FalsePositiveReason | `SENSOR_MALFUNCTION`, `THRESHOLD_MISMATCH`, `INSPECTION_OR_TRIAL`, `OTHER` | 센서 오작동, 기준값 불일치, 점검/시운전, 기타 | FR0607 |
| InventoryStage | `MAINTAIN`, `CAUTION`, `DANGER` | 유지, 주의, 위험 | FR0903 |
| PartLifeBasis | `OPERATING_HOURS`, `ELAPSED_DAYS` | 가동 시간, 경과 일 | FR1101, FR1307 |
| PartUsageStatus | `NORMAL`, `DUE_SOON`, `OVERDUE` | 정상, 임박, 초과 | FR1102 |
| CommunicationStatus | `CONNECTED`, `DISCONNECTED` | 연결, 끊김 | FR0204 |
| HeaderCommunicationStatus | `ALL_NORMAL`, `PARTIAL_DISCONNECTED`, `ALL_DISCONNECTED` | 전체 정상, 일부 끊김, 전체 끊김 | FR0205 |

`ThresholdDirection`과 오류코드 영문명은 개발을 위해 필요한 제안값이며 OpenAPI에 `x-proposed: true`로 표시한다. `ACTION`은 DB의 `ACTION_TAKEN`에 대응한다. 효과 확인은 별도 처리 단계가 아니라 `ACTION` 내부에서 진행한다.

## 7. 전체 API 목록

| API ID | 대분류 | 기능명 | Method | URL | 인증 | 권한 | 관련 요구사항 | 테스트 ID | 우선순위 |
|---|---|---|---|---|---|---|---|---|---|
| AUTH-001 | 인증 | 로그인 | POST | `/auth/login` | 아니오 | ANONYMOUS | FR0101, FR0102 | TC-AUTH-001-01 | P0 |
| AUTH-002 | 인증 | 토큰 갱신 | POST | `/auth/refresh` | Refresh | ANONYMOUS | FR0102 | TC-AUTH-002-01 | P0 |
| AUTH-003 | 인증 | 로그아웃 | POST | `/auth/logout` | 예 | 전체 | FR0104 | TC-AUTH-003-01 | P0 |
| AUTH-004 | 인증 | 로그인 사용자 조회 | GET | `/auth/me` | 예 | 전체 | FR0105 | TC-AUTH-004-01 | P0 |
| AUTH-005 | 인증 | 회원가입 | POST | `/auth/signups` | 아니오 | ANONYMOUS | FR0106 | TC-AUTH-005-01 | P0 |
| AUTH-006 | 인증 | 본인 비밀번호 변경 | PUT | `/auth/me/password` | 예 | 전체 | FR0108 | TC-AUTH-006-01 | P0 |
| COM-001 | 통신 | 통신 현황 조회 | GET | `/communications` | 예 | ADMIN, GENERAL | FR0203, FR0204 | 기본 규칙 | P1 |
| COM-002 | 통신 | 헤더 통신 상태 | GET | `/communications/summary` | 예 | ADMIN, GENERAL | FR0205 | 기본 규칙 | P0 |
| OVW-001 | 종합현황 | 설비 배치도 | GET | `/overview/equipment-layout` | 예 | ADMIN, GENERAL | FR0701 | 기본 규칙 | P0 |
| OVW-002 | 종합현황 | 조립 정지 예측 | GET | `/overview/assembly-stop-forecast` | 예 | ADMIN, GENERAL | FR0903~FR0907 | 기본 규칙 | P1 |
| INV-001 | 재고 | 현재 재고 | GET | `/inventory` | 예 | ADMIN, GENERAL | FR0902 | 기본 규칙 | P1 |
| INV-002 | 재고 | 재고 추이 | GET | `/inventory/history` | 예 | ADMIN, GENERAL | FR0908 | 기본 규칙 | P1 |
| INV-003 | 재고 | 라인 재고 상세 | GET | `/inventory/lines/{lineId}` | 예 | 전체 | FR0909 | TC-INV-003-01 | P1 |
| EQP-001 | 설비조회 | 라인 목록 | GET | `/lines` | 예 | ADMIN, GENERAL | FR0701, FR0703 | 기본 규칙 | P0 |
| EQP-002 | 설비조회 | 라인 공정 목록 | GET | `/lines/{lineId}/processes` | 예 | ADMIN, GENERAL | FR0703 | 기본 규칙 | P0 |
| EQP-003 | 설비조회 | 공정별 설비 | GET | `/processes/{processId}/equipment` | 예 | ADMIN, GENERAL | FR0703 | 기본 규칙 | P0 |
| EQP-004 | 설비조회 | 설비 상세 | GET | `/equipment/{equipmentId}` | 예 | ADMIN, GENERAL | FR0704 | 기본 규칙 | P0 |
| EQP-005 | 설비조회 | 설비 데이터 항목 | GET | `/equipment/{equipmentId}/data-items` | 예 | ADMIN, GENERAL | FR0801 | 기본 규칙 | P0 |
| MEAS-001 | 모니터링 | 측정 추세 | GET | `/equipment/{equipmentId}/measurements` | 예 | ADMIN, GENERAL | FR0802~FR0805 | 기본 규칙 | P0 |
| MEAS-002 | 모니터링 | 알람 비교 | GET | `/alarms/{alarmId}/comparisons` | 예 | 전체 | FR0806 | TC-MEAS-002-01 | P1 |
| ALM-001 | 알람 | 현재 알람 목록 | GET | `/alarms` | 예 | ADMIN, GENERAL | FR0601, FR0602 | 기본 규칙 | P0 |
| ALM-002 | 알람 | 최근 종료 알람 | GET | `/alarms/recent-closed` | 예 | ADMIN, GENERAL | FR0612 | 기본 규칙 | P1 |
| ALM-003 | 알람 | 알람 상세 | GET | `/alarms/{alarmId}` | 예 | ADMIN, GENERAL | FR0603, FR0611 | 기본 규칙 | P0 |
| ALM-004 | 알람 | 확인 및 담당 지정 | PATCH | `/alarms/{alarmId}/acknowledgement` | 예 | ADMIN, GENERAL | FR0604, FR0508 | 기본 규칙 | P0 |
| ALM-005 | 알람 | 과거 조치 추천 | GET | `/alarms/{alarmId}/recommended-actions` | 예 | ADMIN, GENERAL | FR0605 | 기본 규칙 | P1 |
| ALM-006 | 알람 | 조치 기록 | POST | `/alarms/{alarmId}/actions` | 예 | ADMIN, GENERAL | FR0606 | 기본 규칙 | P0 |
| ALM-007 | 알람 | 오경보 신고 | POST | `/alarms/{alarmId}/false-positive` | 예 | ADMIN, GENERAL | FR0607 | 기본 규칙 | P0 |
| ALM-008 | 알람 | 효과 확인 상태 | GET | `/alarms/{alarmId}/effect` | 예 | ADMIN, GENERAL | FR0608~FR0610 | 기본 규칙 | P1 |
| HST-001 | 이력 | 알람 이력 | GET | `/alarm-histories` | 예 | ADMIN, GENERAL | FR1201 | 기본 규칙 | P1 |
| HST-002 | 이력 | 설비 고장 이력 | GET | `/equipment/{equipmentId}/fault-histories` | 예 | ADMIN, GENERAL | FR1205 | 기본 규칙 | P1 |
| PRT-001 | 부품 | 부품 사용량 | GET | `/parts/usage` | 예 | ADMIN, GENERAL | FR1102 | 기본 규칙 | P1 |
| PRT-002 | 부품 | 설비 장착 부품 | GET | `/equipment/{equipmentId}/parts` | 예 | ADMIN, GENERAL | FR1102 | 기본 규칙 | P1 |
| PRT-003 | 부품 | 부품 교체 등록 | POST | `/equipment/{equipmentId}/parts/{partId}/replacements` | 예 | ADMIN, GENERAL | FR1103 | 기본 규칙 | P1 |
| PRT-004 | 부품 | 부품 교체 이력 | GET | `/equipment/{equipmentId}/parts/{partId}/replacements` | 예 | ADMIN, GENERAL | FR1104 | 기본 규칙 | P1 |
| STA-001 | 통계 | 대응 속도 | GET | `/statistics/alarm-response` | 예 | ADMIN, GENERAL | FR1202 | 기본 규칙 | P1 |
| STA-002 | 통계 | 오경보 | GET | `/statistics/false-positives` | 예 | ADMIN, GENERAL | FR1203 | 기본 규칙 | P1 |
| STA-003 | 통계 | 일별 알람 건수 | GET | `/statistics/daily-alarms` | 예 | ADMIN, GENERAL | FR1204 | 기본 규칙 | P1 |
| STA-004 | 통계 | 가동률 | GET | `/statistics/availability` | 예 | ADMIN, GENERAL | FR1206 | 기본 규칙 | P1 |
| STA-005 | 통계 | 정지 시간 | GET | `/statistics/downtime` | 예 | ADMIN, GENERAL | FR1207 | 기본 규칙 | P1 |
| STA-006 | 통계 | 설비 순위 | GET | `/statistics/equipment-rankings` | 예 | ADMIN, GENERAL | FR1208 | 기본 규칙 | P1 |
| STA-007 | 통계 | 손실액 | GET | `/statistics/losses` | 예 | ADMIN, GENERAL | FR1209 | 기본 규칙 | P2 |
| STA-008 | 통계 | 손실 방지 실적 | GET | `/statistics/loss-avoidance` | 예 | ADMIN, GENERAL | FR1212 | 기본 규칙 | P2 |
| MST-001~004 | 기준정보 | 설비 조회/등록/수정/삭제 | 복수 | `/master/equipment...` | 예 | 전체 | FR1301~FR1303 | TC-MST-001~004 | P0 |
| MST-005~008 | 기준정보 | 라인·배치 관리 | 복수 | `/master/lines...` | 예 | ADMIN, GENERAL | FR1304 | 기본 규칙 | P1 |
| MST-009~013 | 기준정보 | 배치·데이터 항목·임계값 | 복수 | `/master/.../data-items...` | 예 | ADMIN, GENERAL | FR1304~FR1306 | 기본 규칙 | P0 |
| MST-014~017 | 기준정보 | 장착 부품 조회/등록/수정/삭제 | 복수 | `/master/.../parts...` | 예 | 전체 | FR1307 | TC-MST-014~017 | P1 |
| SYS-001~004 | 시스템 | 판정/계산 설정 조회·수정 | 복수 | `/system/settings/...` | 예 | ADMIN | FR1401, FR1402 | 기본 규칙 | P1 |
| USR-001~005 | 시스템 | 계정 조회·수정·삭제·초기화 | 복수 | `/system/users...` | 예 | SYSTEM_ADMIN | FR1403 | TC-USR-001~005 | P0 |
| APR-001~003 | 시스템 | 가입 신청 목록·승인·거절 | 복수 | `/system/signup-requests...` | 예 | SYSTEM_ADMIN | FR0107 | TC-APR-001~003 | P0 |
| TLG-001~004 | 텔레그램 | 본인 연결 URL·상태·해제·시험 발송 | 복수 | `/telegram/...` | 예 | 전체 | FR1405 | TC-TLG-001~004 | P1 |
| TLG-005~006 | 시스템 | 관리자 시험 발송·전송결과 | 복수 | `/system/telegram/...` | 예 | SYSTEM_ADMIN | FR0507, FR1406 | TC-TLG-005~006 | P2 |
| AUD-001 | 시스템 | 변경 이력 | GET | `/system/change-histories` | 예 | ADMIN | FR1407 | 기본 규칙 | P1 |
| DUT-001~006 | 담당자 | 알람 당번 CRUD·캘린더 | 복수 | `/system/alarm-duty-schedules...`, `/alarm-duty-schedules/calendar` | 예 | 조회 전체/수정 SYSTEM_ADMIN | FR1408, FR1409 | TC-DUT-001~006 | P1 |
| INT-001 | 내부수집 | 설비 값 수집 | POST | `/internal/v1/measurements` | 내부키 | Node-RED | FR0201, IFR01 | 기본 규칙 | P0 |

테스트 ID가 `기본 규칙`인 API는 `TC-{API ID}-01`을 기본 정상 시나리오, `TC-{API ID}-02`를 인증·검증 오류 시나리오로 확정한다.

## 8. API 상세 명세

모든 요청은 공통 인증/오류/시간/페이징 규칙을 적용하며, 필드의 타입·필수·nullable·길이·enum·단위·예시·검증·출처는 OpenAPI `components.schemas`를 기준으로 한다.

### 8.1 인증·회원가입·통신

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| AUTH-001 | `loginId` 4~20자, `password` 8~64자 | Access/Refresh 토큰, 사용자, `lastLoginAt` | 승인된 계정만 허용, 성공 시 최종 로그인 갱신 | AUTH_INVALID_CREDENTIALS, AUTH_APPROVAL_PENDING | 실패 시 최종 로그인 유지, 비밀정보 로그 금지 |
| AUTH-002 | Refresh Token cookie | 새 Access/Refresh Token | Rotation 후 이전 Refresh 폐기 | AUTH_REFRESH_TOKEN_INVALID | 재사용 탐지 시 token family 전체 폐기 |
| AUTH-003 | Bearer + Refresh cookie | 204 | Access jti와 Refresh family 폐기 | AUTH_TOKEN_INVALID | 멱등 처리 |
| AUTH-004 | Bearer | `CurrentUser` | 토큰 사용자 재조회 | AUTH_TOKEN_INVALID | 파생 permission 포함 |
| AUTH-005 | `loginId,password,name` | 신청 ID·승인대기 상태·텔레그램 연결 URL | 아이디 중복·형식 검증, 비밀번호 해시 저장 | USER_LOGIN_ID_DUPLICATE, VALIDATION_ERROR | 직책은 신청자가 고르지 않음, 연결 URL 10분 유효 |
| AUTH-006 | `currentPassword,newPassword` | 204 | 현재 비밀번호 확인, 정책 검증, 모든 기존 토큰 폐기 | AUTH_INVALID_CREDENTIALS, PASSWORD_POLICY_VIOLATION | 초기화 계정도 동일 API 사용 |
| COM-001 | lineId, status, page, size, sort | 통신 요약과 설비별 연결/마지막 수신/오늘 끊김 | 끊김 많은 순 정렬 가능 | LINE_NOT_FOUND | 화면 위치는 Q-07 |
| COM-002 | 없음 | 전체/끊긴 설비 수, 헤더 상태, 전체 끊김 시작시각 | 전체 정상/일부/전체 끊김 판정 | - | WebSocket으로 변경 통지 |

### 8.2 종합현황·설비·모니터링·재고

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| OVW-001 | 선택 lineId(선택) | 라인·공정 순서·설비 상태·알람 수·주요 값·상태별 대수 | 등록 기준정보 순서 사용 | LINE_NOT_FOUND | 층 정보는 삭제됨 |
| OVW-002 | 없음 | 라인별 재고, 순소비속도, 소진시간, 정지예상, 복구예상, 여유, 예상손실 | 과거 수리 평균과 자동 생산률 사용 | FORECAST_DATA_INSUFFICIENT | 계산식 일부 Q-01/Q-02 |
| INV-001 | 없음 | 라인별 재고·분당 소비·분당 생산 | 실시간 반영 | - | 최대 재고 비율 삭제 |
| INV-002 | from, to, lineIds | 시각별 라인 재고 | from <= to | INVALID_PERIOD | 긴 기간 집계 간격은 제안값 |
| INV-003 | lineId | 현재·적정 재고, 입고/소비 속도, 사이클 타임, 소진 예상 | 최근 10분 이동 구간 계산 | LINE_NOT_FOUND, INVENTORY_DATA_INSUFFICIENT | 적정 재고는 판정 설정값 |
| EQP-001~005 | lineId/processId/equipmentId | 화면 선택 목록과 설비 상세 | 존재/활성 상태 검증 | LINE/PROCESS/EQUIPMENT_NOT_FOUND | 상태값·누적값을 측정값과 분리 반환 |
| MEAS-001 | dataItemId, from, to | 시각별 값, 기준선, 알람 시점, 통신 끊김 구간 | 설비 소속 항목만 허용 | DATA_ITEM_NOT_FOUND, INVALID_PERIOD | 상태값은 계단선, 누적값은 막대 표시용 타입 제공 |
| MEAS-002 | alarmId, occurrenceIds(최대 5), beforeMinutes=30, afterMinutes=30 | 발생별 시계열·최고값·수리시간·조치·결과 | 같은 `alarmKey`만 비교 | ALARM_NOT_FOUND, ALARM_COMPARISON_MISMATCH | 기준 시각은 각 발생의 occurredAt |

### 8.3 알람 처리

| API ID | 요청 | 정상 응답 | 검증·상태 변경 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| ALM-001 | lineId, equipmentId, processStatus, grade, page, size, sort | 미종료 알람 페이지와 발생/미확인 수 | 미확인 → 경보 → 최신 순 기본정렬 | INVALID_FILTER | 새 알람/상태 변경 WebSocket 반영 |
| ALM-002 | limit(기본 20, 최대 100) | 최근 종료 알람 | 최신 종료 순 | - | 종료 시각 기준 안정 정렬 |
| ALM-003 | alarmId | 요약, 현재값, 단계시각, 추세, 처리기록, 재발, 효과 | 존재 확인 | ALARM_NOT_FOUND | 값 정상 복귀와 처리 종료를 구분 |
| ALM-004 | assigneeUserId | 확인자/확인시각/담당자/상태 | 미확인에서만 가능, 담당자는 보전 담당자 | ALARM_ALREADY_ACKNOWLEDGED, ASSIGNEE_NOT_MAINTENANCE | 확인자와 담당자는 다를 수 있음 |
| ALM-005 | alarmId | 조치내용, 효과/전체 횟수, 평균수리시간 | `equipmentId+dataItemId+conditionType` 동일 기준 | - | 최근 종료 20건 사용 |
| ALM-006 | cause, actionContent, replacedPartId? | 조치시각, 상태 | 조치내용 필수, 교체 부품 입력 시 교체 이력 동시 등록 | ALARM_STATE_CONFLICT, ACTION_CONTENT_REQUIRED | 트랜잭션 처리 |
| ALM-007 | reason, detail? | 종료상태와 오경보 결과 | 허용 상태와 1회성 검증 | FALSE_POSITIVE_NOT_ALLOWED | 미확인 단계 허용 여부 Q-06 |
| ALM-008 | alarmId | 목표/현재 생산 수, 대기, 재발, 상태 | 자동 계산 결과만 조회 | - | 수동 통과/종료 API 없음 |

### 8.4 부품·이력·통계

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| PRT-001~004 | line/process/equipment/part/period | 사용량, 교체기준, 잔여, 예상일, 교체이력 | 수명 계산 기준별 계산, 등록 시 사용량 0 초기화 | PART_NOT_FOUND, REQUIRED_FIELD_MISSING | 작업자는 인증 사용자로 자동 기록 |
| HST-001 | 기간, 라인, 설비, 등급, 결과, 페이지, 정렬 | 종료 알람과 전체 건수 | 종료 건만 조회 | INVALID_PERIOD | 읽기 전용 상세 연결 |
| HST-002 | equipmentId, 기간 | 고장 이력, 월 건수, 평균수리, 평균고장간격 | 진행 중 고장 제외 | EQUIPMENT_NOT_FOUND | 평균고장간격은 연속 고장 발생시각 차이의 산술평균 |
| STA-001~008 | 기간, 라인, 정렬기준 | 각 통계 집계 | 원천 이력 기반 계산 | INVALID_PERIOD, STATISTICS_DATA_INSUFFICIENT | 실제 손실액과 예상 손실액 구분 |

### 8.5 기준정보와 시스템관리

| API ID | 요청 | 정상 응답 | 검증·업무 처리 | 주요 오류 | 구현 주의 |
|---|---|---|---|---|---|
| MST-001~004 | 설비 기본/설치/통신/사진 | 설비 기준정보 | 코드 중복 금지, 등록 후 코드 수정 금지, 발생 알람 있으면 삭제 거부 | EQUIPMENT_CODE_DUPLICATE, EQUIPMENT_HAS_ACTIVE_ALARM | 논리 삭제, 알람·교체 이력 보존 |
| MST-005~008 | 라인명·표시순서·설비 배치 | 라인/배치 | 설비 존재 라인 삭제 거부 | LINE_HAS_EQUIPMENT | 순서 변경은 일괄 원자 처리 |
| MST-009~011 | 값 종류별 항목/임계값 | 항목과 통계 참고값 | 측정/상태/누적 조건 검증, 주의가 경보보다 먼저 | INVALID_THRESHOLD_ORDER | 임계값 저장 후 다음 판정부터 적용 |
| MST-014~017 | 부품명·규격·관리번호·수명기준·교체기준 | 장착부품 기준정보 | 삭제 시 과거 교체 기록 유지 | PART_NOT_FOUND | 논리 삭제, 단위는 수명 기준과 일치 |
| SYS-001~004 | 판정 5개 설정/개당 금액 | 현재 설정 | 허용범위 검증, 변경 이력 자동 기록 | SETTING_OUT_OF_RANGE | 화면 예시 범위는 요구사항 기반이나 운영값은 DB 설정 |
| USR-001~005 | 이름/아이디/직책 | 계정 목록/상세 | SYSTEM_ADMIN 전용, 삭제는 논리 삭제 | USER_NOT_FOUND, USER_SELF_DELETE_FORBIDDEN | 텔레그램 연결 여부·시각 표시 |
| APR-001~003 | 신청 ID, 승인 직책 | 승인대기 목록/승인 계정/204 | 승인대기에서만 처리, 거절은 신청 삭제 | SIGNUP_REQUEST_STATE_CONFLICT | 승인 결과 텔레그램 실패는 승인 롤백 안 함. 목록 항목은 `telegramLink` 없이 `telegramConnected`를 반환(연결 토큰은 해시만 저장하고 10분 뒤 만료). 승인·거절은 변경 이력(ACCOUNT)에 같은 트랜잭션으로 기록. 이미 거절되어 삭제된 신청은 404, 이미 승인된 신청은 409 |
| TLG-001~004 | 본인 | 연결 URL·연결 상태·해제·시험 발송 | 봇 `/start <token>`으로 연결, 토큰 10분·1회용 | TELEGRAM_NOT_CONNECTED | 본인만 연결·해제 |
| TLG-005~006 | 사용자/날짜/전송결과 | 시험 발송·당일 집계·수신자별 건수·발송 목록 | 연결된 계정만 발송, 실패 1초 후 1회 재시도 | TELEGRAM_DELIVERY_FAILED | 실패 사유 기록, 알람 처리와 트랜잭션 분리 |
| AUD-001 | 기간, 항목종류, 검색어, 페이지 | 변경시각·변경자·항목·이전/변경값 | 저장 시 자동 기록 | INVALID_PERIOD | 비밀번호/토큰 값 기록 금지 |
| DUT-001~006 | 시작일·종료일·보전 담당자 목록 | 목록·현재·월별 캘린더 | 기간 공백 금지, 보전 담당자 1명 이상, 수정은 SYSTEM_ADMIN | DUTY_GAP_NOT_ALLOWED, DUTY_OVERLAP | 삭제는 마지막 예정 일정만 허용 |

## 9. 오류코드 정의

| 영역 | 오류코드 | HTTP | 사용자 메시지 | 발생 조건 | 출처/구분 |
|---|---|---:|---|---|---|
| AUTH | AUTH_INVALID_CREDENTIALS | 401 | 아이디 또는 비밀번호를 확인해 주세요. | 불일치 | FR0101, 제안 코드 |
| AUTH | AUTH_APPROVAL_PENDING | 403 | 가입 승인 대기 중입니다. | 승인대기 계정 로그인 | FR0101, FR0106, 제안 코드 |
| AUTH | AUTH_TOKEN_INVALID | 401 | 다시 로그인해 주세요. | 누락/만료/폐기 토큰 | FR0102, 제안 코드 |
| AUTH | AUTH_REFRESH_TOKEN_INVALID | 401 | 다시 로그인해 주세요. | Refresh 만료·재사용 | FR0102, 제안 코드 |
| AUTH | PASSWORD_POLICY_VIOLATION | 400 | 비밀번호 형식을 확인해 주세요. | 8~64자 조합 규칙 위반 | FR0106, FR0108, 제안 코드 |
| AUTH | AUTH_PERMISSION_DENIED | 403 | 접근 권한이 없습니다. | 권한 부족 | FR0103, 제안 코드 |
| LINE | LINE_NOT_FOUND | 404 | 라인을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| LINE | LINE_HAS_EQUIPMENT | 422 | 설비가 있는 라인은 삭제할 수 없습니다. | 삭제 규칙 | FR1304, 제안 코드 |
| EQUIPMENT | EQUIPMENT_NOT_FOUND | 404 | 설비를 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| EQUIPMENT | EQUIPMENT_CODE_DUPLICATE | 409 | 이미 사용 중인 설비 코드입니다. | 코드 중복 | FR1302, 제안 코드 |
| EQUIPMENT | EQUIPMENT_HAS_ACTIVE_ALARM | 422 | 발생 중인 알람이 있어 삭제할 수 없습니다. | 삭제 규칙 | FR1303, 제안 코드 |
| SENSOR | DATA_ITEM_NOT_FOUND | 404 | 데이터 항목을 찾을 수 없습니다. | 항목 없음 | 제안 코드 |
| SENSOR | INVALID_THRESHOLD_ORDER | 422 | 주의·경보 기준 순서를 확인해 주세요. | 기준 역전 | FR1306, 제안 코드 |
| ALARM | ALARM_NOT_FOUND | 404 | 알람을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| ALARM | ALARM_ALREADY_ACKNOWLEDGED | 409 | 이미 확인된 알람입니다. | 중복 확인 | FR0604, 제안 코드 |
| ALARM | ALARM_STATE_CONFLICT | 409 | 현재 단계에서는 처리할 수 없습니다. | 잘못된 상태전이 | FR0604~FR0610, 제안 코드 |
| ALARM | ACTION_CONTENT_REQUIRED | 400 | 조치 내용을 입력해 주세요. | 빈 조치 | FR0606, 제안 코드 |
| ALARM | FALSE_POSITIVE_NOT_ALLOWED | 422 | 현재 단계에서는 오경보로 신고할 수 없습니다. | 허용 상태 아님 | FR0607, 제안 코드 |
| USER | USER_LOGIN_ID_DUPLICATE | 409 | 이미 사용 중인 아이디입니다. | 아이디 중복 | FR1403, 제안 코드 |
| USER | USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| USER | SIGNUP_REQUEST_STATE_CONFLICT | 409 | 이미 처리된 가입 신청입니다. | 승인·거절 동시 처리 | FR0107, 제안 코드 |
| USER | USER_SELF_DELETE_FORBIDDEN | 422 | 자신의 계정은 삭제할 수 없습니다. | 관리자 자기 삭제 | FR1403, 제안 코드 |
| PART | PART_NOT_FOUND | 404 | 부품을 찾을 수 없습니다. | ID 없음 | 제안 코드 |
| SETTING | SETTING_OUT_OF_RANGE | 422 | 허용 범위 안의 값을 입력해 주세요. | 설정 범위 위반 | FR1401, 제안 코드 |
| DUTY | DUTY_GAP_NOT_ALLOWED | 422 | 당번이 없는 기간을 만들 수 없습니다. | 저장/삭제 후 공백 | FR1408, 제안 코드 |
| DUTY | DUTY_OVERLAP | 409 | 같은 기간에 이미 당번이 있습니다. | 기간 중복 | FR1408, 제안 코드 |
| TELEGRAM | TELEGRAM_NOT_CONNECTED | 422 | 텔레그램 연결이 필요합니다. | 미연결 계정 시험 발송 | FR1405, 제안 코드 |
| TELEGRAM | TELEGRAM_DELIVERY_FAILED | 422 | 메시지 발송에 실패했습니다. | 재시도까지 실패 | FR0506, FR1406, 제안 코드 |
| INVENTORY | INVENTORY_DATA_INSUFFICIENT | 422 | 재고 계산에 필요한 데이터가 부족합니다. | 최근 10분 데이터 부족 | FR0901~FR0909, 제안 코드 |
| COMMON | INVALID_PERIOD | 400 | 조회 기간을 확인해 주세요. | 시작 > 종료 | 제안 코드 |
| COMMON | VALIDATION_ERROR | 400 | 입력값을 확인해 주세요. | 형식/필수값 | NFR09, 제안 코드 |

## 10. 권한 매트릭스

| 기능 | SYSTEM_ADMIN | LINE_MANAGER | MAINTENANCE | 추가 조건 |
|---|:---:|:---:|:---:|---|
| 로그인/로그아웃/내 정보 | O | O | O | 승인된 계정 |
| 종합현황/설비/모니터링/알람/통계 | O | O | O | 없음 |
| 알람 확인·조치·오경보 | O | O | O | 조치 담당자는 오늘 당번 중 선택 |
| 설비관리 기준정보 | O | O | O | 없음 |
| `/system/**` | O | X | X | SYSTEM_ADMIN 전용 |
| 당번 캘린더 조회 | O | O | O | 없음 |
| 당번 등록·수정·삭제 | O | X | X | 대상은 MAINTENANCE 1명 이상 |
| Node-RED 내부 수집 | X | X | X | 내부 API Key |

화면에서 메뉴나 버튼을 숨기더라도 Spring Security에서 동일한 권한을 검증한다.

## 11. WebSocket STOMP 명세

- 연결 URL: `/ws`
- 인증: STOMP CONNECT 헤더의 Bearer JWT
- REST는 최초 조회·검색·등록·수정·상태 변경, WebSocket은 변경 통지만 담당한다.
- 아래 주소는 프로젝트 제안값으로 확정한다.

| 토픽 | 목적 | 발행 조건 | 권한 | Payload 핵심 | 요구사항 | 주기/순서/중복 |
|---|---|---|---|---|---|---|
| `/topic/equipment-status` | 설비 상태 변경 | 상태 변경 시 | 전체 | eventId,sequence,equipmentId,status,statusStartedAt,occurredAt | FR0302 | 이벤트 발생 시, eventId 중복 제거 |
| `/topic/measurements/{equipmentId}` | 선택 설비 최신 값 | 유효 값 저장 시 | 전체 | eventId,sequence,dataItemId,value,valueType,measuredAt | FR0201, FR1501 | 3초 수집, eventId 중복 제거 |
| `/topic/alarms` | 새 알람/등급/단계 변경 | 알람 변경 시 | 전체 | eventId,sequence,alarmId,eventType,grade,processStatus,occurredAt | FR0501, FR0601 | 이벤트 발생 시 |
| `/topic/alarm-counts` | 미확인 알람 수 | 미확인 수 변경 시 | 전체 | eventId,sequence,unacknowledgedCount | FR0502 | 변경 시 |
| `/topic/inventory` | 재고/소진 예측 변경 | 재계산 시 | 전체 | eventId,sequence,lineId,quantity,stage,remainingMinutes | FR0902, FR1501 | 10초 |
| `/topic/alarm-effects/{alarmId}` | 효과 확인 생산 수 | 생산 이벤트 반영 시 | 전체 | eventId,sequence,alarmId,currentCount,targetCount,effectStatus | FR0608, FR1501 | 생산 이벤트마다 |
| `/user/queue/notifications` | 화면 알림 | 새 알람/재알림/재발/담당 지정 | 전체 | notificationId,type,alarmSummary,createdAt | FR0501, FR0504, FR0505, FR0508 | 사용자별, notificationId 중복 제거 |

서버·클라이언트 heartbeat는 10초다. 재연결은 1, 2, 4, 8, 16, 30초 지수 백오프를 사용한다. 재연결 성공 후 현재 화면의 REST API를 다시 호출한다. 메시지는 `eventId`로 중복 제거하고 집계 키별 `sequence`가 현재 값보다 작거나 같으면 무시한다.

## 12. MQTT 명세

Spring Boot는 Mosquitto를 직접 구독하지 않는다. 데이터 흐름은 `설비/PLC -> MQTT/Modbus/OPC UA -> Node-RED -> HTTP REST -> Spring Boot`이다. 따라서 MQTT는 Node-RED 수집 구간의 내부 인터페이스이며, 실제 PLC 토픽은 IFR01에서 확정되지 않았다.

| 구분 | 제안 토픽 | 발행자 | 구독자 | QoS | 주기 | Payload | 중복/오류 |
|---|---|---|---|---:|---|---|---|
| 측정/상태/누적값 | `stf/equipment/{equipmentCode}/values` | Node-RED 가상설비 | Node-RED 수집 플로우 | 1 | 3초 | messageId,equipmentCode,measuredAt,values[] | retained=false, messageId 24시간 중복 제거 |

Payload는 UTF-8 JSON이다. 필수 필드 누락은 dead-letter 토픽 `stf/dlq/{sourceTopic}`으로 원문과 오류를 발행하고 저장하지 않는다. Node-RED는 설비별 묶음 메시지의 `run_signal`(STATE), `prod_count`(CUMULATIVE), 센서 측정값을 `INT-001`에 함께 보낸다. `prod_count` 감소 시 새 값부터 차이를 계산하고, 통신 끊김은 마지막 수신 후 30초 초과로 판정한다. 등록되지 않은 항목 또는 값 종류 불일치가 하나라도 있으면 전체 메시지를 422로 거절한다. 내부 키는 `INTERNAL_API_KEY` 환경변수에서 주입하며 원문을 저장·기록하지 않는다.

## 13. 요구사항-API 추적표

| 요구사항 ID | 요구사항명 | API/처리 | 내부 처리 | WebSocket | MQTT | 반영 | 비고 |
|---|---|---|:---:|---|---|:---:|---|
| FR0101 | 로그인 | AUTH-001 |  |  |  | O |  |
| FR0102 | 로그인 유지 | AUTH-001, AUTH-002, 공통 인증 필터 | O |  |  | O | Access 30분, Refresh 7일 |
| FR0103 | 권한별 기능 제한 | Spring Security | O |  |  | O |  |
| FR0104 | 로그아웃 | AUTH-003 |  |  |  | O |  |
| FR0105 | 로그인 사용자 표시 | AUTH-004 |  |  |  | O |  |
| FR0106 | 회원가입 | AUTH-005 |  |  |  | O | 승인대기 저장 |
| FR0107 | 가입 승인 | APR-001~003 |  | notifications |  | O | 연결된 계정은 텔레그램 발송 |
| FR0108 | 비밀번호 변경 | AUTH-006, USR-005 |  |  |  | O | 초기화 후 강제 변경 포함 |
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
| FR0503 | 텔레그램 알림 | 텔레그램 발송 서비스 | O |  |  | O | 연결된 계정에만 발송 |
| FR0504 | 미확인 재알림 | 재알림 스케줄러 | O | notifications |  | O |  |
| FR0505 | 재발 알림 | 재발 처리 서비스 | O | notifications |  | O |  |
| FR0506 | 전송 실패 처리 | 텔레그램 재시도 서비스 | O |  |  | O |  |
| FR0507 | 전송 결과 조회 | TLG-006 |  |  |  | O |  |
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
| FR0806 | 알람 비교 | MEAS-002 |  |  |  | O | 같은 alarmKey 최근 발생 비교 |
| FR0901 | 재고 계산 | 재고 계산 서비스 | O | inventory | production | O |  |
| FR0902 | 재고 현황 조회 | INV-001 |  | inventory |  | O |  |
| FR0903 | 재고 소진 시간 예측 | OVW-002 | O | inventory |  | O | 기준값 Q-05 |
| FR0904 | 조립 정지 시각 예측 | OVW-002 | O | inventory |  | O |  |
| FR0905 | 복구 시각 예측 | OVW-002 | O | inventory |  | O |  |
| FR0906 | 여유 시간 판정 | OVW-002 | O | inventory |  | O |  |
| FR0907 | 예상 손실액 | OVW-002 | O | inventory | production | O | Q-01/Q-02 |
| FR0908 | 재고 추이 | INV-002 |  | inventory |  | O |  |
| FR0909 | 재고 상세 | INV-003 |  | inventory |  | O | 적정 재고와 사이클 타임 포함 |
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
| FR1303 | 설비 삭제 | MST-004 |  |  |  | O | 논리 삭제, 이력 보존 |
| FR1304 | 라인과 배치 | MST-005~009 |  |  |  | O |  |
| FR1305 | 데이터 항목 | MST-010~012 |  |  |  | O | 일부 화면 없음 |
| FR1306 | 임계값 | MST-013 |  |  |  | O |  |
| FR1307 | 장착 부품/교체 기준 | MST-014~017 |  |  |  | O |  |
| FR1401 | 판정 설정 | SYS-001, SYS-002 |  |  |  | O |  |
| FR1402 | 계산 기준값 | SYS-003, SYS-004 |  |  |  | O |  |
| FR1403 | 계정 관리 | USR-001~005 |  |  |  | O | 등록은 회원가입·승인으로 처리 |
| FR1405 | 텔레그램 본인 연결 | TLG-001~004 |  |  |  | O | 연결 URL/QR 10분 |
| FR1406 | 테스트 메시지 | TLG-005 |  |  |  | O | 연결된 계정만 |
| FR1407 | 설정 변경 이력 | AUD-001 | O |  |  | O |  |
| FR1408 | 알람 당번 | DUT-001~005 |  |  |  | O |  |
| FR1409 | 당번 캘린더 | DUT-006 |  |  |  | O | 모든 직책 조회 |
| FR1501 | 실시간 갱신 | - |  | 모든 실시간 토픽 |  | O |  |
| FR1502 | 조회 조건 처리 | 공통 페이징/필터 |  |  |  | O |  |
| FR1503 | 오류/빈 결과 | 공통 응답 |  |  |  | O | 화면 예시 없음 |
| FR1504 | 화면 간 이동 | API 식별자/링크 필드 |  |  |  | O | Front 동작 |
| FR1505 | 상태 설명 도움말 | Enum 표시명 |  |  |  | O | Front 동작 |

## 14. API 테스트 체크리스트

- [ ] 로그인 성공/실패/승인대기/비밀번호 변경 필요 계정/토큰 만료/로그아웃 후 재사용을 각각 검증한다.
- [ ] `SYSTEM_ADMIN`과 다른 직책의 시스템 관리 API 200/403을 구분하고 당번 조회 예외를 검증한다.
- [ ] 설비 코드와 로그인 아이디 중복이 409인지 검증한다.
- [ ] 잘못된 알람 상태 전이와 중복 확인이 409인지 검증한다.
- [ ] 알람 조치와 부품 교체가 한 트랜잭션으로 반영되는지 검증한다.
- [ ] 값 정상 복귀만으로 알람이 종료되지 않는지 검증한다.
- [ ] 통신 끊김 우선순위와 복구, 시작/종료 이력 생성을 검증한다.
- [ ] 상태값/측정값/누적값 수집 형식과 미등록 설비·항목 거부를 검증한다.
- [ ] 임계값 순서와 판정 설정 허용 범위 위반이 422인지 검증한다.
- [ ] 당번 추가/수정/삭제 후 공백과 중복 기간을 거부하는지 검증한다.
- [ ] WebSocket 재연결 후 REST 재조회로 상태가 일치하는지 검증한다.
- [ ] 텔레그램 실패가 알람 처리 트랜잭션을 롤백하지 않는지 검증한다.
- [ ] 페이징의 전체 건수와 정렬이 동일 조건에서 안정적인지 검증한다.
- [ ] 비밀번호, JWT, 텔레그램 봇 토큰·chat ID가 응답/로그/감사이력에 노출되지 않는지 검증한다.

## 15. 운영 전 교체 가능한 프로젝트 제안값

- 실제 PLC가 연결되기 전까지 MQTT 토픽과 데이터 항목 코드는 본 문서의 제안값을 사용한다.
- 손실 방지액은 `정지 전 복구 건수의 예상 평균정지시간 × 시간당 생산량 × 개당 금액` 합계로 계산한다.
- 예상 손실액은 `max(0, 복구예상시각-정지예상시각) × 시간당 생산량 × 개당 금액`으로 계산한다.
- 미확인 단계에서는 오경보 신고를 허용하지 않고 확인 단계부터 허용한다.
- 관리자 비밀번호 초기화는 12자리 임시 비밀번호를 생성해 연결된 계정의 텔레그램으로 1회 전달하고 30분 뒤 만료한다. 미연결 계정의 전달 방법은 계정 관리 구현 전에 별도 계약으로 확정한다.
- 사진은 JPG/PNG/WebP 5MB 이하로 받고 객체 저장소의 서명 URL은 10분 유효하다.
- 운영 합의로 값을 바꿀 때 요구사항정의서, 이 문서, OpenAPI, 테스트를 같은 변경으로 갱신한다.

## 16. OpenAPI 3.0.3 YAML

Spring Boot 구현의 기계 판독 기준은 `docs/openapi.yaml`이다. API 주소, Method, DTO 필드, Enum, 오류 응답을 변경할 때 이 문서와 YAML을 함께 수정한다.

## 17. v5 구현 제안값과 경계

- 텔레그램은 봇과의 개인 대화만 사용한다. 봇 deep link의 `/start <token>`은 256비트 난수이며 Redis에 해시로 10분 보관하고 사용 시 삭제한다. 봇 토큰은 환경변수로만 주입한다. 내부망 서버는 공개 webhook 대신 long polling으로 수신한다.
- 발송 대상: 새 알람·주의·경보·등급 상향은 모든 라인 관리자, 미확인 3분 재알림은 모든 라인 관리자와 시스템 관리자(알람당 1회), 재발은 기존 담당자, 담당 지정은 당번 중 선택된 보전 담당자다. 값 정상 복귀·공정 상태 변경·재고 단계·통신 끊김은 화면 알림만 보낸다.
- 텔레그램 발송은 원 업무 트랜잭션 커밋 뒤에 실행하며, 1초 뒤 한 번만 재시도한다. 성공·실패와 읽기 쉬운 실패 사유를 `notification_log`에 저장한다. 미연결 계정은 화면 알림만 받는다.
- SF5-19의 수집 메시지는 설비별 3초 묶음이고, 식별자는 `OR-04-1-20261117T090000.000` 같은 200자 이하 문자열이다. 27개 설비 코드와 `run_signal`·`prod_count`·측정값 15개는 V2 초기 데이터와 일치시킨다.
- 멱등성은 Redis `ingest:message:{messageId}`의 처리 중(60초)·완료(24시간) 상태로 관리한다. DB 트랜잭션 실패 시 처리 중 키를 제거한다. `prod_count`는 이전 값과의 차이를 사용하며 감소는 `data_item.reset_time`을 따른 초기화로 취급한다.
- 이 페이지의 기술 제안값은 실제 공장 수치가 아니다. PLC 주소, 임계값 중 가정 6건, 개당 금액과 실제 운영 시간은 현장 또는 강사 확인 후 교체한다.
