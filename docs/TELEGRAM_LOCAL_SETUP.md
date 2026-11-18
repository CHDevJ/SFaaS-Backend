# 로컬 실행과 Telegram 공용 봇 연결

팀 공용 봇은 `sfaas5_alarm_bot` 하나를 사용한다. 서버 관리자가 봇 토큰을 한 번 설정하고, 사용자는 관제 사이트에서 자기 계정용 연결 링크를 발급받아 Telegram에서 시작을 누른다. 사용자마다 봇을 생성하거나 봇 토큰을 입력하지 않는다.

## 1. 관리자: 새 토큰을 로컬에 설정

채팅에 공유한 토큰은 BotFather의 `/revoke`에서 해당 봇을 선택해 폐기하고 새 토큰을 발급받는다. 새 토큰은 채팅·Git·Jira·Confluence에 붙여 넣지 않는다.

백엔드 프로젝트 `stf` 디렉터리의 PowerShell에서 실행한다.

```powershell
.\scripts\Setup-LocalTelegram.ps1
```

입력창에 새 토큰을 붙여 넣고 Enter를 누른다. 입력 내용은 화면에 표시되지 않는다. 스크립트는 Telegram의 `getMe`로 봇 이름을 확인하고 `getWebhookInfo`로 Webhook 사용 여부를 확인한다. 확인에 실패하면 기존 로컬 설정을 보존한다. Webhook이 있으면 수신 서버를 먼저 합의해야 하므로 자동으로 삭제하지 않는다.

검증 성공 시 Git에서 제외된 `.env.telegram.local.properties`에 봇 이름·토큰·Polling 설정을 저장한다. Spring은 프로젝트 루트에서 실행할 때 이 파일을 자동으로 읽는다. `app.telegram`과 `stf.telegram`이 같은 토큰을 사용하며, 기존 DB와 JWT 파일은 변경하지 않는다. OS·IntelliJ에 같은 환경변수가 있으면 그 값이 로컬 파일보다 우선한다.

`-PrepareOnly`는 토큰 없이 봇 이름만 준비하고 Polling을 끈다. 실제 봇 연결 검증을 의미하지 않는다.

동일한 공용 봇에서 Polling을 실행하는 백엔드는 하나만 둔다. 여러 개발자가 각각 켜면 업데이트 수신이 충돌할 수 있다. 토큰을 입력해도 서버 재시작 전에는 실행 중인 서버에 반영되지 않는다.

## 2. 백엔드 실행

Docker Desktop을 켜고 기존 테스트 DB가 있는 백엔드 프로젝트에서 실행한다. 새 DB 생성이나 데이터 초기화는 필요하지 않다.

```powershell
cd 'C:\Users\한국전파진흥협회\Desktop\스마트팩토리\SFaas\stf'
# 기존 테스트 서버가 켜져 있으면 먼저 종료한다. DB 데이터는 유지된다.
.\scripts\manual-test\Stop-ManualTest.ps1
.\scripts\manual-test\Start-ManualTest.ps1
```

| 확인 항목 | 주소 |
| --- | --- |
| 백엔드 상태 | http://127.0.0.1:8081/actuator/health |
| Swagger | http://127.0.0.1:8081/swagger-ui/index.html |
| 로컬 테스트 MySQL | 127.0.0.1:3308 |
| 로컬 테스트 Redis | 127.0.0.1:6380 |

IntelliJ로 실행한다면 기존 DB·JWT 설정을 유지하고 활성 프로필은 `backend-b,manual-test`, 작업 디렉터리는 `stf` 프로젝트 루트로 둔다. 위 스크립트의 서버와 IntelliJ 서버를 같은 포트에 동시에 띄우지 않는다. 기존 `local,backend-b` 프로필의 8080 서버를 쓴다면 그 서버를 재시작하고 프론트 대상 주소도 8080으로 맞춘다.

## 3. 프론트엔드 실행

현재 로컬 프론트의 실행 폴더는 `stf-frontend\frontend`이다. 프론트 코드는 이번 백엔드 설정 작업에서 수정하지 않는다.

처음 한 번 의존성을 설치한다. Node 버전은 프론트 `package.json`의 `engines`를 따른다.

```powershell
cd 'C:\Users\한국전파진흥협회\Desktop\스마트팩토리\SFaas\stf-frontend\frontend'
npm ci
```

이 디렉터리의 Git 제외 파일 `.env.local`에서 아래 한 항목을 설정한다. 기존 다른 설정이 있으면 유지한다.

```dotenv
STF_BACKEND_URL=http://127.0.0.1:8081
```

프론트 터미널에서 실행한다.

```powershell
npm run dev:server
```

화면은 http://localhost:5173/login 에서 연다. `npm run dev`는 가짜 데이터 모드이므로 실제 Telegram 연결을 확인할 때는 `dev:server`를 사용한다. 프론트 개발 서버가 `/api`, `/ws`를 백엔드로 전달한다. `.env.local` 수정 후에는 프론트 개발 서버도 재시작한다.

같은 Wi-Fi의 다른 PC에서 프론트를 실행할 때 `STF_BACKEND_URL`은 백엔드 PC의 실제 IP와 포트여야 한다. 백엔드가 127.0.0.1에 바인딩된 `manual-test` 설정 그대로라면 다른 PC에서는 접속할 수 없다. LAN 연결 시 별도 실행 인수로 `server.address=0.0.0.0`과 실제 프론트 주소를 포함한 `STF_WS_ORIGINS`를 설정하고 Windows 방화벽의 사설 네트워크 허용을 확인한다. 그림의 IP를 현재 PC의 실제 IP로 단정하지 않는다.

봇 토큰은 백엔드에만 저장하며 `VITE_*` 같은 프론트 환경변수에 넣지 않는다.

## 4. 사용자별 연결 확인

1. 실제 서버 모드의 관제 사이트에 로그인한다.
2. 내 Telegram 연결 메뉴에서 연결 URL을 새로 발급받는다.
3. 발급된 URL을 열어 `sfaas5_alarm_bot`에서 시작을 누른다.
4. 관제 사이트에서 연결 상태를 다시 조회해 연결됨을 확인한다.
5. 내 테스트 메시지 발송을 실행하고 Telegram에서 수신을 확인한다.

단순히 봇을 검색해 `/start`만 보내면 어떤 관제 계정인지 알 수 없으므로 연결되지 않는다. 프로젝트에서 발급한 10분짜리 일회용 링크를 사용한다. 봇 토큰과 이 계정 연결용 일회용 토큰은 서로 다르다.

Swagger에서는 로그인한 Access Token을 Authorize에 넣고 아래 순서로 확인할 수 있다.

| 순서 | API | 확인 |
| --- | --- | --- |
| 1 | POST /api/v1/telegram/link-tokens | 내 계정용 연결 URL 발급 |
| 2 | Telegram에서 발급 URL 열기 | 시작 누르기 |
| 3 | GET /api/v1/telegram/me/connection | 연결 상태 확인 |
| 4 | POST /api/v1/telegram/me/test-messages | 실제 수신과 발송 결과 확인 |

서버가 켜져 있어야 Polling이 연결 요청을 받아 처리한다. 봇을 열었다는 사실만으로 연결 완료를 판정하지 않는다.

참고: [Telegram Deep Linking](https://core.telegram.org/bots/features#deep-linking), [Telegram Bot API](https://core.telegram.org/bots/api).
