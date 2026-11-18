# Backend CI

`.github/workflows/backend-ci.yml`은 다음 이벤트에 자동 실행된다.

| 이벤트 | 브랜치 |
|---|---|
| Pull request 생성·재열기·추가 커밋 | 대상(base)이 main, develop, backend |
| Push | main, develop, backend, Back_B |

Back_B 푸시와 backend 대상 PR을 각각 검사하며, PR 병합 후 backend 푸시도 자동 검사한다. 같은 이벤트·브랜치의 오래된 실행은 새 실행이 시작되면 취소한다.

## 실행 내용

1. Java 21을 준비하고 저장소의 Gradle Wrapper를 사용한다.
2. 해당 Actions 실행에만 사용하는 MySQL 8.4 서비스와 stf_b_test DB를 생성하고 건강 상태를 기다린다.
3. `STF_TEST_MYSQL_URL`을 제공해 MySqlBackendBTest가 건너뛰어지지 않도록 한 뒤 `./gradlew clean test --no-daemon`을 실행한다. 테스트는 별도 무작위 검증 스키마에서 V1~V4를 적용한다.
4. JUnit XML로 테스트·통과·실패·오류·건너뜀·MySQL 실행 수를 Actions Summary에 기록한다. XML이 없거나 테스트가 0건이거나 실패·오류·건너뜀 또는 MySQL 미실행이 있으면 실패 처리한다.
5. 성공·실패 시 모두 생성된 JUnit XML과 HTML 보고서를 Actions artifact에 7일 보관한다.

실행 확인: GitHub의 Actions → Backend CI 또는 PR의 Checks → test. HTML 보고서는 해당 실행의 backend-test-reports artifact를 내려받아 확인한다.

## 설정과 비밀정보

별도 GitHub Secret을 등록할 필요가 없다. CI MySQL은 실행마다 새로 만들어지는 테스트 전용 서비스로만 빈 암호를 허용한다. 이를 개발·공유·운영 DB 설정에 적용하지 않는다. 기존 로컬 DB나 Docker 볼륨은 변경하지 않는다.

실제 DB 비밀번호·JWT 서명키·Telegram 봇 토큰과 `.env`/`secrets` 파일은 workflow에 전달하거나 커밋하지 않는다. 애플리케이션 테스트의 인증 설정은 기존 src/test/resources의 테스트 전용 값을 사용하고 실제 외부 메시지는 보내지 않는다. artifact 업로드 범위는 테스트 결과 경로로 제한한다.

## 로컬 검증

기존 격리 MySQL을 사용하는 PowerShell 실행:

```powershell
.\scripts\manual-test\Run-Tests.ps1
python .\scripts\ci\report-tests.py
```

워크플로 문법은 actionlint로 검사하고, 실제 Linux 실행은 Back_B push·backend 대상 PR·병합 후 backend push의 Actions 결과로 확인한다. 이 문서는 브랜치 보호 규칙을 설정하지 않으며, 병합 전에는 PR의 검사 성공을 확인한다.
