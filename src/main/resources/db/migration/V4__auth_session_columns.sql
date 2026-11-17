-- SF5-54 인증 (AUTH-001, AUTH-002): 최종 로그인 시각과 Refresh Token 회전 추적용 컬럼 추가
-- V1 은 SF5-17, V2 는 SF5-18, V3 는 Back_B(alarm 컬럼과 backend_* 테이블)가 쓰므로 V4 로 추가합니다.
-- 번호 순서 때문에 Back_B 의 V3 가 먼저 머지되거나 같이 머지되어야 합니다(뒤늦게 들어오면 Flyway 가 거부함).
-- 이미 머지된 마이그레이션은 고치지 않는다는 규칙에 따라 V1 을 건드리지 않았습니다.

ALTER TABLE `account`
  ADD COLUMN `last_login_at` datetime NULL
    COMMENT '최종 로그인 시각. 로그인에 성공할 때만 갱신하고 실패해도 그대로 둠 (API 명세 P-05)'
    AFTER `must_change_password`;

ALTER TABLE `auth_token`
  ADD COLUMN `family_id` varchar(36) NOT NULL
    COMMENT 'Refresh Token 계열 번호. 로그인 한 번이 한 계열이고 회전해도 같은 값. 재사용이 감지되면 계열 전체를 폐기'
    AFTER `account_id`,
  ADD COLUMN `used_at` datetime NULL
    COMMENT '회전 시각. 새 토큰으로 교체되면 채움. 이미 채워진 토큰이 다시 오면 재사용으로 판단'
    AFTER `expires_at`;

CREATE INDEX `idx_auth_token_family` ON `auth_token` (`family_id`);
