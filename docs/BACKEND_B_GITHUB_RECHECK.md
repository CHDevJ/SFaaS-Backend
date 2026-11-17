# GitHub 재확인과 A/B 통합 (2026-11-17)

## 확인한 원격 코드

| 브랜치 | 기준 | 반영 |
|---|---|---|
| Back_B | 7dd7ce4 | 테스트 전용 DB·Swagger·문서·233개 Excel 보고서 푸시 완료 |
| feature/SF5-54-auth-signup-login | 68a71fd | A의 66개 파일 변경을 로컬 기존 Back_B에 통합 |
| feature/SF5-20-virtual-equipment | 89c201b | Node-RED 전송 규격 검토. v5와 충돌하는 INT-002는 추가하지 않음 |
| frontend | fdfc040 | 실제 로그인 클라이언트·프록시·계약 동기화 스크립트 확인 |
| main | fbd2d45 | 폴더 골격과 문서만 있으며 백엔드 소스는 아직 없음 |

## 이번 연결 구현

- A의 SecurityConfig가 유일한 HTTP 체인이고 JWT 서비스·Clock도 하나다. 내부 수집은 정확한 POST 경로의 API 키 필터를 추가했다.
- B 40개 Controller 메서드는 Claims principal을 받는다. DB의 현재 직책을 사용하고 당번 조회 전체 허용·관리자 쓰기 제한을 유지한다.
- STOMP는 A의 AccessTokenAuthentication을 사용해 계정 ID 기반 개인 라우팅을 유지한다. CONNECT와 SUBSCRIBE에서 계정·폐기·만료를 검사한다.
- BTokenGuard는 A SessionStore에만 위임한다. B의 Redis 대체 키는 제거해 초/밀리초 이중 계약을 없앴다.
- app.jwt.*와 app.telegram.bot-username 설정을 공유한다. A ApiExceptionHandler는 원본 구조 그대로 채택했고 4종 입력 오류 처리도 포함한다.
- SignupDecidedEvent는 B의 Outbox로 연결했다. 승인·거절 커밋 이후만 전달하며, 거절된 신청의 이름·chat ID는 스냅샷에서 읽고 account_id는 NULL로 기록한다. 롤백 시 미발송, 재처리 시 중복 방지, Telegram 실패 시 승인 유지·실패 로그 저장을 검증한다.
- 로컬 manual-test 프로필에서만 무작위 비밀번호의 badmin01/bmaint01/bline01을 생성한다. DB 이름을 확인하고 기존 계정·비밀번호는 덮어쓰지 않는다. 비밀번호는 Git 제외 파일에만 보관한다.

## 검증

- 최종 Gradle clean test --no-daemon: 358개 통과 / 실패 0 / 오류 0 / 건너뜀 0 (64개 클래스).
- 실제 MySQL 8.4에서 V1~V4 전체 Flyway 적용·초기 데이터·B 읽기/쓰기 검증을 포함한다.
- 실제 실행 서버 HTTP 확인: 48개 통과 / 실패 0. 실제 로그인·발급 JWT의 B API 호출·잘못된 비밀번호 401을 포함한다.
- Swagger: B 40개 + A 로그인·가입·승인 5개 = 45개 operation.
- OpenAPI lint 통과. 기존 STOMP 전용 7개 unused 위치별 제외는 유지하며 계약 테스트로 검증한다.
- 전용 stf_b_manual에 V4와 연습 계정 적용 완료. 공유 개발 DB에는 적용하지 않았다.
- 설정·보고서 커밋 7dd7ce4 이후, 사용자의 추가 지시에 따라 이번 A/B 통합 코드도 Back_B에 커밋·푸시한다. backend/main으로 자동 병합하지 않는다.

## 팀에 전달할 남은 연결

1. Back_B 통합 코드는 backend나 main에 자동 병합하지 않는다. A의 원본 커밋 68a71fd를 기준으로 추적한다.
2. frontend의 api:sync는 origin/backend 고정이다. 통합 Back_B가 backend에 반영된 뒤 계약을 갱신해야 한다. 로컬 프록시 대상은 STF_BACKEND_URL=http://localhost:8081로 지정할 수 있다. 프론트 소스는 변경하지 않았다.
3. A의 AUTH-002/003/004 등 후속 Controller와 COM-002는 이번 A 브랜치에 없다. UI의 새로고침 복원·로그아웃·헤더 통신 상태는 후속 통합 대상이다. Secure Refresh 쿠키 정책은 유지한다.
4. 가상 설비는 누적 prod_count와 별도 production-events를 함께 보낸다. v5는 INT-002를 제거했으므로 measurements 한 경로로 누적 차이를 집계한다. Node-RED 담당자가 별도 이벤트 전송을 끄거나 계약에 맞춰 정리해야 한다.
5. 실제 Telegram 봇·PLC 데이터·공장 운영값은 별도 환경 확인 대상이다. 이번 검증은 외부 메시지를 보내지 않았다.
6. 기존 Excel 보고서는 통합 전 233개 테스트 스냅샷으로 보존한다. 최신 통합 결과는 이 문서와 Gradle HTML/XML을 기준으로 한다.
