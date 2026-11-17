## 2026-11-17 최신 요구사항과 v6 전환

현재 구현 목표는 `docs/openapi.yaml` v6.0.0과 `docs/API_SPEC.md`다. 2026-11-17 요구사항정의서·기능목록·기능 변경·화면설계·공통동작·HTML/PNG를 반영했다. 기존 Back_B 코드와 `docs/BACKEND_B_REMAINING.md`, `docs/BACKEND_B_INTEGRATION.md`는 v5 구현 이력이며 v6 완료 증거로 사용하지 않는다. 문서 개정 요청만으로 기존 구현·DB를 자동 삭제하거나 마이그레이션하지 않는다.

- 권한은 `ADMIN`, `USER` 두 가지다. 기존 직책 3종·파생 권한 모델을 새 API에 사용하지 않는다.
- 알람은 `ACTIVE`, `CLOSED`다. 조치 완료 시 즉시 종료하며 정상 복귀는 값 상태만 바꾼다.
- 확인·담당 지정·추천·오경보·효과 확인·재발·재알림·부품 교체·당번·손실 방지 API는 삭제 범위다. 삭제 API ID는 재사용하지 않는다.
- 재고는 라인 끝 생산 누적 카운터와 조립 투입 누적 카운터가 기준이다. INT-002 생산 delta 이벤트를 재도입하지 않는다.
- 예상 조치시간은 같은 설비·같은 dataItemId의 과거 종료 알람이 3건 이상이면 그 평균, 3건 미만이면 같은 설비 전체 종료 알람 평균, 기록이 없으면 defaultRecoveryMinutes(초기30분)를 적용한다. 원인 문구로 표본을 묶지 않는다.
- 메뉴 발생 중 수와 개인 읽지 않은 수는 별도다. 텔레그램은 연결된 전체 계정에 발생·등급상승만 발송한다.
- 분배는 A 41개/B 28개 operation이며 SYS/AUD는 A 소유다. 세부 목록은 API_SPEC의 API별 담당과 Confluence 분배 페이지를 따른다.

## 현재 문서 상태

- 적용일: 2026-11-17
- `docs/API_SPEC.md`는 2026-11-17 자료 6개를 통합한 **API 명세서 v6.0.0**이다. 기능75건, NFR13건, IFR4건을 연결한다.
- `docs/openapi.yaml`은 `version: 6.0.0`, `x-specification-status: approved-project-proposal`인 구현 계약이다.
- `x-implementation-status: contract-updated-implementation-migration-required`는 기존 v5 구현의 전환이 필요함을 뜻한다. YAML lint 통과와 실제 API 구현 완료를 구분한다.
- 요구사항에 없던 구현 필수값은 사용자의 기존 지시에 따라 v6의 **프로젝트 제안값**으로 정했다. 최신 명시 요구사항과 충돌하는 과거 제안은 사용하지 않는다.
- 프로젝트 제안값은 실제 공장 수치나 현대위아의 실제 운영 기준을 뜻하지 않는다.
- REST 경로, DTO, Enum, 오류코드, JWT, WebSocket, MQTT와 내부 수집 계약은 `docs/openapi.yaml`을 기준으로 구현한다.

계약 변경 규칙:

- API를 추가·삭제·변경할 때 `docs/API_SPEC.md`, `docs/openapi.yaml`, 구현 코드, 테스트를 같은 변경에서 동기화한다.
- 요구사항의 삭제 동작은 DELETE로 제공하되 DB에서는 `deletedAt`, `deletedBy`를 기록하는 논리 삭제를 사용한다.
- 화면 예시값을 실제 임계값, 공정값 또는 운영 기본값으로 승격하지 않는다.
- 프로젝트 제안값을 바꾸려면 변경 이유, 영향 API ID, 마이그레이션과 테스트 영향을 문서화한다.

## 패키지 구조

기능별 패키지 구조를 사용한다.

```text
com.sfaasproject.stf
├── common
├── auth
├── user
├── equipment
├── measurement
├── alarm
├── inventory
├── production
├── statistics
└── system
```

각 기능 내부는 필요에 따라 다음 구조를 사용한다.

```text
controller
service
repository
entity
dto
```

- Controller에서 Repository를 직접 호출하지 않는다.
- Controller는 요청 검증과 응답 변환만 담당한다.
- 업무 로직은 Service에서 처리한다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- Request DTO와 Response DTO를 구분한다.

## 개발 절차

코드를 수정하기 전에 다음 순서를 따른다.

1. `docs/API_SPEC.md`의 현재 판정과 관련 요구사항을 확인한다.
2. `docs/openapi.yaml`의 `x-specification-status`와 `paths`를 확인한다.
3. 상태가 `approved-project-proposal`이고 대상 operation의 API ID가 있으면 해당 계약대로 구현한다.
4. 상태가 `halted`이거나 `paths`가 비어 있으면 새 API 구현을 중단하고 필요한 정의를 보고한다.
5. 수정할 파일과 구현계획을 설명한다.
6. 승인된 범위만 구현한다.
7. 관련 테스트와 전체 테스트를 실행한다.
8. 변경 파일과 테스트 결과를 보고한다.

한 번에 전체 시스템을 구현하지 않는다.

API ID 또는 하나의 기능 단위로 나눠서 구현한다. 단, API ID와 계약이 승인 문서에 실제로 존재하는지 먼저 확인한다.

관련 없는 파일을 수정하거나 다른 개발자의 변경사항을 삭제하지 않는다.

## 테스트 명령

### Windows 전체 테스트

```powershell
.\gradlew.bat clean test
```

### Windows 특정 테스트

```powershell
.\gradlew.bat test --tests "com.sfaasproject.stf.auth.*"
```

### Linux 및 GitHub Actions

```bash
./gradlew clean test
```

### Docker Compose 문법 검사

`compose.yaml`을 수정한 경우 실행한다.

```powershell
docker compose config --quiet
```

테스트 규칙:

- 구현 완료 전에 관련 테스트를 실행한다.
- 관련 테스트 통과 후 전체 테스트를 실행한다.
- 실행하지 않은 테스트를 통과했다고 보고하지 않는다.
- 테스트 실패 시 실패한 테스트 이름과 오류 원인을 명시한다.
- `BUILD SUCCESSFUL`을 확인한 경우에만 전체 테스트가 통과했다고 보고한다.

## Docker 명령

### MySQL 실행

```powershell
docker compose up -d
```

### 상태 확인

```powershell
docker compose ps
```

### 로그 확인

```powershell
docker compose logs mysql --tail 50
```

### 종료

```powershell
docker compose stop
```

데이터 초기화를 사용자가 명시적으로 요청하지 않으면 다음 명령을 실행하지 않는다.

```powershell
docker compose down -v
```

## Git 규칙

- `main`에 직접 push하지 않는다.
- 하나의 Jira 기능마다 하나의 feature 브랜치를 사용한다.
- 커밋 또는 push는 사용자가 요청한 경우에만 수행한다.
- `.env`, 비밀번호, API 키 및 토큰을 커밋하지 않는다.
- 커밋 전 `git diff`와 테스트 결과를 확인한다.
- 다른 개발자의 변경사항을 임의로 삭제하지 않는다.
- 강제 push는 사용자가 명시적으로 승인한 경우에만 수행한다.

브랜치 예시:

```text
feature/SF5-21-auth
feature/SF5-22-measurement
fix/SF5-35-alarm
```

## 완료 조건

다음 조건을 모두 충족해야 구현 완료로 판단한다.

- 승인된 요구사항이 구현됨
- `docs/openapi.yaml`의 상태가 `approved-project-proposal`이고 실제 API 경로가 정의됨
- `docs/openapi.yaml`과 실제 API가 일치함
- 입력값 검증과 예외처리가 구현됨
- ADMIN/USER 권한 검사가 적용됨
- 관련 테스트가 작성됨
- 전체 Gradle 테스트가 통과함
- 비밀번호와 토큰이 코드나 로그에 포함되지 않음
- 관련 없는 파일이 변경되지 않음
- 변경 파일과 검증 결과가 보고됨

## 프론트엔드 범위

현재 브랜치는 Spring Boot 백엔드 전용이다.

프론트엔드 코드를 이 브랜치에 생성하거나 수정하지 않는다.

프론트엔드 프로젝트에는 별도의 `AGENTS.md`를 작성한다.

## 기준 문서

문서 우선순위:

1. 요구사항정의서 v1.0(2026-11-17), 기능목록 수정본·기능 변경 내역: 기능 범위와 업무 흐름
2. 화면설계서·공통동작 정의서·화면 HTML/PNG(2026-11-17): 입력·표시·고정 정렬 기준
3. `docs/API_SPEC.md`: v6 설명 명세, 업무규칙, 추적성 및 프로젝트 제안값
4. `docs/openapi.yaml`: API 주소, Method, DTO, Enum, 오류코드의 기계 판독 구현 계약
5. Confluence API 명세서 v6: 팀 공유용 문서이며 로컬 v6와 같은 내용을 유지

화면의 숫자, 설비 코드, 사용자, 임계값은 예시이며 계약값으로 사용하지 않는다.

문서끼리 충돌하면 최신 요구사항을 우선한다. 누락된 구현 필수값은 v6의 프로젝트 제안값을 적용한다. 실제 PLC 항목과 초기 카운터 기준 등 현장 검증값은 문서에 기록하고 데이터 부족은 null/status로 표시한다.

Confluence와 Git 이력의 v5 이하 문서는 비교·이행용이다. v4의 INT-002 생산 이벤트 합의는 2026-11-17 누적 카운터 요구사항으로 대체됐다.
