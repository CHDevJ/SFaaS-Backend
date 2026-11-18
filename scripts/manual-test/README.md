# 로컬 수동 테스트 v7

기존 로컬 `stf` 프로젝트의 `Back_B`에서 실행한다. 전용 DB는 127.0.0.1:3308/stf_b_manual, Redis는 6380, 서버는 8081이다. 공용 DB와 볼륨은 초기화하지 않는다.

## 실행

```powershell
.\scripts\manual-test\Start-ManualTest.ps1
.\scripts\manual-test\Get-ManualTestToken.ps1 -Role ADMIN -CopyToClipboard
```

Swagger: <http://localhost:8081/swagger-ui.html>. Authorize에 복사한 Access Token을 붙여넣는다. USER 확인은 `-Role USER`를 사용한다. 토큰은 실제 로그인 API로 발급하며 30분 유효하다. Swagger의 영구 인증 저장은 꺼져 있다.

최초 실행 시 Flyway가 V1~V6를 적용한다. 기존 수동 서버가 구버전이면 `Stop-ManualTest.ps1`로 해당 서버만 종료한 뒤 다시 시작한다. 이번 코드 변경 작업은 기존 수동 DB 마이그레이션이나 서버 재시작을 자동 수행하지 않았다. V6는 기존 ACTIVE 중복을 삭제하지 않으므로 중복이 있으면 데이터를 검토하고 해결한 뒤 실행한다.

연습 계정은 badmin01(ADMIN), bmaint01·bline01(USER)이다. 비밀번호는 무작위로 만든 `.env.manual-test`에만 있으며 기존 계정 비밀번호는 재시작으로 덮어쓰지 않는다. `Get-ManualTestLogin.ps1`은 Swagger에 넣을 로그인 JSON을 출력하므로 출력물을 공유하지 않는다.

## 확인

1. GET `/api/v1/overview/equipment-layout`로 설비 상태를 확인한다.
2. GET `/api/v1/inventory`, `/api/v1/overview/assembly-stop-forecast`로 재고와 예측을 확인한다. 실시간 수집이 없으면 데이터 부족이 정상이다.
3. GET `/api/v1/alarms`에서 ID를 받아 상세를 조회한다.
4. 조치 완료는 POST `/api/v1/alarms/{alarmId}/actions`에 equipmentText·actionContent·선택 cause를 전송한다. 즉시 CLOSED가 되며 정상 복귀만으로는 종료하지 않는다.
5. 조회 점검은 `Smoke-Test.ps1`을 실행한다. 이 스크립트는 업무 데이터를 변경하거나 텔레그램을 발송하지 않는다. 로그인은 세션과 성공 로그인 시각을 갱신한다.

삭제된 당번·부품 교체·확인·효과 확인 API는 사용하지 않는다. 테스트용 데이터는 최초 1회만 넣는다. manual-test는 실제 봇 발송·PLC 수집을 기본 비활성화한다. Refresh의 Secure·HttpOnly·SameSite=Strict 쿠키를 브라우저에서 검증하려면 HTTPS 환경을 준비한다.

## 테스트와 누적 기록

```powershell
.\scripts\manual-test\Run-Tests.ps1
```

별도 임시 MySQL 스키마에서 전체 Gradle 테스트를 수행한다. 성공·실패·건너뜀과 개별 메서드를 `docs/test-reports/test-run-history.json`에 중복 없이 누적한다. 비밀번호·토큰·요청 본문·실패 스택 원문은 기록하지 않는다. 엑셀 보고서에는 기존 v5 결과를 보존하고 v7 실행별 결과를 추가한다. 일반 `gradlew test`만 실행한 경우에는 `scripts/test-report/Update-TestHistory.ps1`로 해당 결과를 기록할 수 있다.
