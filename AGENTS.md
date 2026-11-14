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

1. 관련 요구사항과 `docs/openapi.yaml` 확인
2. 기존 코드와 테스트 분석
3. 수정할 파일과 구현계획 설명
4. 승인된 범위만 구현
5. 관련 테스트와 전체 테스트 실행
6. 변경 파일과 테스트 결과 보고

한 번에 전체 시스템을 구현하지 않는다.

API ID 또는 하나의 기능 단위로 나눠서 구현한다.

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
- `docs/openapi.yaml`과 실제 API가 일치함
- 입력값 검증과 예외처리가 구현됨
- 역할별 권한 검사가 적용됨
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

- API 주소, Method, DTO, Enum, 오류코드: `docs/openapi.yaml`
- 업무 흐름과 기능 동작: 요구사항 정의서
- 화면 구성과 사용자 조작: STF 화면 프로토타입
- 설명용 문서: Confluence API 명세서

문서끼리 충돌하면 임의로 결정하지 않고 사용자에게 확인한다.
