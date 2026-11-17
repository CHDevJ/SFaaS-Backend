-- V5: 조립 정지 예측에 쓰는 설정값 3개와 부품 라인 기초 재고
-- 근거: 기능 목록 수정본 (2026. 11. 17) 의 9.3 재고 소진 시간 예측 / 9.7 예상 손실액 계산 / 14.2 계산 기준값 관리
-- V1부터 V4는 고치지 않습니다. 테이블과 칼럼은 그대로이고 데이터만 추가합니다.

-- ───────── 1. 설정값 (3) ─────────
-- STOCK_DANGER_MIN : 남은 시간이 이 값 이하이면 위험 단계. 서버 코드가 이미 이 이름으로 읽음 (JdbcBForecasts)
-- RATE_WINDOW_MIN  : 분당 입고와 분당 소비를 구하는 구간. 지금 서버 코드에는 10분이 고정으로 적혀 있음
-- HOURLY_OUTPUT    : 예상 손실액에 쓰는 조립 라인 시간당 생산량. 상위 시스템 (MES) 에서 받아 온다고 가정한 기준값
INSERT INTO `system_setting` (`setting_key`, `name`, `setting_group`, `setting_value`, `unit`, `min_value`, `max_value`) VALUES
  ('STOCK_DANGER_MIN', '남은 시간 위험 기준', 'JUDGE', '10',  '분', 1, 60),
  ('RATE_WINDOW_MIN',  '속도 계산 구간',      'CALC',  '10',  '분', 1, 60),
  ('HOURLY_OUTPUT',    '시간당 생산량',       'CALC',  '360', '개', 1, 100000);

-- ───────── 2. 기초 재고 (부품 라인마다 300개) ─────────
-- 재고는 재고 시스템에서 받아 stock_history 에 넣는다고 가정합니다.
-- 시연에서는 서버가 생산 수로 계산해 같은 테이블에 이어서 넣습니다. 이 줄이 그 계산의 시작 값입니다.
-- 300개는 분당 6개를 쓸 때 50분을 쓰는 양입니다 (가정 값).
-- 이미 재고 기록이 있는 라인은 건드리지 않습니다.
INSERT INTO `stock_history` (`line_id`, `recorded_at`, `qty`)
SELECT l.`line_id`, CURRENT_TIMESTAMP, 300
FROM `line` l
WHERE l.`line_type` = 'PART'
  AND l.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `stock_history` s WHERE s.`line_id` = l.`line_id`);
