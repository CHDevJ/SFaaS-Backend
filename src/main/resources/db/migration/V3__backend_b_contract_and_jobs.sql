-- Back_B: align writable B fields with the approved v5 OpenAPI contract.
-- V1/V2 are preserved. Apply this migration after DB review on the shared server.
ALTER TABLE alarm_action MODIFY cause varchar(1000), MODIFY action_content varchar(4000) NOT NULL;
ALTER TABLE alarm MODIFY false_positive_detail varchar(2000);
ALTER TABLE part_replacement MODIFY reason varchar(1000) NOT NULL, MODIFY work_content varchar(4000);
ALTER TABLE part MODIFY replace_limit decimal(18,4) NOT NULL;
ALTER TABLE alarm_log MODIFY content text;
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
