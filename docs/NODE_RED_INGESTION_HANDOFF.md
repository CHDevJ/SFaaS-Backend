# Node-RED 담당자 전달용 수집 API

이 문서는 `docs/openapi.yaml` v7 INT-001과 현재 로컬 테스트 DB를 기준으로 한다. 내부 API 키는 문서에 포함하지 않는다.

## 접속 정보

| 항목 | 값 |
| --- | --- |
| 수신 서버 | 백엔드 개발자 PC, 현재 Wi-Fi IP `192.168.203.18` |
| HTTP Method | `POST` |
| API URL | `http://192.168.203.18:8081/internal/v1/measurements` |
| Content-Type | `application/json` |
| 인증 헤더 | `X-Internal-Api-Key: 별도로 전달받은 내부 키` |
| 권장 전송 주기 | 설비별 3초 묶음 |
| 연결 성공 | HTTP 202, `success: true` |
| 통신 끊김 | 마지막 유효 수신 후 30초 초과 |

이 주소는 2026-11-18에 확인한 값이다. 다른 Wi-Fi로 변경하거나 IP가 바뀌면 수신 PC에서 Start-NodeRedReceiver.ps1이 출력하는 주소로 갱신한다. 다른 PC의 Node-RED에서는 localhost 대신 위 수신 PC 주소를 사용한다. 방화벽 허용 범위는 같은 서브넷이다. 실제 담당 PC에서의 접속은 아래 확인 절차로 검증한다.

## JSON 요청 예시

```json
{
  "messageId": "nr-고유한배치ID",
  "equipmentCode": "OR-01-1",
  "measuredAt": "2026-11-18T13:00:00.000+09:00",
  "values": [
    {"dataItemCode": "forming_load", "valueType": "MEASUREMENT", "value": 250.0},
    {"dataItemCode": "die_temp", "valueType": "MEASUREMENT", "value": 100.0},
    {"dataItemCode": "run_signal", "valueType": "STATE", "value": 1},
    {"dataItemCode": "prod_count", "valueType": "CUMULATIVE", "value": 0}
  ]
}
```

시간·ID·측정값·누적 수는 형식 예시다. 새 수집 때마다 실제 측정 시각과 고유 ID를 사용하고 실제 장비 값을 넣는다. 재전송은 최초 요청의 ID·시각·본문을 그대로 유지한다. 예시의 누적 수 0을 반복 전송해 실제 생산 카운터를 대체하지 않는다.

| 필드 | 규칙 |
| --- | --- |
| messageId | 1~200자. 신규 배치마다 고유, 같은 배치 재전송만 재사용. 24시간 중복 제거 |
| equipmentCode | DB에 등록된 설비 코드. 예: OR-01-1 |
| measuredAt | ISO 8601 시각. `+09:00` 또는 `Z`처럼 시간대 필수 |
| values | 동일 설비의 등록된 항목 1~500개. 한 요청에서 항목 코드 중복 금지 |
| dataItemCode | 설비에 등록된 항목 코드와 정확히 일치 |
| valueType | 등록된 종류: MEASUREMENT, STATE, CUMULATIVE |
| value | JSON 숫자. 문자열·null·NaN·Infinity는 사용하지 않음 |

STATE는 0/1, CUMULATIVE는 0 이상의 정수다. MEASUREMENT는 유한한 숫자다. 항목 하나가 잘못되면 해당 배치 전체가 거절된다. 새 ID로 이미 저장된 동일 항목·동일 measuredAt을 다시 보내면 충돌하므로 재전송 시 원본 ID를 유지한다. JWT 로그인 토큰과 Telegram 봇 토큰은 이 API에 사용하지 않는다.

## 응답

```json
{
  "success": true,
  "data": {
    "messageId": "nr-고유한배치ID",
    "acceptedAt": "2026-11-18T13:00:00+09:00",
    "duplicate": false
  }
}
```

| 상태 | 의미와 처리 |
| --- | --- |
| 202, duplicate=false | 신규 배치 처리 성공 |
| 202, duplicate=true | 이미 처리한 ID. 성공으로 취급하고 새 ID로 재전송하지 않음 |
| 400 | JSON 또는 필수 입력 형식 확인 |
| 401 | 내부 API 키 누락·불일치. 실제 키 값을 로그에 남기지 않음 |
| 404 | 등록 설비 코드 또는 호출 경로 확인 |
| 409 | 이미 저장된 측정 시각 충돌. 새 ID로 기존 데이터를 반복 전송하지 않음 |
| 422 | 항목 코드·종류·값 확인. 처리 중 메시지라면 원본 배치로 잠시 후 재시도 |
| 연결 실패 | 백엔드 실행, IP·포트, 같은 네트워크와 방화벽 확인 |

## Node-RED 설정

Node-RED가 실행되는 PC에 `STF_INGESTION_URL`을 위 URL로, `STF_INTERNAL_API_KEY`를 별도로 전달받은 키로 설정하고 런타임을 재시작한다. 키는 Git·프론트 환경변수·공개 flow JSON에 넣지 않는다.

흐름은 수집 노드 → 형식 변환 Function → HTTP request 노드다. Function 예시는 `docs/examples/node-red-measurement-function.js`이며 HTTP request는 `msg.method`, `msg.url`을 사용하고 응답을 JSON으로 받도록 설정한다. Debug에는 `msg.statusCode`, `msg.payload`만 확인하고 인증 헤더가 들어 있는 전체 msg를 출력하지 않는다.

다른 PC의 PowerShell에서 먼저 확인한다.

```powershell
Test-NetConnection 192.168.203.18 -Port 8081
```

`TcpTestSucceeded: True`를 확인하고 실제 배치 1건을 전송한다. HTTP 202와 화면의 마지막 수신 시각·상태 변경을 확인한다. 계속 정상 통신으로 표시하려면 3초 간격으로 실제 데이터를 보내야 한다. 한 번 보내고 중단하면 30초 뒤 통신 끊김이 되는 것이 정상이다.

## 백엔드 실행 담당자

기존 로컬 stf 프로젝트에서 실행한다. 새로 복제하거나 DB를 초기화하지 않는다.

```powershell
.\scripts\Start-NodeRedReceiver.ps1
```

스크립트가 Git 제외 키 파일을 준비하고 빌드한 뒤, 소유권이 확인된 기존 8081 테스트 서버만 재시작한다. MySQL 3308·Redis 6380·JWT·Telegram 설정은 그대로 사용한다. IntelliJ에서는 활성 프로필을 `backend-b,manual-test,node-red-local`, 작업 디렉터리를 stf 루트로 둔다. 스크립트로 실행한 서버에서 IntelliJ로 전환할 때는 기존 8081 인스턴스를 먼저 종료한다.

방화벽은 관리자 PowerShell에서 다음 스크립트로 같은 서브넷의 TCP 8081 한 개만 허용한다.

```powershell
.\scripts\Enable-NodeRedReceiverFirewall.ps1
```

키를 담당자에게 별도로 전달할 때만 로컬에서 클립보드 복사를 실행한다. 키 원문은 콘솔에 출력하지 않는다.

```powershell
.\scripts\Setup-LocalIngestion.ps1 -CopyKeyToClipboard
```

등록된 설비·항목 목록은 함께 제공한 `node-red-data-items.json`을 참고한다. 이 목록은 현재 테스트 DB 기준이며 실제 PLC 주소·단위·태그 매핑은 Node-RED 담당자가 장비 설정과 대조한다.
