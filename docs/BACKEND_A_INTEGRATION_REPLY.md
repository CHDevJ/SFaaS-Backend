# A 개발자에게 전달할 답변

다섯 가지 방향에 동의합니다. 아래 조건만 함께 맞추면 좋겠습니다.

1. **마이그레이션**: B의 V3 → A의 V4에 동의합니다. 머지 순서뿐 아니라 실제 DB에도 V3가 먼저 적용되어야 합니다. 번호 충돌을 피하려고 outOfOrder를 켜지는 않겠습니다. 현재 V3는 제 전용 테스트 DB에서만 적용했습니다.
2. **보안 설정**: 통합 후 SecurityFilterChain과 JwtAccessTokenService 빈은 A 설정으로 통일하겠습니다. `/system/**` 일괄 관리자 제한에는 예외가 필요합니다. v5에서 당번 목록·현재 당번 조회(DUT-001, DUT-003)는 전체 역할에 허용되어 있습니다. 해당 GET 경로를 먼저 허용하고 당번 쓰기와 관리자 전용 기능은 SYSTEM_ADMIN으로 제한해 주세요. `/api/v1/auth/**` 공개도 공개 API만 정확히 지정하는 방식에 동의합니다.
   - BAccess는 Claims principal을 명시적으로 읽도록 수정하고 HTTP Controller는 A의 principal 계약에 맞추겠습니다. 현재 BAccess는 Authentication.getName()을 숫자 계정 ID로 가정합니다.
   - STOMP의 Principal.getName()은 개인 알림 라우팅에 쓰이므로 계정 ID 문자열을 유지해 주세요. HTTP 체인 하나로 통일하는 것과 STOMP CONNECT·SUBSCRIBE·SEND 검사는 별도입니다. WebSocket 설정도 중복 등록하지 않게 조정하겠습니다.
   - BTokenGuard 빈은 현재 BHttpSecurityConfiguration 안에 있으므로, 이 설정 삭제 시 독립 설정으로 옮기겠습니다. BWebSocketConfiguration이 주입받는 JWT 서비스 빈도 하나는 제공되어야 합니다.
3. **ApiExceptionHandler**: A 구조에 B의 입력 오류 4종을 포함해 반영하는 방식에 동의합니다. 이후 해당 파일 일괄 재포맷은 하지 않고, 필요한 오류는 DomainException으로 처리하거나 A에게 전달하겠습니다. 새 로컬 테스트 설정에서는 해당 파일을 변경하지 않았습니다.
4. **머지 순서**: PM V1/V2 → B V3 → A 인증 PR에 동의합니다. A PR 반영 후 B 보안·principal·STOMP 관련 코드와 테스트를 조정하고 전체 Gradle 테스트를 재실행하겠습니다. 현재 B 단독 테스트 서버가 돌아가므로 B 체인과 bJwt를 미리 삭제하지는 않겠습니다.
5. **Redis 폐기 키**: 통합 환경에서는 A의 SessionStore를 사용합니다. 현재 B 단독 테스트 환경은 SessionStore 빈이 없어 대체 경로를 실제 사용하고 있습니다. 전환 시 키 이름·초/밀리초 단위·값이 뜻하는 만료시각/발급기준시각을 함께 맞춰야 합니다. A PR 반영 시 대체 경로를 테스트 프로필에 한정하거나 제거하는 방향으로 정리하겠습니다. SessionStore가 있는 통합 환경에서는 B 대체 키를 병행 사용하지 않습니다.

JWT_SECRET뿐 아니라 JWT_ISSUER·서명 알고리즘·클레임과 app.* / stf.* 속성 매핑도 같이 맞추겠습니다. 보안 공통 파일의 최종 소유자는 A로 두고 B 기능별 업무 권한 검증은 유지하겠습니다.
