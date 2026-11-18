# v7 STOMP 계약 수정 및 전체 테스트 검증

- 검증일: 2026-11-17 (Asia/Seoul)
- 기준: `Back_B`의 문서 개정 커밋 `c155b55`에 이번 STOMP 변경만 적용한 별도 checkout
- 명세: `docs/openapi.yaml` 7.0.0, 사용자에게 공개하는 토픽 6개

기존 검사는 토픽 7개 및 components 참조 스키마만 가정했다. v7의 인라인 스키마를 읽도록 바꾸고 실제 `SimpMessagingTemplate`에 전달되는 객체를 JSON으로 직렬화하여 검사한다. 기대 개수만 수정하지 않고 실제 발행 필드와 값도 계약에 맞췄다.

## 수정 범위

- 공개 이벤트의 필수 필드, 타입, enum, 날짜 형식, 숫자 범위, nullable 및 중첩 스키마를 검증한다. 인라인 및 참조 스키마를 모두 처리한다.
- 알람 이벤트명을 CREATED/GRADE_ESCALATED/VALUE_RECOVERED/CLOSED로 변환하고 상태는 ACTIVE/CLOSED를 사용한다.
- `/topic/alarm-counts`는 모든 미종료 알람의 `activeCount`를 조회한다. 종전 미확인 수를 이름만 바꿔 전달하지 않는다. 삭제된 설비는 제외한다.
- 재고 이벤트는 현재 설정의 `stageThresholds`와 동일한 기준으로 계산한 단계를 보낸다. 기본값이 아닌 주의45분/위험15분의 경계도 검증한다.
- 개인 큐의 수신 사용자 ID는 라우팅에만 사용하고 payload에는 v7 필드만 담는다. 읽음 수, 내부 메시지 및 과거 담당 지정 이벤트를 공개하지 않는다.
- 제거된 효과 확인 토픽은 발행하지 않는다. 기존 내부 이벤트/outbox 형식과 DB migration은 변경하지 않는다.

## 실행 결과

관련 STOMP·Telegram 장애격리·JDBC 회귀 검사를 먼저 통과한 뒤 독립 MySQL 8.4를 사용하여 전체 검사를 실행했다. 이 DB는 로컬 loopback 임의 포트의 임시 컨테이너이며 기존 개발 DB를 사용하지 않았다.

```powershell
# STF_TEST_MYSQL_URL은 독립 MySQL의 stf_b_test 데이터베이스를 가리킨다.
.\gradlew.bat clean test --no-daemon
python scripts/ci/report-tests.py
```

| 항목 | 결과 |
|---|---:|
| 테스트 클래스 | 72 |
| 전체 테스트 | 485 |
| 통과 | 485 |
| 실패 / 오류 / 건너뛰기 | 0 / 0 / 0 |
| STOMP 계약 검사 (전체에 포함) | 29 |
| MySQL 통합 검사 (전체에 포함) | 6 |

Gradle의 `BUILD SUCCESSFUL`과 CI 보고서 검사 스크립트의 종료 코드0을 확인했다. 원본 작업 폴더의 별도 진행 중인 변경은 이 커밋·검증에 포함하지 않았다. 이 결과는 이번 STOMP 경계와 기존 회귀 테스트의 통과를 뜻하며 v7의 모든 REST·계산 업무 구현 완료를 뜻하지 않는다.
