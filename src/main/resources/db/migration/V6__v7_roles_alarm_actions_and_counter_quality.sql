-- API v7 transition. DB-owner V5 is preserved byte-for-byte.
-- Historical duty/part/effect rows and fields are retained.
UPDATE account SET role=CASE WHEN role='SYSTEM_ADMIN' THEN 'ADMIN' ELSE 'USER' END
 WHERE role IN ('SYSTEM_ADMIN','LINE_MANAGER','MAINTENANCE');
ALTER TABLE account MODIFY name varchar(100) NOT NULL, ADD COLUMN deleted_by bigint NULL;
UPDATE alarm SET process_state='ACTIVE' WHERE process_state IN ('UNACKNOWLEDGED','ACKNOWLEDGED','ACTION_TAKEN','ACTION');
ALTER TABLE alarm MODIFY process_state varchar(20) NOT NULL DEFAULT 'ACTIVE', MODIFY message varchar(1000) NOT NULL;
ALTER TABLE alarm_action ADD COLUMN equipment_text varchar(200) NULL;
-- A duplicate ACTIVE pair requires review: this unique constraint intentionally refuses to discard history.
ALTER TABLE alarm ADD COLUMN active_data_item_id bigint
 GENERATED ALWAYS AS (CASE WHEN process_state='ACTIVE' THEN data_item_id ELSE NULL END) STORED;
CREATE UNIQUE INDEX uq_alarm_active_item ON alarm(equipment_id,active_data_item_id);
ALTER TABLE measurement_latest ADD COLUMN counter_status varchar(30) NOT NULL DEFAULT 'READY',
 ADD COLUMN counter_epoch bigint NOT NULL DEFAULT 0, ADD COLUMN counter_reset_at datetime(3) NULL;
