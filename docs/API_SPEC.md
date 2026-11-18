# STF 설비통합관제 API 명세서 v7

- 문서 버전: 7.0.0
- 작성 기준일: 2026-11-17 (Asia/Seoul)
- 사용자 REST: `/api/v1`, 내부 수집: `/internal/v1`, 실시간: `/ws`
- OpenAPI: 3.0.3 / 57개 경로 / 67개 operation
- 계약 상태: approved-project-proposal
- 구현 상태: 기존 구현에서 v7 전환 필요. 별도 문서 검증 checkout을 사용하며 진행 중인 타 작업의 구현 완료를 판정하지 않는다.

## 1. 기준 자료와 개정 원칙

2026-11-17 최종 재수정 자료: 요구사항정의서 (2).pdf 19쪽(기능75·NFR13·IFR4), 기능목록_수정본 (2).pdf 26쪽, 기능_변경내역 (2).pdf 10쪽, 화면설계서 (1).pdf 76쪽, 화면_HTML과_PNG.zip의 HTML72개·PNG72개. 공통동작_정의서_1.pdf는 변경 없는 공통 UI 동작의 보조 자료다.

조립 정지 예측은 [최종 결정문 2판](https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/4259864)을 함께 적용했다. 최신 재수정 요구사항과 이 결정이 과거 v6 제안보다 우선한다. 기능75건은 그대로이며 프론트 표시 변경·응답 보완·미사용 제안 API 제거가 중심이다. 문서 속 화면 숫자는 예시이고 기초300개·계획360개/시간은 시연 가정값이다.

V5는 DB 담당의 V5__forecast_settings_and_initial_stock.sql에 예약되어 있다. 기존 V1~V4와 V5를 재번호하지 않으며 다른 새 migration은 V6부터 작성한다. API 문서 버전7.0.0과 Flyway V6 번호는 서로 별개다. 이번 작업은 문서 개정으로 SQL·DB·서버 구현을 변경하지 않는다.

## 2. v6에서 달라진 계약

| 구분 | v7 적용 |
|---|---|
| 복구 평균 | 같은 equipmentId+dataItemId의 과거 종료 CRITICAL 알람 평균과 표본 건수. 1건부터 사용 |
| 이력0건 | 기본값·다른 항목 평균 대체 없음. repairHistoryCount=0, 평균·복구·여유·부족·손실은 null. 정지 예상과 남은 시간은 유지 |
| 재고 출처 | stock_history 최신줄. 재고 시스템 수신 가정, 시연은 기초300+부품완성-조립완성 |
| 소비 | AS-04-1 끝 검사기의 완성 수. 조립 투입 카운터 가정 제거 |
| 설정 | 주의/위험 두 값과 위험<주의 검증. defaultRecoveryMinutes 삭제 |
| 삭제 표시 | appropriateInventory·cycleTimeSeconds·구매품 재고·재고 비율막대 삭제 |
| 응답 추가 | lineType, repairHistoryCount, averageRepairMinutes, shortageMinutes, 단계 기준, 금액 설정 여부, 알람 기준2개·방향·소수자릿수 |
| 알림 읽음 | NTF-001/002 제거. 헤더 unread는 브라우저 메모리이며 새로고침 시0 |
| 범위 유지 | ADMIN/USER, ACTIVE/CLOSED, 조치 완료 즉시종료, Telegram 전체 연결계정 발송 |

총57개 경로·67개 operation, A41/B26이다. v6의 NTF-001(GET /notifications)·NTF-002(PATCH /notifications/{notificationId}/read)는 제거하며 번호를 재사용하지 않는다. v6에서 제거한 확인·담당 지정·추천·오경보·효과 확인·부품·당번·손실방지 API도 계속 제외한다. TLG-007은 기존 봇 callback 어댑터 계약을 유지하며 새 업무 API를 추가하지 않는다.

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
| 사진 | JPG/PNG/WebP, 최대 5MB, multipart. photoUrl은 MST-018의 10분 HMAC 서명 상대 주소(제안값, 2026-11-18 A 추가) |

일반 표 헤더 정렬은 제공하지 않는다. 통계 순위의 sortBy는 화면에서 고르는 집계 기준이며 표 헤더 정렬과 구분한다. 모든 리스트는 동일 값의 마지막 비교로 ID를 사용해 페이지 결과를 안정화한다.

## 4. 핵심 업무규칙

### 4.1 알람과 상태

- 미종료 동일 equipmentId+dataItemId 한 건만 허용한다. 3회 연속 이탈 발생, 3회 정상 복귀가 초기 제안값이다. 정상 복귀 횟수는 alarmConsecutiveCount와 동일하게 적용한다.
- 측정값 임계값은 ABOVE면 caution < critical, BELOW면 caution > critical. 상태값은 alarmValue 0 또는 1과 등급을 사용한다. 누적값은 알람 임계값을 두지 않는다.
- 데이터 항목(MST-010~013, 2026-11-18 A 제안값): 등록 요청의 dataItemCode는 INT-001 dataItemCode와 같은 값으로 필수이며 설비 안에서 중복할 수 없다(삭제한 항목 포함, 409 DATA_ITEM_CODE_DUPLICATE). 등록한 뒤 코드와 valueType은 바꿀 수 없다(valueType 변경은 422 DATA_ITEM_VALUE_TYPE_IMMUTABLE, MST-012의 코드는 무시). MST-012에서 기준 칸을 보내지 않으면 기존 기준을 유지하고 null을 보내면 지운다. MST-013은 MEASUREMENT에 MeasurementThreshold, STATE에 StateAlarmCondition만 받고 CUMULATIVE는 422 THRESHOLD_NOT_SUPPORTED다. 저장한 기준은 다음 판정부터 적용된다. 통계 참고값은 최근 7일(오늘 포함) 측정값의 최소·평균·최대로 MEASUREMENT 항목만 계산한다.
- ACTIVE 알람에 조치를 입력하면 조치·처리자·종료시각·상태를 한 트랜잭션으로 저장한다. 처리자는 토큰에서 정하고 클라이언트가 보내지 않는다. 중복 종료는 409 ALARM_STATE_CONFLICT다.
- CLOSED 알람이 같은 항목에 있어도 새 이탈이면 새 알람이다. 별도 재발 상태나 효과 확인 단계는 없다.
- 설비 상태 우선순위는 COMMUNICATION_LOST → FAULT → CAUTION → IDLE → RUNNING이다. 복귀한 값 자체는 고장으로 판정하지 않고, 미종료 알람은 조치 전까지 화면 목록에 남는다.
- 경과시간=현재-발생, 소요시간=종료-발생. 현재 값은 최신 수집값, 발생 때 값은 occurredValue로 별도 보존한다.

### 4.2 재고·정지·복구·손실 (최종 결정 2판)

- 현재 재고는 PART 라인의 stock_history 가장 최신 줄이다. 운영은 재고 시스템에서 받는다고 가정하며 시연은 검증된 기초300개에 기준점 이후 해당 끝 검사기 생산 증가량을 더하고 AS-04-1 완성 증가량을 뺀다. 조립 완성1개마다 네 부품 라인 재고에서 각각1개 차감한다. 두 출처를 동시에 더하지 않는다.
- 공급 끝 검사기는 OR-06-1/IR-06-1/CG-06-1/SH-05-1, 소비는 AS-04-1이다. 설비 전체 누적값은 수집 가능하지만 재고에 중복 합산하지 않는다. production_log의1분 생산 수량을 RATE_WINDOW_MIN(기본10분) 구간으로 합산해 구간 분으로 나눈다. 고장 설비가 있는 PART 라인의 공급은 즉시0이다.
- 순소비=소비-공급. 순소비>0이면 남은 분=재고/순소비, 정지 예상=서버현재+남은 분. 순소비<=0이면 소진 예상 없음(null)이며 stage는 MAINTAIN이다. 재고0·순소비>0이면 남은0·현재시각 정지예상이다.
- 예상 조치시간은 현재 고장 발생 전에 종료된 같은 equipmentId+dataItemId의 CRITICAL 알람에서 (closedAt-occurredAt)의 평균이다. 유효한1건부터 사용하고 repairHistoryCount와 averageRepairMinutes를 함께 응답한다. 진행 중·음수시간·다른 항목·주의 표본은 제외한다. 원인 문구로 묶지 않는다.
- 이력0건이면 repairHistoryCount=0이고 averageRepairMinutes/recoveryExpectedAt/marginMinutes/marginStatus/shortageMinutes/expectedLossAmount는 모두 null이다. 기본30분이나 설비 전체 평균으로 대체하지 않는다. 입력이 충분하면 remainingMinutes와 assemblyStopExpectedAt은 계속 제공한다. 평균0분은 표본0건과 구분한다.
- 복구 예상=고장 발생+평균 조치분. 여유분=정지 예상-복구 예상. 부족분=max(복구 예상-정지 예상,0). 예상손실=HOURLY_OUTPUT × 부족분/60 × UNIT_PRICE. 분/시간을 환산하고 마지막에 원 단위 반올림한다. 화면 뜻은 '조립이 멈춘 동안 못 만든 수량을 금액으로 바꾼 값'이다.
- HOURLY_OUTPUT은 실측이 아닌 조립 기준값(MES 계획 가정)이며 V5 시연값360개/시간이다. UNIT_PRICE는 초기0, 화면 입력값이다. 단가가0이면 unitPriceConfigured=false를 제공해 설정 안내를 표시한다. 복구·소진 예측이 가능하면 이때 손실액은0이다. 단가가 양수이고 부족분이0인 경우에는 손실액0과 unitPriceConfigured=true를 반환한다. 복구이력0건의 손실null과 구별한다.
- 주의=STOCK_LEVEL_MIN 기본30분(5~120), 위험=STOCK_DANGER_MIN 기본10분(1~60). 반드시0<위험<주의. 남은분<=위험 DANGER, 그다음<=주의 CAUTION, 그 외 MAINTAIN. 기준을 stageThresholds로 공개 조회 응답과 이벤트에 넣어 도움말이 현재 설정을 표시하도록 한다.
- 재계산은60초마다와 설비 상태 변경 시 한다. 같은 계산 snapshot의 calculatedAt을 반환하고 복수 PART 라인은 정지 예상이 빠른 순·lineId로 정렬한다. 이력0건과 카운터 입력부족은 각각 forecastStatus로 구분한다.
- 카운터 리셋·역순·음수 차이를 생산으로 합산하지 않는다. 최신 재고는 유지하되 검증되지 않은 속도/예측은 null/status로 구분한다. 실제 공급 지연·복구 후 남은 공정 통과시간은 현재 모델에 포함되지 않아 손실이 작게 추정될 수 있다.
- 적정재고·사이클타임·구매품 재고·비율막대는 계약에 넣지 않는다. lineType은 DB line.line_type, 알람 방향과 표시 자릿수는 서버 데이터 항목 메타데이터를 사용한다.

### 4.3 알림과 Telegram

- 발생·등급상승만 텔레그램을 연결한 모든 미삭제 계정에 발송한다. 값 정상 복귀·조치 완료·통신 끊김·재고는 텔레그램 발송 대상에서 제외한다(뒤의 두 항목은 제안 결정).
- 발송은 도메인 트랜잭션 완료 후 처리하고 3초 timeout, 1초 후 1회 재시도한다. 실패 사유는 쉬운 메시지와 공급자 코드로 기록한다.
- 가입 완료 응답에 일회용 10분 연결 URL/QR payload를 제공한다. 인증 전 신청자는 이 링크로만 연결한다. 로그인 후에는 본인만 TLG-001~004를 사용한다. update_id와 발송 eventId+recipient를 중복 제거한다.
- ADMIN은 TLG-005로 대상 계정 테스트, TLG-006으로 발송·수신자별 집계를 조회한다. 휴대전화 번호는 수집하지 않는다.
- 발생 중 알람 수는 ALM-001 activeCount이고 헤더 읽지 않은 수는 브라우저 메모리다. 새로고침하면0, 서버에 읽음 기록을 저장하지 않는다. 발생 중 알람은 ALM-001과 STOMP로 복원하며 조치 완료는 ALM-006이다.
- 비밀번호 초기화는 12자리 임시값을 연결된 텔레그램으로 전달하고 30분 후 만료(제안값). 미연결 계정은 422, 계정은 변경하지 않는다. 전달 재시도 실패는 감사 기록과 결과에 남긴다.

### 4.4 분석·정렬·통계

- MEAS-001은 같은 설비 최대10개 항목을 받는다. 24시간 이하 원본, 7일 이하 1분, 그 이상 1시간 간격(제안값). 측정값 평균, 상태값 마지막값, 누적값 마지막값을 사용하고 통신 미수신 구간은 null로 분리한다. 항목당 2000점을 넘으면 1분·5분·10분·15분·30분·1시간·2시간·3시간·6시간·12시간·1일 순으로 간격을 올린다(2026-11-18 A 제안값, 11.3).
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
| MST-018 | A | GET | /api/v1/equipment-photos/{fileName} | 설비 사진 조회(10분 서명 주소) | 서명(로그인 불필요) | FR1302 |
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
| TLG-007 | B | POST | /internal/v1/telegram/updates | Telegram 봇 시작 연결 수신 | TELEGRAM_BOT | FR1410 |

## 6. API별 상세 계약

BR/TC는 작성할 업무규칙·정상/오류 테스트 식별자이며 구현 완료 표시가 아니다. 필드 사전은12절과 OpenAPI components를 따른다.

### AUTH-001 로그인

- 주소: POST /api/v1/auth/login
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0101
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: application/json / LoginRequest
- 응답: 200 LoginResponse; 400 ValidationError; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-AUTH-001; TC-AUTH-001-01, TC-AUTH-001-02

### AUTH-002 Access/Refresh Token 갱신

- 주소: POST /api/v1/auth/refresh
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0102
- 처리: HttpOnly Secure SameSite=Strict 쿠키의 Refresh Token을 회전하고 이전 토큰을 폐기한다.

- 요청: application/json / RefreshTokenRequest
- 응답: 200 TokenResponse; 401 Unauthorized
- 규칙/테스트: BR-AUTH-002; TC-AUTH-002-01, TC-AUTH-002-02

### AUTH-003 로그아웃과 현재 Access Token 폐기

- 주소: POST /api/v1/auth/logout
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0104
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 204 로그아웃 성공; 401 Unauthorized
- 규칙/테스트: BR-AUTH-003; TC-AUTH-003-01, TC-AUTH-003-02

### AUTH-004 로그인 사용자 정보 조회

- 주소: GET /api/v1/auth/me
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0105
- 처리: 로그인 사용자와 성공한 최종 로그인 시각을 반환한다.

- 요청: 본문 없음
- 응답: 200 CurrentUserResponse; 401 Unauthorized
- 규칙/테스트: BR-AUTH-004; TC-AUTH-004-01, TC-AUTH-004-02

### AUTH-005 회원가입 신청

- 주소: POST /api/v1/auth/signups
- 소유자: A; 권한: ANONYMOUS; 요구사항: FR0106
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: application/json / SignupRequest
- 응답: 201 SignupResponse; 400 ValidationError; 409 Conflict
- 규칙/테스트: BR-AUTH-005; TC-AUTH-005-01, TC-AUTH-005-02

### AUTH-006 본인 비밀번호 변경

- 주소: PUT /api/v1/auth/me/password
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0108
- 처리: 변경 성공 시 현재 세션을 포함한 기존 토큰을 모두 폐기한다.

- 요청: application/json / PasswordChangeRequest
- 응답: 204 비밀번호 변경 및 기존 토큰 폐기 성공; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-AUTH-006; TC-AUTH-006-01, TC-AUTH-006-02

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
- 규칙/테스트: BR-COM-001; TC-COM-001-01, TC-COM-001-02

### COM-002 헤더 통신 상태 조회

- 주소: GET /api/v1/communications/summary
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0205
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 CommunicationSummaryResponse; 401 Unauthorized
- 규칙/테스트: BR-COM-002; TC-COM-002-01, TC-COM-002-02

### OVW-001 공장 설비 배치도 조회

- 주소: GET /api/v1/overview/equipment-layout
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0701
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 EquipmentLayoutResponse; 401 Unauthorized
- 규칙/테스트: BR-OVW-001; TC-OVW-001-01, TC-OVW-001-02

### OVW-002 조립 정지·복구·손실 예측 조회

- 주소: GET /api/v1/overview/assembly-stop-forecast
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0903, FR0904, FR0905, FR0906, FR0907
- 처리: FR0903~0907 조립 정지 예측. 같은 설비·항목 종료 CRITICAL 평균과 표본 수 제공. 0건이면 복구·여유·부족·손실 null, 소진·정지 예상 유지. 생산량은 HOURLY_OUTPUT, 단가는 UNIT_PRICE. 60초 및 설비 상태변경 재계산.

- 요청: 본문 없음
- 응답: 200 AssemblyStopForecastResponse; 401 Unauthorized
- 규칙/테스트: BR-OVW-002, BR-RECOVERY-001; TC-OVW-002-01, TC-OVW-002-02

### INV-001 라인별 현재 재고 조회

- 주소: GET /api/v1/inventory
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0902
- 처리: PART 라인의 최신 stock_history 수량과 속도·소진·정지 예상·단계·현재 설정 기준. AS-04-1 완성 수를 소비량으로 사용. 데이터 부족은 calculationStatus와 null로 구분.

- 요청: 본문 없음
- 응답: 200 InventoryResponse; 401 Unauthorized
- 규칙/테스트: BR-INV-001; TC-INV-001-01, TC-INV-001-02

### INV-002 재고 추이 조회

- 주소: GET /api/v1/inventory/history
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0908
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineIds | N | array<Identifier> | - |

- 요청: 본문 없음
- 응답: 200 InventoryHistoryResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-INV-002; TC-INV-002-01, TC-INV-002-02

### INV-003 라인별 재고 상세 조회

- 주소: GET /api/v1/inventory/lines/{lineId}
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0909
- 처리: 재고 시스템/시연용 최신 stock_history와 입고·소비·소진·정지 예상. 적정재고·사이클타임·구매품·비율막대 필드 제거.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 InventoryDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-INV-003; TC-INV-003-01, TC-INV-003-02

### EQP-001 조회용 라인 목록

- 주소: GET /api/v1/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0701, FR0703, FR0801
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 LineListResponse; 401 Unauthorized
- 규칙/테스트: BR-EQP-001; TC-EQP-001-01, TC-EQP-001-02

### EQP-002 라인의 공정 목록 조회

- 주소: GET /api/v1/lines/{lineId}/processes
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0703
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 ProcessListResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-002; TC-EQP-002-01, TC-EQP-002-02

### EQP-003 공정별 설비 목록 조회

- 주소: GET /api/v1/processes/{processId}/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0703
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | processId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 ProcessEquipmentResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-003; TC-EQP-003-01, TC-EQP-003-02

### EQP-004 설비 상세 조회

- 주소: GET /api/v1/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0203, FR0704
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 EquipmentDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-004; TC-EQP-004-01, TC-EQP-004-02

### EQP-005 설비 데이터 항목 목록 조회

- 주소: GET /api/v1/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR0801
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DataItemListResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-EQP-005; TC-EQP-005-01, TC-EQP-005-02

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
- 규칙/테스트: BR-MEAS-001; TC-MEAS-001-01, TC-MEAS-001-02

### ALM-001 발생 중·종료 알람 통합 목록

- 주소: GET /api/v1/alarms
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0502, FR0601, FR0602
- 처리: 발생 중 우선, 각 상태 안에서 occurredAt DESC, alarmId DESC로 고정 정렬. 기간·등급·설비·상태로 조회. 요약은 동일 조회 조건 기준이며 today 집계는 Asia/Seoul 오늘과 조회 범위의 교집합이다. 주의·경보 기준, 기준 방향, 소수 자릿수는 서버 항목 설정값을 제공한다. thresholdValue는 발생 당시 기준이다.

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
- 규칙/테스트: BR-ALM-001; TC-ALM-001-01, TC-ALM-001-02

### ALM-003 알람 상세 조회

- 주소: GET /api/v1/alarms/{alarmId}
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0603, FR0611
- 처리: 발생 전후 각 30분의 측정값, 같은 설비의 최근 종료 알람 최대 20건, 조치 기록, 조립 정지 예상 시각. 아직 도래하지 않은 구간은 빈 값으로 유지하며 데이터가 없는 구간을 보간하지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | alarmId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AlarmDetailResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-ALM-003; TC-ALM-003-01, TC-ALM-003-02

### ALM-006 조치 기록 및 알람 즉시 종료

- 주소: POST /api/v1/alarms/{alarmId}/actions
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR0606, FR0610, FR0611
- 처리: 설비 글과 조치 상세를 검증하고 원인 기본값을 적용한 후 한 트랜잭션으로 조치·처리자·종료 시각·CLOSED를 저장한다. 이미 종료된 알람은 409. 값 정상 여부와 관계없이 종료한다. 종료 이후 별도의 판정 주기를 충족하면 새 알람을 생성할 수 있다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | alarmId | Y | Identifier | - |

- 요청: application/json / AlarmActionRequest
- 응답: 201 AlarmActionResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound; 409 Conflict
- 규칙/테스트: BR-ALM-006; TC-ALM-006-01, TC-ALM-006-02

### HST-001 종료 알람 이력 조회

- 주소: GET /api/v1/alarm-histories
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1201
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

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
- 규칙/테스트: BR-HST-001; TC-HST-001-01, TC-HST-001-02

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
- 규칙/테스트: BR-HST-002; TC-HST-002-01, TC-HST-002-02

### STA-001 알람 대응 속도 집계

- 주소: GET /api/v1/statistics/alarm-response
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1202
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AlarmResponseStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-001; TC-STA-001-01, TC-STA-001-02

### STA-003 일별 알람 건수

- 주소: GET /api/v1/statistics/daily-alarms
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1204
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DailyAlarmStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-003; TC-STA-003-01, TC-STA-003-02

### STA-004 설비·라인 가동률 집계

- 주소: GET /api/v1/statistics/availability
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1206
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 AvailabilityStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-004; TC-STA-004-01, TC-STA-004-02

### STA-005 설비 정지 시간 집계

- 주소: GET /api/v1/statistics/downtime
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1207
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 DowntimeStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-005; TC-STA-005-01, TC-STA-005-02

### STA-006 문제 설비 순위 조회

- 주소: GET /api/v1/statistics/equipment-rankings
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1208
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |
| query | sortBy | Y | string | 허용 faultCount, downtimeMinutes, averageRepairMinutes, lossAmount |

- 요청: 본문 없음
- 응답: 200 EquipmentRankingResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-006; TC-STA-006-01, TC-STA-006-02

### STA-007 실제 손실액 집계

- 주소: GET /api/v1/statistics/losses
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1209
- 처리: ERD E2의 실제 조립 정지 구간을 읽어 오늘·최근 7일·일별 손실액과 설비 순위를 집계한다. 실제 재고0 또는 A가 제공한 조립 설비 FAULT에서 B가 assembly_stop을 시작한다. 예측 시각만으로 실제 정지를 만들지 않는다.
- 기록: 기존 A 상태·설정 Query를 읽고 B의 assembly_stop과 backend_job_lock만 쓴다. 이벤트 후와 1초 주기에서 재확인하며 전체 시스템의 연속 정지는 한 건으로 유지한다. 원인은 기존 정지 요인 집계처럼 가장 이른 근거와 ID로 안정 선택한다.
- 복구: 재고 부족과 조립 고장이 모두 해소된 것을 확인했을 때 종료한다. 통신 끊김·기준정보 없음·재고 없음으로 복구를 추정하지 않는다. 기록을 시작한 관찰 시각부터 계산하며 미기록 과거 구간과 과거 설정값을 추측해 소급하지 않는다.
- 금액: 정지 시작 때 HOURLY_OUTPUT과 UNIT_PRICE를 복사해 보존한다. 0원은 유효하다. 겹친 구간은 한 번만, 조회기간은 [from,to), 진행 중은 현재 시각까지만 집계한다. 구간별 조기 반올림 없이 각 응답 집계의 합계를 원 단위에서 최종 반올림한다. 원인 설비를 알 수 없는 재고 부족은 null로 보존하고 shortage_line_id로 라인 집계에 포함한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | from | Y | string(date-time) | - |
| query | to | Y | string(date-time) | - |
| query | lineId | N | Identifier | - |

- 요청: 본문 없음
- 응답: 200 LossStatisticsResponse; 401 Unauthorized
- 규칙/테스트: BR-STA-007; TC-STA-007-01, TC-STA-007-02

### MST-001 기준정보 설비 조회

- 주소: GET /api/v1/master/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1301
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | lineId | N | Identifier | - |
| query | equipmentId | N | Identifier | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 MasterEquipmentPageResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-001; TC-MST-001-01, TC-MST-001-02

### MST-002 설비 등록

- 주소: POST /api/v1/master/equipment
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1302
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: multipart/form-data / EquipmentWriteRequest
- 응답: 201 MasterEquipmentResponse; 400 ValidationError; 401 Unauthorized; 409 Conflict
- 규칙/테스트: BR-MST-002; TC-MST-002-01, TC-MST-002-02

### MST-003 설비 수정

- 주소: PUT /api/v1/master/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1302
- 처리: equipmentCode는 등록 후 수정할 수 없다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: multipart/form-data / EquipmentUpdateRequest
- 응답: 200 MasterEquipmentResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-003; TC-MST-003-01, TC-MST-003-02

### MST-004 설비 논리 삭제

- 주소: DELETE /api/v1/master/equipment/{equipmentId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1303
- 처리: deletedAt과 deletedBy를 기록하고 일반 조회·판정·알림 대상에서 제외한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 논리 삭제 성공; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-004; TC-MST-004-01, TC-MST-004-02

### MST-005 라인과 배치 기준정보 조회

- 주소: GET /api/v1/master/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 MasterLineListResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-005; TC-MST-005-01, TC-MST-005-02

### MST-006 라인 등록

- 주소: POST /api/v1/master/lines
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: application/json / LineWriteRequest
- 응답: 201 MasterLineResponse; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-MST-006; TC-MST-006-01, TC-MST-006-02

### MST-007 라인 이름과 표시순서 수정

- 주소: PUT /api/v1/master/lines/{lineId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: application/json / LineWriteRequest
- 응답: 200 MasterLineResponse; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-007; TC-MST-007-01, TC-MST-007-02

### MST-008 빈 라인 삭제

- 주소: DELETE /api/v1/master/lines/{lineId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 라인 삭제 성공; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-MST-008; TC-MST-008-01, TC-MST-008-02

### MST-009 라인 설비 배치 일괄 변경

- 주소: PATCH /api/v1/master/lines/{lineId}/layout
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1304
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | lineId | Y | Identifier | - |

- 요청: application/json / LineLayoutUpdateRequest
- 응답: 200 MasterLineResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound
- 규칙/테스트: BR-MST-009; TC-MST-009-01, TC-MST-009-02

### MST-010 설비 데이터 항목 관리 목록

- 주소: GET /api/v1/master/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305, FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 MasterDataItemListResponse; 401 Unauthorized
- 규칙/테스트: BR-MST-010; TC-MST-010-01, TC-MST-010-02

### MST-011 설비 데이터 항목 등록

- 주소: POST /api/v1/master/equipment/{equipmentId}/data-items
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305, FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | equipmentId | Y | Identifier | - |

- 요청: application/json / DataItemWriteRequest
- 응답: 201 MasterDataItemResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound(EQUIPMENT_NOT_FOUND); 409 Conflict(DATA_ITEM_CODE_DUPLICATE); 422 BusinessRuleViolation(INVALID_THRESHOLD_ORDER)
- 규칙/테스트: BR-MST-011; TC-MST-011-01, TC-MST-011-02

### MST-012 데이터 항목 수정

- 주소: PUT /api/v1/master/data-items/{dataItemId}
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1305
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | dataItemId | Y | Identifier | - |

- 요청: application/json / DataItemWriteRequest
- 응답: 200 MasterDataItemResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound(DATA_ITEM_NOT_FOUND); 422 BusinessRuleViolation(INVALID_THRESHOLD_ORDER, DATA_ITEM_VALUE_TYPE_IMMUTABLE)
- 규칙/테스트: BR-MST-012; TC-MST-012-01, TC-MST-012-02

### MST-013 측정값 또는 상태값 알람 기준 수정

- 주소: PUT /api/v1/master/data-items/{dataItemId}/threshold
- 소유자: A; 권한: ADMIN, USER; 요구사항: FR1306
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | dataItemId | Y | Identifier | - |

- 요청: application/json / ThresholdWriteRequest
- 응답: 200 ThresholdResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound(DATA_ITEM_NOT_FOUND); 422 BusinessRuleViolation(INVALID_THRESHOLD_ORDER, THRESHOLD_NOT_SUPPORTED)
- 규칙/테스트: BR-MST-013; TC-MST-013-01, TC-MST-013-02

### MST-018 설비 사진 조회

- 주소: GET /api/v1/equipment-photos/{fileName}?expires={epochSeconds}&signature={hmac}
- 소유자: A; 권한: 로그인 불필요(서명 확인); 요구사항: FR1302. 2026-11-18 A 추가(제안값)
- 처리: MST-001/002/003·EQP-004의 photoUrl이 주는 상대 주소다. 화면의 `<img>`가 토큰 없이 연다. 만료 10분, 서명은 JWT 키에서 용도별로 파생한 HMAC-SHA256이다. 서명 불일치·만료·파일 없음·이름 형식 오류는 모두 404 EQUIPMENT_PHOTO_NOT_FOUND로 같게 응답한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | fileName | Y | string | 패턴 `^[0-9a-f]{32}\.(jpg|png|webp)$` |
| query | expires | Y | integer(int64) | - |
| query | signature | Y | string | - |

- 요청: 본문 없음
- 응답: 200 image/jpeg·image/png·image/webp; 404 NotFound

### SYS-001 판정 설정 조회

- 주소: GET /api/v1/system/settings/judgement
- 소유자: A; 권한: ADMIN; 요구사항: FR1401
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 JudgementSettingsResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-SYS-001; TC-SYS-001-01, TC-SYS-001-02

### SYS-002 판정 설정 수정

- 주소: PUT /api/v1/system/settings/judgement
- 소유자: A; 권한: ADMIN; 요구사항: FR1401
- 처리: 판정 설정의 주의5~120분·위험1~60분, 위험<주의 관계를 서버 검증. 위반422 SETTING_OUT_OF_RANGE. 정상 복귀에는 alarmConsecutiveCount 사용.

- 요청: application/json / JudgementSettings
- 응답: 200 JudgementSettingsResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-SYS-002; TC-SYS-002-01, TC-SYS-002-02

### SYS-003 계산 기준값 조회

- 주소: GET /api/v1/system/settings/calculation
- 소유자: A; 권한: ADMIN; 요구사항: FR1402
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 CalculationSettingsResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-SYS-003; TC-SYS-003-01, TC-SYS-003-02

### SYS-004 계산 기준값 수정

- 주소: PUT /api/v1/system/settings/calculation
- 소유자: A; 권한: ADMIN; 요구사항: FR1402
- 처리: UNIT_PRICE, RATE_WINDOW_MIN, HOURLY_OUTPUT 저장. 복구 기본값 필드 없음. 모든 필드는 PUT 시 필수. 현재 값 조회 후 한 카드만 바뀌어도 나머지를 함께 송신.

- 요청: application/json / CalculationSettingsUpdateRequest
- 응답: 200 CalculationSettingsResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-SYS-004; TC-SYS-004-01, TC-SYS-004-02

### USR-001 등록 사용자 목록 조회

- 주소: GET /api/v1/system/users
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | keyword | N | string | 길이≤100 |
| query | permission | N | Permission | - |
| query | accountStatus | N | AccountStatus | - |
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 UserPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-USR-001; TC-USR-001-01, TC-USR-001-02

### USR-002 사용자 상세 조회

- 주소: GET /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 200 UserResponse; 401 Unauthorized; 403 Forbidden; 404 NotFound
- 규칙/테스트: BR-USR-002; TC-USR-002-01, TC-USR-002-02

### USR-003 사용자 이름·권한 수정

- 주소: PUT /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 이름·권한만 수정. 마지막 ADMIN 계정의 USER 강등은 LAST_ADMIN_REQUIRED 422. 권한 변경 시 기존 토큰을 폐기하고 감사 기록을 남긴다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: application/json / UserUpdateRequest
- 응답: 200 UserResponse; 400 ValidationError; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-USR-003; TC-USR-003-01, TC-USR-003-02

### USR-004 사용자 계정 논리 삭제

- 주소: DELETE /api/v1/system/users/{userId}
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 자기 삭제 및 마지막 ADMIN 삭제는 422. 논리 삭제와 토큰 폐기·텔레그램 연결 해제·감사 기록을 한 트랜잭션으로 처리하고 지난 조치 기록은 보존한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 논리 삭제 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-USR-004; TC-USR-004-01, TC-USR-004-02

### USR-005 관리자 비밀번호 초기화

- 주소: POST /api/v1/system/users/{userId}/password-reset
- 소유자: A; 권한: ADMIN; 요구사항: FR1403
- 처리: 12자리 임시 비밀번호를 생성해 연결된 텔레그램으로 전달(30분 유효, 프로젝트 제안값). 미연결 또는 재시도까지 발송 실패면 422로 응답하고 기존 비밀번호·세션을 유지한다. 전달 성공 시 새 해시·강제변경 상태 저장 및 기존 세션 폐기. 동시 초기화는 계정 잠금으로 직렬화한다. 첫 로그인은 비밀번호 변경 화면만 허용한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | userId | Y | Identifier | - |

- 요청: 본문 없음
- 응답: 204 비밀번호 초기화 처리 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound; 422 ErrorResponse
- 규칙/테스트: BR-USR-005; TC-USR-005-01, TC-USR-005-02

### APR-001 회원가입 승인 대기 목록 조회

- 주소: GET /api/v1/system/signup-requests
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| query | page | N | integer | 값≥0; 기본 0 |
| query | size | N | integer | 값≥1; 값≤100; 기본 20 |

- 요청: 본문 없음
- 응답: 200 SignupPageResponse; 401 Unauthorized; 403 Forbidden
- 규칙/테스트: BR-APR-001; TC-APR-001-01, TC-APR-001-02

### APR-002 회원가입 승인

- 주소: POST /api/v1/system/signup-requests/{requestId}/approval
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 권한을 지정해 ACTIVE로 승인하고 연결된 신청자에게 결과를 발송한다. 발송 장애는 승인 트랜잭션을 되돌리지 않는다.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | requestId | Y | Identifier | - |

- 요청: application/json / SignupApprovalRequest
- 응답: 200 UserResponse; 401 Unauthorized; 403 Forbidden; 404 NotFound; 409 Conflict
- 규칙/테스트: BR-APR-002; TC-APR-002-01, TC-APR-002-02

### APR-003 회원가입 거절

- 주소: POST /api/v1/system/signup-requests/{requestId}/rejection
- 소유자: A; 권한: ADMIN; 요구사항: FR0107
- 처리: 신청을 제거하고 연결 토큰을 폐기한다. 신청자에게 보낼 결과는 제거 전 수신 정보를 이벤트로 보존해 발송한다. 반복 승인·거절 충돌은409.

| 위치 | 이름 | 필수 | 타입 | 제약 |
|---|---|---|---|---|
| path | requestId | Y | Identifier | - |

- 요청: application/json / SignupRejectionRequest
- 응답: 204 거절 처리 성공; 401 Unauthorized; 403 Forbidden; 404 NotFound
- 규칙/테스트: BR-APR-003; TC-APR-003-01, TC-APR-003-02

### TLG-001 내 텔레그램 연결 URL 발급

- 주소: POST /api/v1/telegram/link-tokens
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 로그인한 본인 계정만 발급. 익명 가입자는 AUTH-005 응답의 10분 링크를 사용한다. 새 토큰 발급 시 이전 미사용 토큰을 폐기.

- 요청: 본문 없음
- 응답: 200 TelegramLinkResponse; 401 Unauthorized
- 규칙/테스트: BR-TLG-001; TC-TLG-001-01, TC-TLG-001-02

### TLG-002 내 텔레그램 연결 상태

- 주소: GET /api/v1/telegram/me/connection
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 TelegramConnectionResponse; 401 Unauthorized
- 규칙/테스트: BR-TLG-002; TC-TLG-002-01, TC-TLG-002-02

### TLG-003 내 텔레그램 연결 해제

- 주소: DELETE /api/v1/telegram/me/connection
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 204 연결 해제; 401 Unauthorized
- 규칙/테스트: BR-TLG-003; TC-TLG-003-01, TC-TLG-003-02

### TLG-004 내 텔레그램 시험 발송

- 주소: POST /api/v1/telegram/me/test-messages
- 소유자: B; 권한: ADMIN, USER; 요구사항: FR1410
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: 본문 없음
- 응답: 200 TelegramTestMessageResponse; 401 Unauthorized; 422 BusinessRuleViolation
- 규칙/테스트: BR-TLG-004; TC-TLG-004-01, TC-TLG-004-02

### TLG-005 테스트 메시지 발송

- 주소: POST /api/v1/system/telegram/test-messages
- 소유자: B; 권한: ADMIN; 요구사항: FR1406
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

- 요청: application/json / TelegramTestMessageRequest
- 응답: 200 TelegramTestMessageResponse; 401 Unauthorized; 403 Forbidden; 422 BusinessRuleViolation
- 규칙/테스트: BR-TLG-005; TC-TLG-005-01, TC-TLG-005-02

### TLG-006 텔레그램 전송 결과 조회

2026-11-18 보완: 선택 query alarmId(integer/int64, 최소1)를 지원한다. KST date(생략 시 오늘)와 함께 이 알람만 목록·요약·수신자별 집계에 포함한다. result는 목록과 meta에 적용하고 요약/수신자별 집계는 같은 날짜·알람의 성공/실패 전체를 제공한다. ADMIN 전용이며 ALM-003 USER 권한으로 수신자 이력을 우회 제공하지 않는다.

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
- 규칙/테스트: BR-TLG-006; TC-TLG-006-01, TC-TLG-006-02

### AUD-001 설정 변경 이력 조회

- 주소: GET /api/v1/system/change-histories
- 소유자: A; 권한: ADMIN; 요구사항: FR1407
- 처리: 공통 인증·검증·업무규칙을 적용하고 스키마를 반환한다.

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
- 규칙/테스트: BR-AUD-001; TC-AUD-001-01, TC-AUD-001-02

### INT-001 Node-RED 설비 데이터 수집

- 주소: POST /internal/v1/measurements
- 소유자: A; 권한: NODE_RED; 요구사항: FR0201
- 처리: 등록 설비/항목 CUMULATIVE(prod_count)와 MEASUREMENT/STATE 묶음 수집. 누적 차분을 equipment별 production_log 1분 수량으로 집계. 재고에는 OR-06-1/IR-06-1/CG-06-1/SH-05-1와 AS-04-1만 사용. 메시지 중복·카운터 리셋은 중복 생산으로 합산하지 않음.

- 요청: application/json / MeasurementIngestionRequest
- 응답: 202 IngestionAcceptedResponse; 400 ValidationError; 401 Unauthorized; 404 NotFound; 422 BusinessRuleViolation
- 규칙/테스트: BR-INT-001; TC-INT-001-01, TC-INT-001-02

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
- 규칙/테스트: BR-ALM-002; TC-ALM-002-01, TC-ALM-002-02

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
- 규칙/테스트: BR-STA-009; TC-STA-009-01, TC-STA-009-02

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
- 규칙/테스트: BR-STA-010; TC-STA-010-01, TC-STA-010-02

### TLG-007 Telegram 봇 시작 연결 수신

- 주소: POST /internal/v1/telegram/updates
- 소유자: B; 권한: TELEGRAM_BOT; 요구사항: FR1410
- 처리: 등록한 봇 Webhook만 접근. /start <일회용 토큰>을 검증해 계정 연결. update_id 24시간 중복 제거. 토큰 10분·일회성·해시 보관. 사용자에게 chat_id 입력을 받지 않는다. 지원하지 않는 update는 200 processed=false. 만료·사용된 토큰은 연결하지 않고 processed=false로 응답한다.

- 요청: application/json / TelegramUpdate
- 응답: 200 TelegramUpdateResult; 400 ValidationError; 401 Unauthorized
- 규칙/테스트: BR-TLG-007; TC-TLG-007-01, TC-TLG-007-02

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
| LINE_NOT_FOUND | 404 | 라인 없음·삭제됨(EQP-002, MST-007~009) |
| PROCESS_NOT_FOUND | 404 | 공정 없음 또는 라인 삭제됨(EQP-003) |
| EQUIPMENT_HAS_ACTIVE_ALARM | 422 | 발생 중 알람 설비 삭제 |
| LINE_HAS_EQUIPMENT | 422 | 설비가 있는 라인 삭제 |
| EQUIPMENT_PHOTO_NOT_FOUND | 404 | 사진 서명 불일치·만료·파일 없음(MST-018) |
| DATA_ITEM_NOT_FOUND | 404 | 수집/조회 항목 미등록 |
| INVALID_THRESHOLD_ORDER | 422 | 주의/경보 방향 순서 오류 |
| DATA_ITEM_CODE_DUPLICATE | 409 | 설비 안 항목 코드 중복(삭제한 항목 포함). 2026-11-18 추가 |
| DATA_ITEM_VALUE_TYPE_IMMUTABLE | 422 | 등록한 항목의 valueType 변경. 2026-11-18 추가 |
| THRESHOLD_NOT_SUPPORTED | 422 | 누적값 항목의 알람 기준 수정. 2026-11-18 추가 |
| ALARM_NOT_FOUND | 404 | 알람 없음 |
| ALARM_STATE_CONFLICT | 409 | 이미 종료된 알람에 조치 |
| ACTION_CONTENT_REQUIRED | 400 | 조치 상세 공백 |
| SETTING_OUT_OF_RANGE | 422 | 설정값 범위·관계 오류 |
| TELEGRAM_NOT_CONNECTED | 422 | 미연결 테스트/초기화 |
| TELEGRAM_DELIVERY_FAILED | 422 | 재시도 발송 실패 |

error.details는 필드별 검증을, traceId는 서버 로그 연계를 위한 식별자를 제공한다. 비밀번호·토큰·연결 원문 비밀값은 로그·감사에서 제외한다.

## 8. WebSocket STOMP

/ws, CONNECT Bearer 인증, heartbeat10초. 재연결1·2·4·8·16·30초 후 현재 화면 REST 재조회. DB커밋 후 발행, eventId 중복제거·집계별 sequence 비교.

| 토픽 | 발행 | 핵심 필드 |
|---|---|---|
| /topic/equipment-status | 변경 시 | eventId, sequence, equipmentId, status, statusStartedAt, occurredAt |
| /topic/measurements/{equipmentId} | 유효수집3초 | eventId, sequence, equipmentId, dataItemId, valueType, value, measuredAt |
| /topic/alarms | 변경 시 | eventId, sequence, alarmId, eventType, grade, processStatus, occurredAt |
| /topic/alarm-counts | 변경 시 | eventId, sequence, activeCount |
| /topic/inventory | 60초 및 상태변경 | eventId, sequence, lineId, quantity, stage, remainingMinutes, occurredAt, stageThresholds |
| /user/queue/notifications | 변경 시 | eventId, sequence, type, alarmId, occurredAt |

헤더 종 unread는 클라이언트 메모리에서 신규·등급상승 이벤트로 계산하며 새로고침하면0이다. 서버 읽음 저장 REST/DB 계약은 없다. 개인 큐는 본인만, 공용topic은 승인된 로그인 계정만 구독한다. stageThresholds는 현행 설정값이다.

### 프론트 타입 생성과 순번 (2026-11-18 보완)

OpenAPI의 components.schemas에 EquipmentStatusEvent, MeasurementEvent, AlarmEvent, AlarmCountEvent, InventoryEvent, UserNotificationEvent를 제공한다. x-websocket.topics의 인라인 스키마와 YAML 앵커로 동일한 정의를 공유한다. 실제 메시지 필드·Enum은 변경하지 않는다. UserNotificationEvent는 eventId·sequence·type·alarmId·occurredAt이며 userId·message·notificationId는 없다.

순번은 발행 서비스 인스턴스의 AtomicLong을 공유한다. 클라이언트 비교 키는 destination과 설비 상태=equipmentId, 측정값=equipmentId+dataItemId, 알람/개인 알림=alarmId, 재고=lineId다. alarm-counts는 destination 하나다. 서로 다른 키나 토픽의 순번을 비교하지 않는다. 한 프로세스의 발행 서비스는 동작 중 카운터를 초기화하지 않으며 프로세스 재시작 시 초기화된다. 재연결마다 이전 비교값을 버리고 현재 화면 REST를 재조회한다. outbox의 지연된 과거 재시도는 낮은 순번일 수 있으며 eventId 중복 제거를 함께 적용한다. 다중 서버의 전역 순번은 이번 단일 서버 계약에 포함하지 않는다.

## 9. MQTT·수집·생산 기록

설비/PLC → MQTT/Node-RED → POST /internal/v1/measurements. Spring Boot는 브로커를 직접 구독하지 않는다. 토픽 stf/equipment/{equipmentCode}/values, QoS1·retained=false·3초 묶음, UTF-8 JSON. messageId 문자열은 재전송만 재사용하고24시간 중복제거. 등록된 설비·항목의1~500개를 원자 검증하고 STATE0/1, CUMULATIVE 비음수정수, MEASUREMENT 유한숫자로 저장한다.

생산은 누적 prod_count 차분을 설비별 production_log 1분 수량으로 집계한다. AS-04-1의 완성 수를 소비로 사용한다. 결정문7절의 '생산 이벤트'는 가상설비의 생산 통지를 뜻하며 최신 IFR01은 누적 카운터를 명시한다. HTTP INT-002를 임의 재도입하지 않고 기존 Node-RED 어댑터가 전달하는 값을 INT-001/production_log로 한 번만 반영한다. 서로 다른 전송 형식을 동시에 집계해 생산량을 두 번 더하지 않는다.

현재 재고는 stock_history 최신값이며 운영 외부 재고연계는 가정이다. 시연 모드만 V5 기초300개에 끝 검사기 생산/완성 증가량을 반영한다. 초기카운터 기준·리셋epoch·역순값을 검증하고 미확정 입력은 null/status로 나타낸다.

통신은 마지막 유효수신 후30초 초과 기본, 설정10~300초. 수신 재개 시 끊김 해제와 시작·종료 기록. 내부 Key는90일 회전·직전24시간 중첩(제안값), TLS·내부망·환경변수 관리다.

## 10. 요구사항 추적표

| FR | 요구사항 | API / 처리 |
|---|---|---|
| FR0101 | 로그인 등록된 계정으로 사용자를 확인한다 | AUTH-001 |
| FR0102 | 로그인 유지 로그인 상태를 계속 유지한다 | AUTH-002, AUTH-002·인증 필터 |
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
| FR0302 | 설비 상태 실시간 전달 상태 변화를 화면에 바로 알린다 | STOMP equipment-status |
| FR0401 | 임계값 판정 측정값이 기준을 벗어났는지 판정한다 없음 없음 | B 연속 이탈 판정 |
| FR0402 | 등급 상향 주의 알람이 더 나빠지면 경보로 올린다 없음 없음 | B 등급 상향 |
| FR0403 | 중복 알람 방지 같은 원인의 알람이 여러 건 생기지 않게 | 동일 설비·항목 미종료 유일성 |
| FR0404 | 값 정상 복귀 판정 측정값이 기준 안으로 돌아왔는지 판정 | 값 정상 복귀, 알람 종료 아님 |
| FR0405 | 상태값 판정 상태값이 알람 조건에 해당하는지 판정 | STATE 0/1 판정 |
| FR0501 | 화면 알림 접속 중인 사용자에게 새 알람을 바로 알 | STOMP 알람 이벤트·Front 알림 |
| FR0502 | 발생 중 알람 수 제공 발생 중인 알람이 몇 건인지 항상 보여 | ALM-001, ALM-001 activeCount·Front 메모리 unreadCount |
| FR0503 | 텔레그램 알림 텔레그램으로 알람을 보낸다 없음 없음 | B Telegram 발송 |
| FR0506 | 발송 실패 처리 텔레그램 발송이 실패해도 알람 처리가 | B 재시도·실패 이력 |
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
| FR0901 | 재고 계산 라인별 부품 재고를 계산한다 없음 없음 | stock_history 최신 재고·시연 생산 차분 |
| FR0902 | 재고 현황 조회 라인별 재고와 줄어드는 속도를 본다 | INV-001 |
| FR0903 | 재고 소진 시간 예측 재고가 바닥나기까지 남은 시간을 계산 | OVW-002, production_log 속도·소진 예측 |
| FR0904 | 조립 정지 시각 예측 조립이 멈출 것으로 예상되는 시각을 계 | OVW-002, B 조립 정지 예측 |
| FR0905 | 복구 시각 예측 고장 난 설비가 언제 복구될지 예상한다 | OVW-002, BR-RECOVERY-001 동일 항목 종료 경보 평균 |
| FR0906 | 여유 시간 판정 조립이 멈추기 전에 복구되는지 판정한 | OVW-002, 복구 이력 없으면 여유 null |
| FR0907 | 예상 손실액 계산 지금 고장으로 생길 손실을 금액으로 계 | OVW-002, HOURLY_OUTPUT·UNIT_PRICE·부족분 손실 |
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
| FR1501 | 실시간 갱신 새로 고침 없이 바뀐 값을 화면에 반영한 | STOMP 재연결·REST 재조회 |
| FR1502 | 조회 조건 처리 이력과 통계의 조회 방식을 통일한다 | 고정 정렬·페이징·필터 |
| FR1503 | 오류와 빈 결과 안내 조회나 저장이 되지 않았을 때 알려 준다 | 공통 성공/오류 응답 |
| FR1504 | 화면 간 이동 관련된 정보로 바로 넘어간다 없음 | Front 화면 이동 |
| FR1505 | 상태 설명 도움말 범례와 상태 설명을 필요할 때만 본다 없음 | Front 설정값 기반 도움말 |

75개 FR 전체 연결. NFR01~02=인증·권한·비밀번호,03~04=실시간·복구,05=발송장애격리,06=조치·감사,07~09=기준정보·보존·검증,10~13=오류·표시·화면크기·ECharts. IFR01=INT-001·누적생산,02=가입/TLG연결,03=Telegram발송,04=STOMP다.

## 11. 구현 전환·마이그레이션·검증

이번 작업은 v7 문서 개정이다. 진행 중인 타 개발자의 서버 코드를 수정하거나 DB에 적용하지 않는다. 배포·구현 완료는 별도 통합 테스트로 판단한다.

- v6의 동일 항목3건·설비 전체평균·기본30분 대체, 조립 투입 카운터 가정, 위험=주의/3, 적정재고/사이클타임, NTF읽음 API를 제거한다.
- A는 SYS 판정/계산 설정·lineType·수집과1분집계·감사 포트를, B는 복구 평균/건수·stock_history 재고·고장공급0·손실·응답필드·무이력 null을 담당한다. 실제 이름별9개 서버 항목은 Confluence 분배 페이지에서 함께 추적한다.
- V5는 DB 담당의 설정/기초재고 데이터 migration이다. 검토·통합은 공동 진행하고 V5를 다시 만들거나 재번호하지 않는다. 이외의 새 migration은 V6부터, 적용된 과거 SQL 및 사용하지 않는 테이블/칼럼은 보존한다.
- 경계 테스트: 동일 항목0·1·2·3건(최소3건 제한 없음), 다른 항목/주의/미종료/음수시간 제외, 이력0건에도 정지예상 유지, 단가0은0원/설정안내와 무이력null 구분.
- 계산 예시: 재고300, 고장공급0, 소비6/분 →50분 소진. 동일항목 종료경보5건 평균70분이면 부족20분. HOURLY_OUTPUT360, UNIT_PRICE50000 →6000000원. 예시금액은 실제단가가 아니다.
- 단계 경계는 위험/주의 이하, 위험>=주의는422. 새 설정은 도움말·예측 응답·이벤트에 반영한다. 카운터 중복·리셋·1분집계, 계획생산량과 실측생산량 분리도 검증한다.
- 기존 ADMIN/USER·ACTIVE/CLOSED·즉시종료·논리삭제·Telegram 장애격리 규칙은 유지한다. 각 API 정상/인증/권한/검증/충돌 테스트와 전체 검증 후 구현 완료로 표시한다.

### 11.0 2026-11-18 기준정보 입력 길이를 ERD 에 맞춤 (A)

- 이유: v7 요청 길이가 DB 컬럼보다 길어 그 사이 길이는 저장 시 500 이 났다(프론트·B 확인 요청). migration 없이 API 를 ERD 길이로 줄인다.
- 기준: 라인 이름 50(line.name). 설비 코드 20·이름 100·종류 50·시리얼 50·통신 방식 20(equipment). 데이터 항목 이름 50·단위 20·0/1 표시 20(data_item).
- 영향 API: MST-002/003/006/007/011/012. 넘으면 400 VALIDATION_ERROR. DB 담당 확인 대상.
- 사진 조회: v7에는 photoUrl(10분 서명 URL)만 있고 사진을 주는 API가 없어 MST-018을 추가했다. photoUrl은 상대 주소(uri-reference)다.

### 11.1 2026-11-18 데이터 항목 계약 보완 (A)

- 이유: v7 MST-011 요청에 항목 코드가 없어 등록한 항목이 INT-001 dataItemCode(data_item.item_code, NOT NULL·설비별 UNIQUE)와 연결되지 않았다. 통계 참고값의 "최근" 구간도 정의가 없었다.
- 영향 API: MST-010~013(요청·응답 dataItemCode, MST-011 404/409, MST-012/013 400, 새 오류코드 3개, 통계 구간 7일). 다른 API와 DB migration 변경은 없다.
- 테스트: MasterDataItemApiTest(H2), MySqlMasterLineTest의 데이터 항목 흐름(MySQL, STF_TEST_MYSQL_URL 필요).
- 길이는 11.0 대로 ERD에 맞춘다(이름 50, 단위 20, 0/1 표시 20).
- MST-012에서 measurementThreshold·stateAlarmCondition 칸을 아예 보내지 않으면 기존 기준을 유지하고, 명시적으로 null 을 보내면 지운다(2026-11-18 사용자 결정).

### 11.2 2026-11-18 설비 현황 조회 (A, EQP-002~005)

- 판정은 B의 OVW-001과 같다: 상태 줄이 없는 설비는 COMMUNICATION_LOST, 이상 설비는 FAULT·CAUTION·COMMUNICATION_LOST, 측정값 valueStatus는 지금 값과 기준 비교(경보 먼저, 기준 포함), 상태값은 알람 발생 값이면 그 등급, 누적값은 null.
- 주요 측정값은 표시 순서가 앞선 측정값 3개다. CurrentDataItem은 value·measuredAt이 필수라 값을 받은 적 없는 항목은 목록에서 뺀다.
- 추가 필드(프론트 요청): ProcessSummary.representativeStatus, AbnormalMeasurement의 displayValue·cautionThreshold·criticalThreshold·thresholdDirection·decimalPlaces. 모두 선택 필드라 B의 OVW-001 응답 형식에는 영향이 없다.
- EquipmentDetailData.delayed는 의미가 정의되지 않아 아직 내보내지 않는다.

### 11.3 2026-11-18 측정 추세 조회 (A, MEAS-001)

- 집계 간격: 24시간 이하는 원본(모든 항목이 2000점 이하일 때), 7일 이하는 1분, 그보다 길면 1시간에서 시작하고 칸 수가 2000을 넘으면 다음 간격으로 올린다. 3초 수집이면 24시간 원본이 28,800점이라 v7 기본값만으로는 2000점 제한을 지킬 수 없기 때문이다. 모든 항목에 같은 간격을 쓴다.
- 칸 시각은 칸의 시작이며 Asia/Seoul 자정 기준으로 맞춘다. 측정값 평균, 상태값·누적값은 칸의 마지막 값이다.
- 통신 끊김은 equipment_status_history의 COMMUNICATION_LOST 구간을 조회 구간으로 자르고 합친다. 아직 끊겨 있으면 endedAt은 null이고, 각 끊김 시작 시각에 value=null 점을 넣는다.
- alarmMarkers는 구간 안에서 발생한 알람(발생 때 값), abnormalIntervals는 구간과 겹치는 알람의 발생~종료다.
- from>=to는 400 INVALID_PERIOD, dataItemIds 1~10개·중복 금지(400), 다른 설비·삭제한 항목은 404 DATA_ITEM_NOT_FOUND, 삭제한 설비는 404 EQUIPMENT_NOT_FOUND.
- 추세 메타정보(프론트 요청): thresholdDirection·decimalPlaces·zeroDisplayName·oneDisplayName. STOMP MeasurementEvent는 B 발행 형식이라 이번 변경에 포함하지 않는다.

### 11.4 2026-11-18 통신 현황 (A, COM-001/002)

- 통신 끊김은 수집 쪽 스케줄러가 equipment_state에 남긴 COMMUNICATION_LOST(마지막 수신 후 COMM_TIMEOUT_SEC 초과)를 쓴다. 상태 줄이 없는 설비(값을 받은 적 없음)도 끊김으로 보고(EQP·OVW-001과 같음) lastReceivedAt은 null이다. disconnectedSince는 상태 시작 시각, 상태 줄이 없으면 설비 등록 시각이다.
- 오늘 끊김 횟수·분은 오늘(Asia/Seoul 0시~지금)과 겹치는 COMMUNICATION_LOST 상태 이력이다. 어제부터 이어진 끊김도 1회로 세고 오늘 안의 길이만 더한다(분 미만 버림).
- COM-001 summary는 lineId 조건만 따르고 status 조건·페이지와는 상관없다. 없는 lineId는 0대(ALL_NORMAL)다. COM-002는 사용 중인 라인의 사용 중인 설비 전체다.
- 계약 보완: CommunicationEquipment.lastReceivedAt nullable.

## 12. 필드 사전

Y=필수, N=선택. nullable은 명시된 경우만 허용한다. allOf는 참조와 추가 필드 모두 적용한다. 단계·금액·무이력 상태를 구분하며 null을0으로 변환하지 않는다.

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
| lastReceivedAt | string(date-time) | Y | Y | - | 값을 받은 적 없는 설비는 null. 2026-11-18 보완 |
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
| lineName | string | Y | N | 길이≤50 | - |
| displayOrder | integer | Y | N | 값≥1 | - |
| lineType | LineType | Y | N | - | - |

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
| hasAbnormalEquipment | boolean | Y | N | - | FAULT·CAUTION·COMMUNICATION_LOST 설비가 있으면 true |
| representativeStatus | EquipmentStatus | N | Y | - | 공정 설비 중 가장 나쁜 상태. 설비가 없으면 null. 2026-11-18 추가 |

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
| valueStatus | string | N | Y | 허용 NORMAL, CAUTION, CRITICAL,  | - |
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
| threshold | number | N | N | - | 벗어난 쪽 기준(상태값은 알람 발생 값) |
| displayValue | string | N | Y | - | 표시 값. 2026-11-18 추가 |
| cautionThreshold | number | N | Y | - | 주의 기준. 2026-11-18 추가 |
| criticalThreshold | number | N | Y | - | 경보 기준. 2026-11-18 추가 |
| thresholdDirection | string | N | Y | 허용 ABOVE, BELOW | 2026-11-18 추가 |
| decimalPlaces | integer | N | N | 값≥0; 값≤10 | 2026-11-18 추가 |

### EquipmentSummary

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentId | Identifier | Y | N | - | - |
| equipmentCode | string | Y | N | 길이≤20 | - |
| equipmentName | string | Y | N | 길이≤100 | - |
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
| lineType | LineType | Y | N | - | - |

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
| thresholdDirection | string | N | Y | 허용 ABOVE, BELOW | 2026-11-18 추가 |
| decimalPlaces | integer | N | N | 값≥0; 값≤10 | 2026-11-18 추가 |
| zeroDisplayName | string | N | Y | - | 상태값 0 표시. 2026-11-18 추가 |
| oneDisplayName | string | N | Y | - | 상태값 1 표시. 2026-11-18 추가 |
| points | array<MeasurementPoint> | Y | N | - | 시각순. 끊김 시작마다 value=null 점 |
| alarmMarkers | array<AlarmMarker> | Y | N | - | - |
| disconnections | array<DisconnectionInterval> | Y | N | - | - |
| currentlyConnected | boolean | Y | N | - | - |
| abnormalIntervals | array<object> | N | N | - | 구간과 겹치는 알람의 발생~종료(미종료면 endedAt null) |
| bucketSeconds | integer | N | N | 값≥1 | 원본이면 항목 수집 주기, 집계면 적용한 칸 크기(초) |

### InventoryLine

현재 재고는 stock_history의 최신 줄. 대상 PART 라인만 반환. 시연은 기초300 + 라인 끝 생산 증가 - AS-04-1 완성 증가로 stock_history를 갱신. 재고 시스템과 시연 계산을 동시에 더하지 않는다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| quantity | integer(int64) | Y | Y | 값≥0 | 개 |
| consumptionPerMinute | number(double) | Y | Y | 값≥0 | 개/분 |
| productionPerMinute | number(double) | Y | Y | 값≥0 | 개/분 |
| calculatedAt | string(date-time) | Y | N | - | - |
| calculationStatus | string | Y | N | 허용 READY, INSUFFICIENT_DATA, COUNTER_RESET_PENDING, COUNTER_INCONSISTENT | - |
| lineType | LineType | Y | N | - | - |
| stage | string | Y | Y | 허용 MAINTAIN, CAUTION, DANGER,  | - |
| remainingMinutes | number | Y | Y | 값≥0 | - |
| assemblyStopExpectedAt | string(date-time) | Y | Y | - | - |
| stageThresholds | InventoryStageThresholds | Y | N | - | - |
| inventorySource | string | Y | N | 허용 INVENTORY_SYSTEM, DEMO_PRODUCTION | 재고 시스템에서 받은 stock_history 또는 시연용 생산 계산. 출처 표시 필드는 제안 계약 |

### InventoryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | array<InventoryLine> | Y | N | - | - |

### InventoryDetail

현재 재고·분당 입고·분당 소비·소진 예상·조립 정지 예상만 표시. appropriateInventory/cycleTimeSeconds/최대재고 비율/구매품 재고는 제공하지 않음.

상속: InventoryLine


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

복구 이력0건이어도 남은 시간·정지 예상·단계는 계산 가능한 한 유지한다. 복구 예상·평균·여유·부족·손실만 null. 평균0분은 유효한 표본이며 표본0건과 구분한다. 금액은 최종 원 단위 반올림.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| lineId | Identifier | Y | N | - | - |
| lineName | string | Y | N | - | - |
| currentInventory | integer(int64) | Y | Y | 값≥0 | - |
| netConsumptionPerMinute | number(double) | Y | Y | - | - |
| remainingMinutes | number | Y | Y | 값≥0 | - |
| stage | string | Y | Y | 허용 MAINTAIN, CAUTION, DANGER,  | - |
| assemblyStopExpectedAt | string(date-time) | Y | Y | - | - |
| sourceEquipmentId | integer(int64) | Y | Y | - | - |
| activeAlarmId | integer(int64) | Y | Y | - | - |
| recoveryExpectedAt | string(date-time) | Y | Y | - | 고장 발생 시각 + averageRepairMinutes. 이력0건이면 null |
| marginMinutes | number | Y | Y | - | 정지 예상-복구 예상, 분. 둘 중 하나가 없으면 null |
| marginStatus | string | Y | Y | 허용 AVAILABLE, SHORTAGE,  | - |
| hourlyProduction | number | Y | Y | 값≥0 | HOURLY_OUTPUT 조립 기준값. 실측값으로 대체 금지 |
| unitPrice | number | Y | Y | 값≥0 | UNIT_PRICE 원/개. 0도 유효한 초기값 |
| expectedLossAmount | number | Y | Y | 값≥0 | shortageMinutes/60 × hourlyProduction × unitPrice. 복구/정지 예상 없으면 null. 금액0은 데이터 없음과 다름 |
| lineType | LineType | Y | N | - | - |
| repairHistoryCount | integer | Y | N | 값≥0 | 동일 설비·동일 항목의 유효한 종료 경보 표본 수. 0일 때 다른 설비/항목으로 대체하지 않음 |
| averageRepairMinutes | number | Y | Y | 값≥0 | closedAt-occurredAt의 분 평균. 표본0건이면 null, 1~2건도 그대로 평균 |
| shortageMinutes | number | Y | Y | 값≥0 | max(복구 예상-정지 예상,0), 분. 복구/정지 예상 없으면 null |
| unitPriceConfigured | boolean | Y | N | - | UNIT_PRICE > 0. false이면 개당 금액을 설정해 주세요 안내 |
| forecastStatus | ForecastStatus | Y | N | - | - |
| stageThresholds | InventoryStageThresholds | Y | N | - | - |

### AssemblyStopForecastData

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| representativeLineId | integer(int64) | N | Y | - | - |
| earliestAssemblyStopExpectedAt | string(date-time) | N | Y | - | - |
| lines | array<LineStopForecast> | Y | N | - | - |
| calculatedAt | string(date-time) | Y | N | - | 동일 계산 snapshot의 서버 기준 시각 |

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
| dataItemId | Identifier | Y | N | - | - |
| dataItemName | string | N | N | 길이≤100 | - |
| alarmMessage | string | Y | N | 길이≤1000 | - |
| cause | string | N | N | 길이≤1000 | - |
| currentValue | number | N | Y | - | - |
| occurredValue | number | N | N | - | - |
| thresholdValue | number | N | Y | - | 발생 당시 적용 기준의 스냅샷. 현재 항목 기준2개와 구분 |
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
| cautionThreshold | number | Y | Y | - | 항목 현재 설정의 주의 기준. STATE/CUMULATIVE는 null |
| criticalThreshold | number | Y | Y | - | 항목 현재 설정의 경보 기준. STATE/CUMULATIVE는 null |
| thresholdDirection | string | Y | Y | 허용 ABOVE, BELOW,  | 항목 현재 설정의 임계값 방향. 값의 크기로 추측하지 않음 |
| decimalPlaces | integer | Y | Y | 값≥0; 값≤10 | 항목에 등록된 소수 자릿수. STATE/CUMULATIVE는0 |

### AlarmPageData

발생 중 전체·등급별 건수는 필터와 페이지에 무관한 메뉴 전체 집계다. 오늘/전일 발생은 lineId·equipmentId·grade 조건을 적용하고 KST 자정으로 구분한다. processStatus와 페이지는 items에 적용한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| yesterdayOccurredCount | integer | Y | N | 값≥0 | KST 전일 발생 건수 |
| activeCautionCount | integer | Y | N | 값≥0 | 전체 발생 중 CAUTION |
| activeCriticalCount | integer | Y | N | 값≥0 | 전체 발생 중 CRITICAL. 두 등급 합은 activeCount |
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
| averageRepairMinutes | number(double) | Y | Y | 값≥0 | - |
| averageFailureIntervalMinutes | number(double) | Y | Y | 값≥0 | - |
| items | array<FaultHistoryItem> | Y | N | - | - |

### FaultHistoryResponse

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| success | boolean | Y | N | 허용 true | - |
| data | FaultHistoryData | Y | N | - | - |

### AlarmResponseStatistics

종료 표본이 없으면 최장 시간·설비 ID·이름은 null이다. 같은 최장 시간이면 alarmId가 작은 이력의 설비를 선택한다. 발생 중 등급별 건수는 이 API의 기간·라인 조건 안의 수이며 기간 제한이 없는 ALM-001 메뉴 수와 구분한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| activeCautionCount | integer | Y | N | 값≥0 | 조회 조건 내 ACTIVE CAUTION |
| activeCriticalCount | integer | Y | N | 값≥0 | 조회 조건 내 ACTIVE CRITICAL |
| longestActionEquipmentId | integer(int64) | Y | Y | 값≥1 | 최장 종료 이력의 설비 ID |
| longestActionEquipmentName | string | Y | Y | - | 최장 종료 이력의 설비명 |
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
| averageRepairMinutes | number(double) | Y | Y | 값≥0 | - |
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
| equipmentCode | string | Y | N | 길이≥1; 길이≤20 | 등록 후 수정 불가 |
| equipmentName | string | Y | N | 길이≥1; 길이≤100 | - |
| equipmentType | string | Y | N | 길이≥1; 길이≤50 | - |
| serialNumber | string | N | Y | 길이≤50 | - |
| lineId | Identifier | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |
| communicationMethod | string | Y | N | 길이≥1; 길이≤20 | - |
| photo | string(binary) | N | Y | - | - |
| registeredAt | string(date-time) | N | N | - | 등록 화면 입력값. 등록 시 생략하면 서버 현재 시각. 수정 시 생략하면 기존 값 유지. |

### EquipmentUpdateRequest

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| equipmentName | string | Y | N | 길이≥1; 길이≤100 | - |
| equipmentType | string | Y | N | 길이≥1; 길이≤50 | - |
| serialNumber | string | N | Y | 길이≤50 | - |
| lineId | Identifier | Y | N | - | - |
| processOrder | integer | Y | N | 값≥1 | - |
| communicationMethod | string | Y | N | 길이≥1; 길이≤20 | - |
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
| lineName | string | Y | N | 길이≥1; 길이≤50 | ERD line.name varchar(50). 2026-11-18 조정 |
| displayOrder | integer | Y | N | 값≥1 | - |
| lineType | LineType | N | N | 기본 PART | 추가 계약 필드. 생략하면 PART(제안값); 기존 ASSEMBLY는 유지 |

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

valueType에 따라 측정값/상태값/누적값 전용 필드를 검증한다. MEASUREMENT는 0/1 표시·stateAlarmCondition을 받지 않고 decimalPlaces를 비우면 1이다. STATE는 unit·measurementThreshold를 받지 않고 decimalPlaces는 0이다. CUMULATIVE는 두 기준과 0/1 표시를 받지 않고 decimalPlaces는 0이다. 맞지 않으면 400이다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| dataItemCode | string | N | N | 길이≥1; 길이≤50; 패턴 `^[a-z][a-z0-9_]*$` | INT-001 dataItemCode. MST-011 필수, MST-012 무시. 2026-11-18 추가 |
| dataItemName | string | Y | N | 길이≥1; 길이≤50 | ERD data_item.name |
| valueType | DataValueType | Y | N | - | - |
| unit | string | N | Y | 길이≤20 | - |
| collectionIntervalSeconds | integer | Y | N | 값≥1 | - |
| decimalPlaces | integer | N | Y | 값≥0; 값≤10 | - |
| zeroDisplayName | string | N | Y | 길이≤20 | - |
| oneDisplayName | string | N | Y | 길이≤20 | - |
| measurementThreshold | MeasurementThreshold | N | Y | - | MST-012에서 생략하면 유지, null이면 삭제 |
| stateAlarmCondition | StateAlarmCondition | N | Y | - | MST-012에서 생략하면 유지, null이면 삭제 |

### ThresholdWriteRequest


### ThresholdStatistics

최근 값 범위는 최근 7일(오늘 포함, Asia/Seoul) 측정값이며 MEASUREMENT 항목만 값이 있다. 알람 수는 지금부터 30일 전 이후 발생 건수다(2026-11-18 A 제안값).

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
| dataItemCode | string | Y | N | - | INT-001 dataItemCode. 2026-11-18 추가 |
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

communicationLossSeconds=COMM_TIMEOUT_SEC, alarmConsecutiveCount=ALARM_STREAK_COUNT, 주의=STOCK_LEVEL_MIN, 위험=STOCK_DANGER_MIN. 정상 복귀 횟수는 연속 횟수와 동일. 0 < 위험 < 주의를 서버에서 검증하고 실패 시422.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| communicationLossSeconds | integer | Y | N | 값≥10; 값≤300; 기본 30 | - |
| alarmConsecutiveCount | integer | Y | N | 값≥1; 값≤10; 기본 3 | - |
| inventoryWarningMinutes | integer | Y | N | 값≥5; 값≤120; 기본 30 | STOCK_LEVEL_MIN |
| inventoryDangerMinutes | integer | Y | N | 값≥1; 값≤60; 기본 10 | STOCK_DANGER_MIN. inventoryWarningMinutes보다 작아야 함 |

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
| unitPrice | number | Y | N | 값≥0; 값≤100000000; 기본 0 | UNIT_PRICE, 원/개. 초기0, 0이면 화면에 개당 금액 설정 안내. 상한은 프로젝트 제안값 |
| speedWindowMinutes | integer | Y | N | 값≥5; 값≤60; 기본 10 | RATE_WINDOW_MIN. production_log 속도 계산 구간(분) |
| hourlyProduction | integer | Y | N | 값≥1; 값≤1000; 기본 360 | HOURLY_OUTPUT. 실측 생산량이 아닌 MES 계획값으로 가정한 조립 기준 생산량(개/시간) |

### CalculationSettings

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| unitPrice | number | Y | N | 값≥0; 값≤100000000; 기본 0 | UNIT_PRICE, 원/개. 초기0, 0이면 화면에 개당 금액 설정 안내. 상한은 프로젝트 제안값 |
| speedWindowMinutes | integer | Y | N | 값≥5; 값≤60; 기본 10 | RATE_WINDOW_MIN. production_log 속도 계산 구간(분) |
| hourlyProduction | integer | Y | N | 값≥1; 값≤1000; 기본 360 | HOURLY_OUTPUT. 실측 생산량이 아닌 MES 계획값으로 가정한 조립 기준 생산량(개/시간) |

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

message는 실제 발송한 고정 테스트 문구다. 화면은 문구를 복제하지 않고 이 값을 표시한다. 연결 없음은422, 공급자 발송 실패는200의 result=FAILURE와 failureReason이다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| message | string | Y | N | - | 설비 통합관제 테스트 메시지. 연결 확인용 테스트 메시지입니다 |
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

### AlarmHourlyCount

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| from | string(date-time) | Y | N | - | 포함 경계 |
| to | string(date-time) | Y | N | - | 제외 경계 |
| cautionCount | integer | Y | N | 값≥0 | 이 구간의 CAUTION 발생 |
| criticalCount | integer | Y | N | 값≥0 | 이 구간의 CRITICAL 발생 |

### RecentAlarmListResponse

data와 meta는 기존 페이지 목록이다. 차트는 페이지를 세지 않고 hourlyCounts를 사용한다. windowFrom=조회 시각-24시간, windowTo=조회 시각이며 [windowFrom,windowTo)의 1시간 구간24개를 순서대로 반환한다. 구간별 from 이상·to 미만으로 세며 빈 구간은0이다. data와 차트는 같은 기준 시각으로 계산한다.

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| windowFrom | string(date-time) | Y | N | - | 24시간 조회 시작 |
| windowTo | string(date-time) | Y | N | - | 조회 종료. 해당 시각은 제외 |
| hourlyCounts | array<AlarmHourlyCount> | Y | N | 정확히24개 | 전체 집계. page/size와 무관 |
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

### LineType

DB line.line_type. PART=부품 라인, ASSEMBLY=조립 라인

허용값: `PART`, `ASSEMBLY`

### InventoryStageThresholds

| 필드 | 타입 | 필수 | Nullable | 제약 | 설명 |
|---|---|---|---|---|---|
| inventoryWarningMinutes | integer | Y | N | 값≥5; 값≤120 | 현재 STOCK_LEVEL_MIN |
| inventoryDangerMinutes | integer | Y | N | 값≥1; 값≤60 | 현재 STOCK_DANGER_MIN. 주의보다 작음 |

### ForecastStatus

NO_DEPLETION=소비<=공급으로 소진 없음. NO_RECOVERY_HISTORY=소진 예측은 있으나 동일 설비·항목 종료 경보0건. INSUFFICIENT_DATA=재고/카운터/설정 등 필수 입력 부족. 복수 사유가 겹치면 입력부족→소진없음→이력없음 순서.

허용값: `READY`, `NO_DEPLETION`, `NO_RECOVERY_HISTORY`, `INSUFFICIENT_DATA`

## 13. 이번 개정 검증

- OpenAPI3.0.3 lint: 오류0·경고0. 경로57·operation67, API ID중복0.
- FR75건 및 API필드사전 연결, 금지된 대체값/삭제필드/읽음경로 부재를 확인한다.
- 별도 문서 검증 checkout 테스트: 453건 중446통과·1실패·6건너뜀. 기준 커밋 caf97b9에 이번 문서3개만 적용하고 gradlew.bat test --rerun-tasks를 실행했다. OpenApiStompContractTest.sevenStompSchemasMatchTheActualPublishedRecordFields는 이벤트7개를 기대하지만 현행 명세가6개여서 실패했다(expected 7, actual 6). MySqlBackendBTest 6개는 MySQL 통합 조건 미충족으로 건너뛰었다. 명세에 맞춘 구현·테스트 전환이 남아 있으므로 전체 테스트 통과 또는 서버 v7 구현 완료로 표시하지 않는다. 진행 중인 다른 개발자의 코드 변경은 이 검증에 포함하지 않았다.
