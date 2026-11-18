-- H2 test adaptation of V1, V3 and V4.
-- STF 설비통합관제 시스템 첫 스키마 (Flyway V1)
-- 만든 방법: erd.dbml (dbdiagram.io 형식) 을 DBML 도구 (dbml2sql) 로 변환
-- 대상: MySQL 8.0 / InnoDB / utf8mb4
-- 표 23개 / 외래 키 31개
-- 표를 바꿀 때는 이 파일을 고치지 말고 V2부터 새 파일을 추가합니다

CREATE TABLE `account` (
  `account_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '계정 번호',
  `login_id` varchar(50) UNIQUE NOT NULL COMMENT '아이디. 중복 불가. 삭제한 계정의 아이디도 다시 쓸 수 없음',
  `password_hash` varchar(100) COMMENT '비밀번호. 암호화한 값만 저장 (BCrypt). 삭제한 계정은 비움',
  `name` varchar(50) NOT NULL COMMENT '이름. 삭제해도 남김. 지난 처리 기록에 이름을 보여 주기 위해서',
  `telegram_chat_id` varchar(30) UNIQUE COMMENT '텔레그램 대화 번호. 텔레그램 봇과 연결하면 채움. 비어 있으면 텔레그램 알림을 받지 않음. 삭제한 계정은 비움',
  `telegram_linked_at` datetime COMMENT '텔레그램 연결 시각. 봇과 연결한 시각. 연결을 끊으면 비움',
  `role` varchar(20) COMMENT '직책. 가입을 승인할 때 정함. 승인 전에는 비어 있음. 허용 값: SYSTEM_ADMIN 시스템관리자 / LINE_MANAGER 라인 관리자 / MAINTENANCE 보전 담당자',
  `approval_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '승인 상태. 승인된 계정만 로그인 가능. 허용 값: PENDING 승인 대기 / APPROVED 승인',
  `approved_at` datetime COMMENT '승인 시각',
  `approved_by` bigint COMMENT '승인한 사람',
  `must_change_password` boolean NOT NULL DEFAULT false COMMENT '비밀번호 변경 필요. 임시 비밀번호를 발급하면 TRUE. 다음 로그인 때 새 비밀번호를 정하면 FALSE',
  `deleted_at` datetime COMMENT '삭제 시각. 비어 있으면 사용 중인 계정. 채워져 있으면 로그인 거부',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각. 가입을 신청한 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `auth_token` (
  `token_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '토큰 번호',
  `account_id` bigint NOT NULL COMMENT '계정 번호',
  `token_hash` varchar(64) UNIQUE NOT NULL COMMENT '토큰 확인 값. 토큰 원문 대신 SHA-256으로 바꾼 값 (64글자) 을 저장',
  `issued_at` datetime NOT NULL COMMENT '발급 시각',
  `expires_at` datetime NOT NULL COMMENT '만료 시각',
  `revoked_at` datetime COMMENT '폐기 시각. 로그아웃하면 채움'
);

CREATE TABLE `duty` (
  `duty_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '당번 번호',
  `start_date` date UNIQUE NOT NULL COMMENT '시작일. 앞 당번의 종료일 다음 날',
  `end_date` date NOT NULL COMMENT '종료일',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `duty_member` (
  `duty_id` bigint NOT NULL COMMENT '당번 번호',
  `account_id` bigint NOT NULL COMMENT '계정 번호',
  PRIMARY KEY (`duty_id`, `account_id`)
);

CREATE TABLE `line` (
  `line_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '라인 번호',
  `name` varchar(50) UNIQUE NOT NULL COMMENT '라인 이름',
  `line_type` varchar(20) NOT NULL COMMENT '라인 종류. 허용 값: PART 부품 라인 / ASSEMBLY 조립 라인',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `consume_per_min` double COMMENT '분당 소비 개수. 조립이 이 라인의 부품을 1분에 쓰는 개수',
  `max_stock` int COMMENT '최대 재고',
  `deleted_at` datetime COMMENT '삭제 시각. 비어 있으면 사용 중인 라인',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `process` (
  `process_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '공정 번호',
  `line_id` bigint NOT NULL COMMENT '라인 번호',
  `process_seq` int NOT NULL COMMENT '공정 순서. 라인 안에서 몇 번째 공정인지',
  `name` varchar(100) NOT NULL COMMENT '공정 이름. 예: 열처리',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `equipment` (
  `equipment_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '설비 번호',
  `equip_code` varchar(20) UNIQUE NOT NULL COMMENT '설비 코드. 중복 불가. 등록한 뒤 수정 불가 (예: OR-05-1). 삭제한 설비의 코드도 다시 쓸 수 없음',
  `name` varchar(100) NOT NULL COMMENT '이름. 예: 외륜 연삭기 1호기',
  `equip_type` varchar(50) NOT NULL COMMENT '종류. 예: 연삭기',
  `serial_no` varchar(50) COMMENT '시리얼 번호',
  `process_id` bigint NOT NULL COMMENT '공정 번호. 라인은 공정을 거쳐서 찾음',
  `comm_type` varchar(20) NOT NULL COMMENT '통신 방식. 허용 값: MODBUS_TCP Modbus TCP / OPC_UA OPC UA / MQTT MQTT',
  `photo_path` varchar(255) COMMENT '설비 사진. 사진 파일을 저장한 경로. JPG 또는 PNG이고 5MB 이하',
  `registered_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 일시. 화면에서 날짜와 시각으로 고르는 값. 처음 값은 현재 일시',
  `deleted_at` datetime COMMENT '삭제 시각. 비어 있으면 사용 중인 설비. 채워져 있으면 감시와 알람과 배치도에서 제외',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '만든 시각. 줄을 만든 시각. 서버가 채움',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `data_item` (
  `data_item_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '항목 번호',
  `equipment_id` bigint NOT NULL COMMENT '설비 번호',
  `item_code` varchar(50) NOT NULL COMMENT '항목 코드. 설비가 보낸 메시지의 항목과 맞추는 값 (예: coolant_flow)',
  `name` varchar(50) NOT NULL COMMENT '항목 이름. 예: 냉각수 유량',
  `item_kind` varchar(20) NOT NULL DEFAULT 'MEASUREMENT' COMMENT '값 종류. 허용 값: MEASUREMENT 측정값 / STATE 상태값 / CUMULATIVE 누적값',
  `item_role` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '용도. 서버가 설비 상태와 생산 수를 계산할 때 쓰는 항목을 표시. 허용 값: NONE 일반 / RUN_SIGNAL 가동 신호 / PRODUCTION 생산 수',
  `unit` varchar(20) COMMENT '단위. 예: L/min. 상태값은 비움',
  `collect_cycle_sec` int NOT NULL DEFAULT 3 COMMENT '수집 주기. 초 단위',
  `decimal_places` int NOT NULL DEFAULT 1 COMMENT '소수 자릿수. 화면에 표시할 자릿수',
  `caution_limit` double COMMENT '주의 기준. 측정값에만 넣음',
  `critical_limit` double COMMENT '경보 기준. 측정값에만 넣음',
  `limit_direction` varchar(10) COMMENT '기준 방향. 측정값에만 넣음. 허용 값: ABOVE 값이 기준 이상이면 벗어남 / BELOW 값이 기준 이하이면 벗어남',
  `state_label_0` varchar(20) COMMENT '0일 때 표시. 상태값에만 넣음 (예: 닫힘)',
  `state_label_1` varchar(20) COMMENT '1일 때 표시. 상태값에만 넣음 (예: 열림)',
  `alarm_state_value` tinyint COMMENT '알람 발생 값. 상태값에만 넣음. 0 또는 1. 비우면 알람을 만들지 않음',
  `alarm_state_grade` varchar(20) COMMENT '알람 등급. 상태값에만 넣음. 허용 값: CAUTION 주의 / CRITICAL 경보',
  `reset_time` time COMMENT '0으로 되돌리는 시각. 누적값에만 넣음. 매일 이 시각에 누적을 0으로 되돌림 (예: 00:00)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '표시 순서. 순서가 앞선 항목을 배치도의 주요 측정값으로 표시',
  `deleted_at` datetime COMMENT '삭제 시각. 비어 있으면 사용 중인 항목',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `part` (
  `part_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '부품 번호',
  `equipment_id` bigint NOT NULL COMMENT '설비 번호',
  `name` varchar(100) NOT NULL COMMENT '부품 이름. 예: 냉각 필터',
  `spec` varchar(100) COMMENT '규격',
  `manage_no` varchar(50) COMMENT '관리 번호',
  `manage_basis` varchar(20) NOT NULL COMMENT '수명 계산 기준. 허용 값: OPERATING_HOURS 가동 시간 / ELAPSED_DAYS 경과 일',
  `replace_limit` int NOT NULL COMMENT '교체 기준 값. 가동 시간 기준이면 시간. 경과 일 기준이면 일',
  `last_replaced_at` datetime NOT NULL COMMENT '최근 교체 일시. 처음 등록할 때는 장착한 일시',
  `deleted_at` datetime COMMENT '삭제 시각. 비어 있으면 사용 중인 부품',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `system_setting` (
  `setting_key` varchar(50) PRIMARY KEY COMMENT '설정 키. 예: COMM_TIMEOUT_SEC',
  `name` varchar(100) NOT NULL COMMENT '설정 이름. 예: 통신 끊김 판단 시간',
  `setting_group` varchar(20) NOT NULL COMMENT '구분. 허용 값: JUDGE 판정 설정 / CALC 계산 기준값',
  `setting_value` varchar(100) NOT NULL COMMENT '값',
  `unit` varchar(20) COMMENT '단위',
  `min_value` double COMMENT '허용 최솟값',
  `max_value` double COMMENT '허용 최댓값',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `measurement` (
  `data_item_id` bigint NOT NULL COMMENT '항목 번호',
  `measured_at` datetime(3) NOT NULL COMMENT '측정 시각. 설비가 값을 잰 시각',
  `measured_value` double NOT NULL COMMENT '값',
  `received_at` datetime(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)) COMMENT '받은 시각. 서버가 값을 받은 시각. 측정 시각과의 차이로 지연을 확인',
  PRIMARY KEY (`data_item_id`, `measured_at`)
);

CREATE TABLE `measurement_latest` (
  `data_item_id` bigint PRIMARY KEY COMMENT '항목 번호',
  `measured_value` double NOT NULL COMMENT '값',
  `measured_at` datetime(3) NOT NULL COMMENT '측정 시각',
  `received_at` datetime(3) NOT NULL COMMENT '받은 시각. 항목의 마지막 통신 시각',
  `value_grade` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '값 구분. 지금 값을 기준과 비교한 결과. 허용 값: NORMAL 정상 / CAUTION 주의 / CRITICAL 경보',
  `out_streak` int NOT NULL DEFAULT 0 COMMENT '벗어남 연속 횟수. 주의 기준을 연속으로 벗어난 횟수. 기준 안의 값이 들어오면 0',
  `critical_streak` int NOT NULL DEFAULT 0 COMMENT '경보 연속 횟수. 경보 기준을 연속으로 벗어난 횟수. 아니면 0',
  `normal_streak` int NOT NULL DEFAULT 0 COMMENT '정상 연속 횟수. 기준 안의 값이 연속으로 들어온 횟수. 벗어나면 0'
);

CREATE TABLE `equipment_state` (
  `equipment_id` bigint PRIMARY KEY COMMENT '설비 번호',
  `status` varchar(20) NOT NULL COMMENT '상태. 허용 값: RUNNING 가동 / IDLE 대기 / CAUTION 주의 / FAULT 고장 / COMMUNICATION_LOST 통신 끊김',
  `status_since` datetime NOT NULL COMMENT '상태 시작 시각',
  `last_received_at` datetime(3) COMMENT '마지막 통신 시각. 이 설비의 값을 마지막으로 받은 시각',
  `updated_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '수정 시각'
);

CREATE TABLE `equipment_status_history` (
  `history_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '이력 번호',
  `equipment_id` bigint NOT NULL COMMENT '설비 번호',
  `status` varchar(20) NOT NULL COMMENT '상태. 허용 값: RUNNING 가동 / IDLE 대기 / CAUTION 주의 / FAULT 고장 / COMMUNICATION_LOST 통신 끊김',
  `started_at` datetime NOT NULL COMMENT '시작 시각',
  `ended_at` datetime COMMENT '끝난 시각. 비어 있으면 지금 이어지고 있는 상태'
);

CREATE TABLE `production_log` (
  `equipment_id` bigint NOT NULL COMMENT '설비 번호',
  `minute_at` datetime NOT NULL COMMENT '분 단위 시각. 초를 버린 시각 (예: 17:13:00)',
  `qty` int NOT NULL DEFAULT 0 COMMENT '생산 수. 그 1분 동안 생산한 수',
  PRIMARY KEY (`equipment_id`, `minute_at`)
);

CREATE TABLE `stock_history` (
  `line_id` bigint NOT NULL COMMENT '라인 번호',
  `recorded_at` datetime NOT NULL COMMENT '기록 시각',
  `qty` int NOT NULL COMMENT '재고 수량',
  PRIMARY KEY (`line_id`, `recorded_at`)
);

CREATE TABLE `alarm` (
  `alarm_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '알람 번호',
  `equipment_id` bigint NOT NULL COMMENT '설비 번호. 항목의 설비와 같은 값. 조회를 빠르게 하려고 같이 저장',
  `data_item_id` bigint NOT NULL COMMENT '항목 번호',
  `grade` varchar(20) NOT NULL COMMENT '등급. 허용 값: CAUTION 주의 / CRITICAL 경보',
  `message` varchar(200) NOT NULL COMMENT '알람 내용. 예: 냉각액 유량 낮음',
  `trigger_value` double NOT NULL COMMENT '발생 때 값',
  `limit_value` double NOT NULL COMMENT '발생 때 기준. 알람을 만들 때 적용한 기준 값',
  `value_state` varchar(20) NOT NULL DEFAULT 'ABNORMAL' COMMENT '값 상태. 허용 값: ABNORMAL 기준 벗어남 / RECOVERED 정상 복귀',
  `process_state` varchar(20) NOT NULL DEFAULT 'UNACKNOWLEDGED' COMMENT '처리 상태. 허용 값: UNACKNOWLEDGED 미확인 / ACKNOWLEDGED 확인 / ACTION_TAKEN 조치 / CLOSED 종료',
  `process_result` varchar(20) COMMENT '처리 결과. 종료할 때 채움. 허용 값: EFFECTIVE 효과 있음 / FALSE_POSITIVE 오경보',
  `occurred_at` datetime NOT NULL COMMENT '발생 시각',
  `grade_changed_at` datetime COMMENT '등급 변경 시각. 주의에서 경보로 올라간 시각',
  `recovered_at` datetime COMMENT '값 정상 복귀 시각',
  `confirmed_at` datetime COMMENT '확인 시각',
  `confirmed_by` bigint COMMENT '확인자',
  `assignee_id` bigint COMMENT '담당자. 확인할 때 지정한 조치 담당자',
  `acted_at` datetime COMMENT '조치 시각. 가장 최근 조치를 저장한 시각',
  `closed_at` datetime COMMENT '종료 시각',
  `false_positive_reason` varchar(30) COMMENT '오경보 사유. 오경보로 신고할 때 고름. 허용 값: SENSOR_MALFUNCTION 센서 오작동 / THRESHOLD_MISMATCH 기준값 불일치 / INSPECTION_OR_TRIAL 점검 또는 시운전 / OTHER 기타',
  `false_positive_detail` varchar(500) COMMENT '오경보 상세 내용',
  `recurrence_count` int NOT NULL DEFAULT 0 COMMENT '재발 횟수',
  `renotified_at` datetime COMMENT '재알림 시각. 미확인 재알림을 보낸 시각. 알람마다 한 번만 보냄',
  `version` int NOT NULL DEFAULT 0 COMMENT '수정 번호. 동시 수정 방지용. 줄을 고칠 때마다 1씩 증가'
);

CREATE TABLE `alarm_action` (
  `action_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '조치 번호',
  `alarm_id` bigint NOT NULL COMMENT '알람 번호',
  `cause` varchar(200) COMMENT '원인',
  `action_content` varchar(500) NOT NULL COMMENT '조치 내용. 비어 있으면 저장 거부',
  `acted_by` bigint NOT NULL COMMENT '조치자',
  `acted_at` datetime NOT NULL COMMENT '조치 시각',
  `effect_result` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '효과. 허용 값: PENDING 확인 전 / EFFECTIVE 효과 있음 / RECURRED 재발',
  `verify_started_at` datetime COMMENT '효과 확인 시작 시각. 조치 뒤 값이 정상으로 돌아온 시각',
  `verify_target_count` int COMMENT '목표 개수. 시작할 때의 설정값을 복사',
  `verify_passed_count` int NOT NULL DEFAULT 0 COMMENT '확인 중 검사기 통과 수. 효과 확인을 시작한 뒤 라인 끝 검사기를 통과한 수',
  `verify_ended_at` datetime COMMENT '효과 확인 종료 시각'
);

CREATE TABLE `alarm_log` (
  `log_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '기록 번호',
  `alarm_id` bigint NOT NULL COMMENT '알람 번호',
  `event_type` varchar(20) NOT NULL COMMENT '처리 종류. 허용 값: OCCUR 발생 / GRADE_UP 등급 상향 / RECOVER 값 정상 복귀 / CONFIRM 확인과 담당 지정 / ACTION 조치 / VERIFY_START 효과 확인 시작 / RECUR 재발 / FALSE_POSITIVE 오경보 신고 / CLOSE 종료 / RENOTIFY 미확인 재알림',
  `actor_id` bigint COMMENT '처리자. 비어 있으면 서버가 자동으로 처리한 줄',
  `content` varchar(500) COMMENT '처리 내용',
  `created_at` datetime NOT NULL COMMENT '시각'
);

CREATE TABLE `notification_log` (
  `notification_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '전송 번호',
  `alarm_id` bigint COMMENT '알람 번호. 알람과 관계없는 전송은 비워 둠 (가입 승인 결과와 테스트 메시지 발송)',
  `account_id` bigint COMMENT '수신자. 가입을 거절한 신청자는 줄을 지우기 때문에 비워 둠',
  `receiver_name` varchar(50) NOT NULL COMMENT '수신자 이름. 보낼 때의 이름을 복사',
  `notify_type` varchar(20) NOT NULL COMMENT '알림 종류. 허용 값: OCCUR 발생 / GRADE_UP 등급 상향 / RENOTIFY 미확인 재알림 / RECUR 재발 / ASSIGN 조치 담당 지정 / SIGNUP_RESULT 가입 승인 결과 / TEST 테스트 메시지 발송',
  `message` varchar(1000) NOT NULL COMMENT '보낸 내용',
  `send_result` varchar(20) NOT NULL COMMENT '전송 결과. 허용 값: SUCCESS 성공 / FAILURE 실패',
  `fail_reason` varchar(500) COMMENT '실패 사유. 예: 사용자가 봇을 차단함',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '다시 보낸 횟수',
  `sent_at` datetime NOT NULL COMMENT '전송 시각'
);

CREATE TABLE `part_replacement` (
  `replacement_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '교체 번호',
  `part_id` bigint NOT NULL COMMENT '부품 번호',
  `replaced_at` datetime NOT NULL COMMENT '교체 일시',
  `reason` varchar(20) NOT NULL COMMENT '교체 사유. 허용 값: FAILURE 고장 / REGULAR 정기 / ETC 기타',
  `work_content` varchar(500) COMMENT '작업 내용',
  `worker_id` bigint NOT NULL COMMENT '작업자',
  `action_id` bigint COMMENT '조치 번호. 알람 조치에서 함께 등록한 교체이면 채움',
  `created_at` datetime NOT NULL DEFAULT (CURRENT_TIMESTAMP) COMMENT '등록 시각'
);

CREATE TABLE `assembly_stop` (
  `stop_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '정지 번호',
  `started_at` datetime NOT NULL COMMENT '정지 시작 시각',
  `ended_at` datetime COMMENT '정지 종료 시각. 비어 있으면 지금 멈춰 있는 상태',
  `shortage_line_id` bigint COMMENT '재고가 바닥난 라인. 부품 재고가 바닥나서 멈춘 경우에만 채움',
  `cause_equipment_id` bigint COMMENT '원인 설비. 고장으로 정지를 일으킨 설비',
  `cause_alarm_id` bigint COMMENT '원인 알람',
  `hourly_output` int NOT NULL COMMENT '시간당 생산량. 정지 때 수집한 값으로 계산한 시간당 생산량을 복사',
  `unit_price` bigint NOT NULL COMMENT '개당 금액. 원 단위. 정지 때의 설정값을 복사',
  `loss_amount` bigint COMMENT '손실액. 원 단위. 정지가 끝나면 계산해서 저장'
);

CREATE TABLE `change_log` (
  `change_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '변경 번호',
  `changed_at` datetime NOT NULL COMMENT '변경 시각',
  `changed_by` bigint NOT NULL COMMENT '변경자',
  `target_type` varchar(20) NOT NULL COMMENT '항목 종류. 허용 값: EQUIPMENT 설비 / LINE 라인 / DATA_ITEM 데이터 항목 / THRESHOLD 임계값 / PART 장착 부품 / SETTING 설정값 / ACCOUNT 계정 / DUTY 알람 당번',
  `target_name` varchar(100) NOT NULL COMMENT '바꾼 대상. 예: OR-04 냉각액 유량',
  `field_name` varchar(50) NOT NULL COMMENT '바꾼 항목. 예: 경보 기준',
  `old_value` varchar(500) COMMENT '이전 값',
  `new_value` varchar(500) COMMENT '변경 값'
);

CREATE INDEX `idx_auth_token_account` ON `auth_token` (`account_id`);

CREATE UNIQUE INDEX `uk_process_line_seq` ON `process` (`line_id`, `process_seq`);

CREATE INDEX `idx_equipment_process` ON `equipment` (`process_id`);

CREATE UNIQUE INDEX `uk_data_item_code` ON `data_item` (`equipment_id`, `item_code`);

CREATE INDEX `idx_part_equipment` ON `part` (`equipment_id`);

CREATE INDEX `idx_status_history_equipment` ON `equipment_status_history` (`equipment_id`, `started_at`);

CREATE INDEX `idx_alarm_state` ON `alarm` (`process_state`, `grade`, `occurred_at`);

CREATE INDEX `idx_alarm_equipment` ON `alarm` (`equipment_id`, `occurred_at`);

CREATE INDEX `idx_alarm_item` ON `alarm` (`data_item_id`, `occurred_at`);

CREATE INDEX `idx_alarm_closed` ON `alarm` (`closed_at`);

CREATE INDEX `idx_action_alarm` ON `alarm_action` (`alarm_id`, `acted_at`);

CREATE INDEX `idx_alarm_log_alarm` ON `alarm_log` (`alarm_id`, `created_at`);

CREATE INDEX `idx_notification_sent` ON `notification_log` (`sent_at`);

CREATE INDEX `idx_notification_account` ON `notification_log` (`account_id`, `sent_at`);

CREATE INDEX `idx_notification_alarm` ON `notification_log` (`alarm_id`);

CREATE INDEX `idx_replacement_part` ON `part_replacement` (`part_id`, `replaced_at`);

CREATE INDEX `idx_stop_started` ON `assembly_stop` (`started_at`);

CREATE INDEX `idx_change_at` ON `change_log` (`changed_at`);

CREATE INDEX `idx_change_type` ON `change_log` (`target_type`, `changed_at`);















































ALTER TABLE `account` ADD CONSTRAINT `fk_account_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `account` (`account_id`);

ALTER TABLE `auth_token` ADD CONSTRAINT `fk_auth_token_account_id` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `duty_member` ADD CONSTRAINT `fk_duty_member_duty_id` FOREIGN KEY (`duty_id`) REFERENCES `duty` (`duty_id`) ON DELETE CASCADE;

ALTER TABLE `duty_member` ADD CONSTRAINT `fk_duty_member_account_id` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `process` ADD CONSTRAINT `fk_process_line_id` FOREIGN KEY (`line_id`) REFERENCES `line` (`line_id`);

ALTER TABLE `equipment` ADD CONSTRAINT `fk_equipment_process_id` FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`);

ALTER TABLE `data_item` ADD CONSTRAINT `fk_data_item_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `part` ADD CONSTRAINT `fk_part_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `measurement` ADD CONSTRAINT `fk_measurement_data_item_id` FOREIGN KEY (`data_item_id`) REFERENCES `data_item` (`data_item_id`);

ALTER TABLE `measurement_latest` ADD CONSTRAINT `fk_measurement_latest_data_item_id` FOREIGN KEY (`data_item_id`) REFERENCES `data_item` (`data_item_id`);

ALTER TABLE `equipment_state` ADD CONSTRAINT `fk_equipment_state_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `equipment_status_history` ADD CONSTRAINT `fk_equipment_status_history_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `production_log` ADD CONSTRAINT `fk_production_log_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `stock_history` ADD CONSTRAINT `fk_stock_history_line_id` FOREIGN KEY (`line_id`) REFERENCES `line` (`line_id`) ON DELETE CASCADE;

ALTER TABLE `alarm` ADD CONSTRAINT `fk_alarm_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `alarm` ADD CONSTRAINT `fk_alarm_data_item_id` FOREIGN KEY (`data_item_id`) REFERENCES `data_item` (`data_item_id`);

ALTER TABLE `alarm` ADD CONSTRAINT `fk_alarm_confirmed_by` FOREIGN KEY (`confirmed_by`) REFERENCES `account` (`account_id`);

ALTER TABLE `alarm` ADD CONSTRAINT `fk_alarm_assignee_id` FOREIGN KEY (`assignee_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `alarm_action` ADD CONSTRAINT `fk_alarm_action_alarm_id` FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`);

ALTER TABLE `alarm_action` ADD CONSTRAINT `fk_alarm_action_acted_by` FOREIGN KEY (`acted_by`) REFERENCES `account` (`account_id`);

ALTER TABLE `alarm_log` ADD CONSTRAINT `fk_alarm_log_alarm_id` FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`);

ALTER TABLE `alarm_log` ADD CONSTRAINT `fk_alarm_log_actor_id` FOREIGN KEY (`actor_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `notification_log` ADD CONSTRAINT `fk_notification_log_alarm_id` FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`);

ALTER TABLE `notification_log` ADD CONSTRAINT `fk_notification_log_account_id` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `part_replacement` ADD CONSTRAINT `fk_part_replacement_part_id` FOREIGN KEY (`part_id`) REFERENCES `part` (`part_id`);

ALTER TABLE `part_replacement` ADD CONSTRAINT `fk_part_replacement_worker_id` FOREIGN KEY (`worker_id`) REFERENCES `account` (`account_id`);

ALTER TABLE `part_replacement` ADD CONSTRAINT `fk_part_replacement_action_id` FOREIGN KEY (`action_id`) REFERENCES `alarm_action` (`action_id`);

ALTER TABLE `assembly_stop` ADD CONSTRAINT `fk_assembly_stop_shortage_line_id` FOREIGN KEY (`shortage_line_id`) REFERENCES `line` (`line_id`);

ALTER TABLE `assembly_stop` ADD CONSTRAINT `fk_assembly_stop_cause_equipment_id` FOREIGN KEY (`cause_equipment_id`) REFERENCES `equipment` (`equipment_id`);

ALTER TABLE `assembly_stop` ADD CONSTRAINT `fk_assembly_stop_cause_alarm_id` FOREIGN KEY (`cause_alarm_id`) REFERENCES `alarm` (`alarm_id`);

ALTER TABLE `change_log` ADD CONSTRAINT `fk_change_log_changed_by` FOREIGN KEY (`changed_by`) REFERENCES `account` (`account_id`);

-- ───────── DBML로 표현할 수 없어서 직접 넣은 제약 ─────────
-- 같은 항목에 종료되지 않은 알람은 한 건만 있게 합니다 (기능 4.3 중복 알람 방지)
-- 종료된 알람은 open_item_id가 NULL이라서 여러 건이 있어도 됩니다
-- 이 컬럼은 DB가 계산합니다. JPA 엔티티에는 넣지 않습니다

-- Back_B: align writable B fields with the approved v5 OpenAPI contract.
-- V1/V2 are preserved. Apply this migration after DB review on the shared server.
ALTER TABLE alarm_action ALTER COLUMN cause varchar(1000);
ALTER TABLE alarm_action ALTER COLUMN action_content varchar(4000) NOT NULL;
ALTER TABLE alarm ALTER COLUMN false_positive_detail varchar(2000);
ALTER TABLE part_replacement ALTER COLUMN reason varchar(1000) NOT NULL;
ALTER TABLE part_replacement ALTER COLUMN work_content varchar(4000);
ALTER TABLE part ALTER COLUMN replace_limit decimal(18,4) NOT NULL;
ALTER TABLE alarm_log ALTER COLUMN content text;
CREATE TABLE backend_event_claim (event_id varchar(255) PRIMARY KEY, expires_at datetime(3) NOT NULL);
CREATE INDEX idx_backend_event_claim_expiry ON backend_event_claim(expires_at);
CREATE TABLE backend_job_lock (lock_name varchar(50) PRIMARY KEY);
INSERT INTO backend_job_lock(lock_name) VALUES ('DUTY');
CREATE TABLE backend_alarm_impact (
  alarm_id bigint PRIMARY KEY,
  expected_stop_at datetime NOT NULL,
  expected_downtime_minutes decimal(18,4) NOT NULL,
  hourly_output decimal(18,4) NOT NULL,
  unit_price decimal(18,4) NOT NULL,
  recovered_at datetime,
  CONSTRAINT fk_backend_impact_alarm FOREIGN KEY (alarm_id) REFERENCES alarm(alarm_id)
);
CREATE TABLE backend_event_outbox (
  outbox_id bigint PRIMARY KEY AUTO_INCREMENT,
  event_type varchar(50) NOT NULL,
  payload text NOT NULL,
  created_at datetime(3) NOT NULL,
  published_at datetime(3)
);
CREATE INDEX idx_backend_outbox_pending ON backend_event_outbox(published_at,outbox_id);

-- A V4 authentication schema, adapted for H2.
ALTER TABLE account ADD COLUMN last_login_at datetime;
ALTER TABLE auth_token ADD COLUMN family_id varchar(36) NOT NULL;
ALTER TABLE auth_token ADD COLUMN used_at datetime;
CREATE INDEX idx_auth_token_family ON auth_token(family_id);

ALTER TABLE account ADD COLUMN deleted_by bigint;
ALTER TABLE alarm_action ADD COLUMN equipment_text varchar(200);
ALTER TABLE measurement_latest ADD COLUMN counter_status varchar(30) DEFAULT 'READY';
ALTER TABLE measurement_latest ADD COLUMN counter_epoch bigint DEFAULT 0;
ALTER TABLE measurement_latest ADD COLUMN counter_reset_at timestamp;

ALTER TABLE account ALTER COLUMN name varchar(100);
ALTER TABLE alarm ADD active_data_item_id bigint GENERATED ALWAYS AS (CASE WHEN process_state='ACTIVE' THEN data_item_id ELSE NULL END);
CREATE UNIQUE INDEX uq_alarm_active_item ON alarm(equipment_id,active_data_item_id);
