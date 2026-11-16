-- STF 설비통합관제 시스템 초기 데이터 (Flyway V2) - Jira SF5-18
-- 라인 5 / 공정 27 / 설비 27 / 설비 현재 상태 27 / 데이터 항목 69 / 설정 6 / 계정 5 (합계 166줄)
-- 자동 증가 번호는 직접 쓰지 않고 이름과 코드로 찾아서 넣습니다
-- 설비 코드와 항목 코드는 센서 기준표 기준입니다. 가상 설비 메시지 규격 (SF5-19) 이 확정되면 대조합니다
-- 이 파일은 실행된 뒤에 고치지 않습니다. 바꿀 것은 V3부터 새 파일로 추가합니다

-- ───────── 1. 라인 (5) ─────────
-- consume_per_min 6.0 과 max_stock 500 은 가정 값 (사용 시나리오의 시간당 360개 기준)
INSERT INTO `line` (`name`, `line_type`, `sort_order`, `consume_per_min`, `max_stock`) VALUES
  ('외륜',   'PART',     1, 6.0, 500),
  ('내륜',   'PART',     2, 6.0, 500),
  ('케이지', 'PART',     3, 6.0, 500),
  ('샤프트', 'PART',     4, 6.0, 500),
  ('조립',   'ASSEMBLY', 5, NULL, NULL);

-- ───────── 2. 공정 (27) ─────────
-- 라인 이름으로 line_id 를 찾습니다
INSERT INTO `process` (`line_id`, `process_seq`, `name`)
SELECT l.`line_id`, v.`seq`, v.`name`
FROM `line` l
JOIN (
            SELECT '외륜' AS line_name, 1 AS seq, '단조' AS name
  UNION ALL SELECT '외륜', 2, '선삭'
  UNION ALL SELECT '외륜', 3, '볼 홈 가공'
  UNION ALL SELECT '외륜', 4, '고주파 열처리'
  UNION ALL SELECT '외륜', 5, '연삭'
  UNION ALL SELECT '외륜', 6, '측정'
  UNION ALL SELECT '내륜', 1, '단조'
  UNION ALL SELECT '내륜', 2, '선삭'
  UNION ALL SELECT '내륜', 3, '볼 홈 가공'
  UNION ALL SELECT '내륜', 4, '열처리'
  UNION ALL SELECT '내륜', 5, '연삭'
  UNION ALL SELECT '내륜', 6, '측정'
  UNION ALL SELECT '케이지', 1, '절단'
  UNION ALL SELECT '케이지', 2, '선삭'
  UNION ALL SELECT '케이지', 3, '창 타발'
  UNION ALL SELECT '케이지', 4, '열처리'
  UNION ALL SELECT '케이지', 5, '연삭'
  UNION ALL SELECT '케이지', 6, '측정'
  UNION ALL SELECT '샤프트', 1, '절단'
  UNION ALL SELECT '샤프트', 2, '전조'
  UNION ALL SELECT '샤프트', 3, '고주파 열처리'
  UNION ALL SELECT '샤프트', 4, '교정'
  UNION ALL SELECT '샤프트', 5, '측정'
  UNION ALL SELECT '조립', 1, '조인트 조립'
  UNION ALL SELECT '조립', 2, '부트 조립'
  UNION ALL SELECT '조립', 3, '밴드 체결'
  UNION ALL SELECT '조립', 4, '최종 검사'
) v ON v.`line_name` = l.`name`;

-- ───────── 3. 설비 (27) ─────────
-- 라인 이름과 공정 순서로 process_id 를 찾습니다
-- 외륜 6대는 화면설계서 값. 나머지 21대 이름과 종류는 제안 값. IR-04-1 은 실제 PLC (MODBUS_TCP). 나머지 MQTT 는 가정
INSERT INTO `equipment` (`equip_code`, `name`, `equip_type`, `process_id`, `comm_type`)
SELECT v.`code`, v.`name`, v.`type`, p.`process_id`, v.`comm`
FROM (
            SELECT 'OR-01-1' AS code, '외륜' AS line_name, 1 AS seq, '외륜 단조기 1호기' AS name, '단조기' AS type, 'MODBUS_TCP' AS comm
  UNION ALL SELECT 'OR-02-1', '외륜', 2, '외륜 선삭기 1호기',        '선삭기',   'OPC_UA'
  UNION ALL SELECT 'OR-03-1', '외륜', 3, '외륜 볼 홈 가공기 1호기',  '가공기',   'OPC_UA'
  UNION ALL SELECT 'OR-04-1', '외륜', 4, '외륜 열처리로 1호기',      '열처리로', 'MODBUS_TCP'
  UNION ALL SELECT 'OR-05-1', '외륜', 5, '외륜 연삭기 1호기',        '연삭기',   'OPC_UA'
  UNION ALL SELECT 'OR-06-1', '외륜', 6, '외륜 측정기 1호기',        '측정기',   'MQTT'
  UNION ALL SELECT 'IR-01-1', '내륜', 1, '내륜 단조기 1호기',        '단조기',   'MQTT'
  UNION ALL SELECT 'IR-02-1', '내륜', 2, '내륜 선삭기 1호기',        '선삭기',   'MQTT'
  UNION ALL SELECT 'IR-03-1', '내륜', 3, '내륜 볼 홈 가공기 1호기',  '가공기',   'MQTT'
  UNION ALL SELECT 'IR-04-1', '내륜', 4, '내륜 열처리로 1호기',      '열처리로', 'MODBUS_TCP'
  UNION ALL SELECT 'IR-05-1', '내륜', 5, '내륜 연삭기 1호기',        '연삭기',   'MQTT'
  UNION ALL SELECT 'IR-06-1', '내륜', 6, '내륜 측정기 1호기',        '측정기',   'MQTT'
  UNION ALL SELECT 'CG-01-1', '케이지', 1, '케이지 절단기 1호기',    '절단기',   'MQTT'
  UNION ALL SELECT 'CG-02-1', '케이지', 2, '케이지 선삭기 1호기',    '선삭기',   'MQTT'
  UNION ALL SELECT 'CG-03-1', '케이지', 3, '케이지 창 타발기 1호기', '타발기',   'MQTT'
  UNION ALL SELECT 'CG-04-1', '케이지', 4, '케이지 열처리로 1호기',  '열처리로', 'MQTT'
  UNION ALL SELECT 'CG-05-1', '케이지', 5, '케이지 연삭기 1호기',    '연삭기',   'MQTT'
  UNION ALL SELECT 'CG-06-1', '케이지', 6, '케이지 측정기 1호기',    '측정기',   'MQTT'
  UNION ALL SELECT 'SH-01-1', '샤프트', 1, '샤프트 절단기 1호기',    '절단기',   'MQTT'
  UNION ALL SELECT 'SH-02-1', '샤프트', 2, '샤프트 전조기 1호기',    '전조기',   'MQTT'
  UNION ALL SELECT 'SH-03-1', '샤프트', 3, '샤프트 열처리로 1호기',  '열처리로', 'MQTT'
  UNION ALL SELECT 'SH-04-1', '샤프트', 4, '샤프트 교정기 1호기',    '교정기',   'MQTT'
  UNION ALL SELECT 'SH-05-1', '샤프트', 5, '샤프트 측정기 1호기',    '측정기',   'MQTT'
  UNION ALL SELECT 'AS-01-1', '조립', 1, '조립 조인트 조립기 1호기', '조립기',   'MQTT'
  UNION ALL SELECT 'AS-02-1', '조립', 2, '조립 부트 조립기 1호기',   '조립기',   'MQTT'
  UNION ALL SELECT 'AS-03-1', '조립', 3, '조립 밴드 체결기 1호기',   '체결기',   'MQTT'
  UNION ALL SELECT 'AS-04-1', '조립', 4, '조립 최종 검사기 1호기',   '검사기',   'MQTT'
) v
JOIN `line` l ON l.`name` = v.`line_name`
JOIN `process` p ON p.`line_id` = l.`line_id` AND p.`process_seq` = v.`seq`;

-- ───────── 4. 설비 현재 상태 (27) ─────────
-- 처음 상태는 통신 끊김. 값을 받기 전이라 마지막 통신 시각은 비움
INSERT INTO `equipment_state` (`equipment_id`, `status`, `status_since`, `last_received_at`)
SELECT e.`equipment_id`, 'COMMUNICATION_LOST', CURRENT_TIMESTAMP, NULL
FROM `equipment` e;

-- ───────── 5. 데이터 항목: 측정값 (15) ─────────
-- 설비 코드로 equipment_id 를 찾습니다. 수집 주기 3초. 측정값을 표시 순서 앞에 둡니다
-- decimal_places 는 가정 값 (forming_load, tool_use_count 0 / 나머지 1)
INSERT INTO `data_item` (`equipment_id`, `item_code`, `name`, `item_kind`, `item_role`, `unit`,
                         `collect_cycle_sec`, `decimal_places`, `caution_limit`, `critical_limit`, `limit_direction`, `sort_order`)
SELECT e.`equipment_id`, v.`code`, v.`name`, 'MEASUREMENT', 'NONE', v.`unit`,
       3, v.`dp`, v.`caution`, v.`critical`, v.`dir`, v.`ord`
FROM (
            SELECT 'OR-01-1' AS equip, 'forming_load' AS code, '성형 하중' AS name, 'tonf' AS unit, 0 AS dp, 337 AS caution, 367 AS critical, 'ABOVE' AS dir, 1 AS ord
  UNION ALL SELECT 'OR-01-1', 'die_temp',            '금형 온도',      '℃',     1, 150,  180,  'ABOVE', 2
  UNION ALL SELECT 'OR-02-1', 'spindle_load',        '주축 부하',      '%',     1, 80,   100,  'ABOVE', 1
  UNION ALL SELECT 'OR-02-1', 'cutting_oil_temp',    '절삭유 온도',    '℃',     1, 30,   35,   'ABOVE', 2
  UNION ALL SELECT 'OR-03-1', 'spindle_vibration',   '주축 진동',      'mm/s',  1, 2.8,  4.5,  'ABOVE', 1
  UNION ALL SELECT 'OR-03-1', 'tool_use_count',      '공구 사용 횟수', '회',    0, 450,  500,  'ABOVE', 2
  UNION ALL SELECT 'OR-04-1', 'coolant_flow',        '냉각액 유량',    'L/min', 1, 72,   64,   'BELOW', 1
  UNION ALL SELECT 'OR-04-1', 'coolant_temp',        '냉각액 온도',    '℃',     1, 35,   45,   'ABOVE', 2
  UNION ALL SELECT 'OR-04-1', 'heating_power',       '가열 전력',      'kW',    1, 33,   30,   'BELOW', 3
  UNION ALL SELECT 'IR-04-1', 'coolant_flow',        '냉각액 유량',    'L/min', 1, 72,   64,   'BELOW', 1
  UNION ALL SELECT 'IR-04-1', 'coolant_temp',        '냉각액 온도',    '℃',     1, 35,   45,   'ABOVE', 2
  UNION ALL SELECT 'IR-04-1', 'heating_power',       '가열 전력',      'kW',    1, 33,   30,   'BELOW', 3
  UNION ALL SELECT 'OR-05-1', 'wheel_load',          '숫돌 부하',      '%',     1, 80,   100,  'ABOVE', 1
  UNION ALL SELECT 'OR-05-1', 'vibration',           '진동',           'mm/s',  1, 2.8,  4.5,  'ABOVE', 2
  UNION ALL SELECT 'OR-06-1', 'dimension_deviation', '치수 편차',      'µm',    1, 15,   20,   'ABOVE', 1
) v
JOIN `equipment` e ON e.`equip_code` = v.`equip`;

-- ───────── 6. 데이터 항목: 가동 신호 (27) ─────────
-- 모든 설비. 0 정지 / 1 가동. 알람은 만들지 않음 (alarm_state_value 비움)
-- 표시 순서는 그 설비의 측정값 다음
INSERT INTO `data_item` (`equipment_id`, `item_code`, `name`, `item_kind`, `item_role`, `unit`,
                         `collect_cycle_sec`, `decimal_places`, `state_label_0`, `state_label_1`, `sort_order`)
SELECT e.`equipment_id`, 'run_signal', '가동 신호', 'STATE', 'RUN_SIGNAL', NULL,
       3, 0, '정지', '가동', COALESCE(m.`cnt`, 0) + 1
FROM `equipment` e
LEFT JOIN (
  SELECT `equipment_id`, COUNT(*) AS cnt FROM `data_item` WHERE `item_kind` = 'MEASUREMENT' GROUP BY `equipment_id`
) m ON m.`equipment_id` = e.`equipment_id`;

-- ───────── 7. 데이터 항목: 생산 수 (27) ─────────
-- 모든 설비. 매일 00:00 에 0으로 되돌림. 표시 순서는 가동 신호 다음
INSERT INTO `data_item` (`equipment_id`, `item_code`, `name`, `item_kind`, `item_role`, `unit`,
                         `collect_cycle_sec`, `decimal_places`, `reset_time`, `sort_order`)
SELECT e.`equipment_id`, 'prod_count', '생산 수', 'CUMULATIVE', 'PRODUCTION', '개',
       3, 0, '00:00:00', COALESCE(m.`cnt`, 0) + 2
FROM `equipment` e
LEFT JOIN (
  SELECT `equipment_id`, COUNT(*) AS cnt FROM `data_item` WHERE `item_kind` = 'MEASUREMENT' GROUP BY `equipment_id`
) m ON m.`equipment_id` = e.`equipment_id`;

-- ───────── 8. 설정값 (6) ─────────
-- UNIT_PRICE 는 값 미정이라 0
INSERT INTO `system_setting` (`setting_key`, `name`, `setting_group`, `setting_value`, `unit`, `min_value`, `max_value`) VALUES
  ('COMM_TIMEOUT_SEC',   '통신 끊김 판단 시간',      'JUDGE', '30', '초', 10, 300),
  ('RENOTIFY_MIN',       '미확인 재알림 시간',       'JUDGE', '3',  '분', 1,  60),
  ('VERIFY_PASS_COUNT',  '효과 확인 검사기 통과 수', 'JUDGE', '20', '개', 5,  100),
  ('ALARM_STREAK_COUNT', '알람 판정 연속 횟수',      'JUDGE', '3',  '회', 1,  10),
  ('STOCK_LEVEL_MIN',    '남은 시간 구분 기준',      'JUDGE', '30', '분', 5,  120),
  ('UNIT_PRICE',         '개당 금액',                'CALC',  '0',  '원', 0,  NULL);

-- ───────── 9. 계정 (5) ─────────
-- 가상 이름. 모두 승인. 휴대전화는 비움
-- 비밀번호는 공통 임시 비밀번호의 BCrypt 값. 첫 로그인 때 변경하도록 must_change_password = TRUE
INSERT INTO `account` (`login_id`, `password_hash`, `name`, `phone`, `role`, `approval_status`,
                       `approved_at`, `approved_by`, `must_change_password`) VALUES
  ('admin01', '$2a$10$Sb.dmMfR78ZzyWXXpJ5RfumM3cVgYm8Z58K/otmyAtf3RbOzs0Xk.', '정관리', NULL, 'SYSTEM_ADMIN', 'APPROVED',
   CURRENT_TIMESTAMP, NULL, TRUE);

-- 나머지 4개는 관리자 아이디로 승인한 사람 번호를 찾습니다
INSERT INTO `account` (`login_id`, `password_hash`, `name`, `phone`, `role`, `approval_status`,
                       `approved_at`, `approved_by`, `must_change_password`)
SELECT v.`login_id`, '$2a$10$Sb.dmMfR78ZzyWXXpJ5RfumM3cVgYm8Z58K/otmyAtf3RbOzs0Xk.', v.`name`, NULL, v.`role`, 'APPROVED',
       CURRENT_TIMESTAMP, a.`account_id`, TRUE
FROM (
            SELECT 'linemgr01' AS login_id, '강라인' AS name, 'LINE_MANAGER' AS role
  UNION ALL SELECT 'linemgr02', '오라인', 'LINE_MANAGER'
  UNION ALL SELECT 'maint01',   '서보전', 'MAINTENANCE'
  UNION ALL SELECT 'maint02',   '문보전', 'MAINTENANCE'
) v
JOIN `account` a ON a.`login_id` = 'admin01';
