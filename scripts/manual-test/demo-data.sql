-- Fictional manual-test fixtures. Only the dedicated stf_b_manual database is allowed.
USE stf_b_manual;
SET NAMES utf8mb4;
START TRANSACTION;
SET @equipment=(SELECT equipment_id FROM equipment WHERE equip_code='OR-04-1');
SET @item=(SELECT data_item_id FROM data_item WHERE equipment_id=@equipment AND item_code='coolant_flow');
SET @maint=(SELECT account_id FROM account WHERE login_id='maint01');
INSERT INTO part(equipment_id,name,spec,manage_no,manage_basis,replace_limit,last_replaced_at)
VALUES(@equipment,'[테스트] 냉각 필터','시연용','MANUAL-DEMO-001','ELAPSED_DAYS',30,DATE_SUB(NOW(),INTERVAL 25 DAY));
INSERT INTO alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at)
VALUES(@equipment,@item,'CRITICAL','[테스트] 냉각액 유량 부족 - 확인/조치 연습',50,64,DATE_SUB(NOW(),INTERVAL 5 MINUTE));
SET @alarm=LAST_INSERT_ID();
INSERT INTO alarm_log(alarm_id,event_type,content,created_at) VALUES(@alarm,'OCCUR','수동 테스트용 가상 알람',DATE_SUB(NOW(),INTERVAL 5 MINUTE));
INSERT INTO alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,value_state,process_state,process_result,occurred_at,recovered_at,closed_at)
VALUES(@equipment,@item,'CAUTION','[테스트] 지난 오경보 이력',70,72,'RECOVERED','CLOSED','FALSE_POSITIVE',DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_SUB(NOW(),INTERVAL 23 HOUR),DATE_SUB(NOW(),INTERVAL 23 HOUR));
INSERT INTO measurement(data_item_id,measured_at,measured_value,received_at)
SELECT @item,TIMESTAMPADD(MINUTE,-n,NOW(3)),IF(n<5,50,80),TIMESTAMPADD(MINUTE,-n,NOW(3))
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10) t;
INSERT INTO measurement_latest(data_item_id,measured_value,measured_at,received_at,value_grade,out_streak,critical_streak)
VALUES(@item,50,NOW(3),NOW(3),'CRITICAL',3,3);
UPDATE equipment_state SET status='FAULT',status_since=DATE_SUB(NOW(),INTERVAL 5 MINUTE),last_received_at=NOW(3) WHERE equipment_id=@equipment;
INSERT INTO equipment_status_history(equipment_id,status,started_at,ended_at)
VALUES(@equipment,'RUNNING',DATE_SUB(NOW(),INTERVAL 8 HOUR),DATE_SUB(NOW(),INTERVAL 5 MINUTE)),(@equipment,'FAULT',DATE_SUB(NOW(),INTERVAL 5 MINUTE),NULL);
INSERT INTO stock_history(line_id,recorded_at,qty) SELECT line_id,NOW(),180 FROM line WHERE line_type='PART';
COMMIT;
