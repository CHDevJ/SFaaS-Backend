# 2026-11-18 backend / Back_B 통합 검증

기존 로컬 `C:\Users\한국전파진흥협회\Desktop\스마트팩토리\SFaas\stf`의 `Back_B`에서 최신 backend `2e9d150`를 통합했다. 별도 복제 없이 기존 Telegram·Node-RED 실행 설정과 A의 설비 사진 설정을 모두 유지했다.

## 반영 내용

- backend의 A 통신 현황·설비 조회·추세·기준정보·설정 변경 이력과 공통 계약 변경을 통합했다.
- B의 비공개 로컬 Telegram 설정, 실제 발송 확인 기록, Node-RED 수집 프로필·실행/방화벽 도구·인계 문서를 유지했다.
- `scripts/ci/report-tests.py`가 `MySqlMasterLineTest`도 집계하고 필수 실행 여부를 확인하게 했다.
- `scripts/manual-test/Smoke-Test.ps1`에 필수 from/to 기간을 KST 반개방 구간으로 전달하고 +09:00을 URL 인코딩하도록 반영했다. 새 A 조회와 관리자 권한, 필수 날짜 누락 400 응답을 확인한다.
- A 업무 로직은 원격 변경을 그대로 통합했으며 별도로 수정하지 않았다. V1~V6 SQL의 SHA-256은 통합 전후 동일하다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| 관련 Gradle 회귀 | 131 PASS / RUN-84E0976BE173 |
| 전체 `.\gradlew.bat clean test --no-daemon` | 95클래스 626 PASS / RUN-1732DDB7DCE4 |
| 실제 MySQL | 17 PASS, 위 전체 건수에 포함 |
| 전체 실패 / 오류 / 건너뜀 | 0 / 0 / 0 |
| CI 결과 집계 검증 | 7개 시나리오 PASS, Gradle 건수와 별도 |
| 실제 HTTP | 28 PASS / 0 FAIL, Gradle 건수와 별도 |
| 누적 자동 테스트 | 29회 / 9398건 시도, 과거 실패 기록 포함 |

실제 MySQL 검증은 운영·수동 시연 데이터와 분리된 `stf_b_test` 및 테스트별 임시 스키마를 사용했다. 현재 개발 DB를 초기화하지 않았다. 자동 테스트의 Mock Telegram 결과를 이번 실제 발송 결과로 간주하지 않는다. 외부 PC Node-RED/PLC의 실제 송신은 이번 검증에서 실행하지 않았다.

## 실제 서버 확인

- 최신 통합 JAR로 수집 서버를 재기동했다. Swagger: http://localhost:8081/swagger-ui.html
- 기존 수집 주소: `POST http://192.168.203.18:8081/internal/v1/measurements` (PC IP 변경 시 주소도 변경)
- 최초 수동 확인의 7건 실패는 날짜 누락 요청의 400 응답을 200으로 기대한 검증 스크립트 문제였다. 수정 전후 결과를 별도 JSON으로 보존했다.
- [트러블슈팅 기록](https://sfaas5-team.atlassian.net/wiki/spaces/SF5/pages/5668865)

## 근거와 보안

- `docs/test-reports/backend-integration-verification-2026-11-18.json`
- `docs/test-reports/backend-integration-runtime-before-fix-2026-11-18.json`
- `docs/test-reports/backend-integration-runtime-2026-11-18.json`
- 누적 `test-run-history.json`과 기존 단위테스트 엑셀 보고서
- `.env*` 실파일, `secrets/`, 실행 로그는 Git 제외 상태를 유지한다. 실제 비밀번호·JWT·봇 토큰·내부 API 키를 문서/커밋에 기록하지 않는다.
