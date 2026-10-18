-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE write_guard (id BIGINT PRIMARY KEY);
INSERT INTO write_guard VALUES (1);
CREATE TABLE department (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(60) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  kind VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL
);
CREATE TABLE access_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(120) NOT NULL,
  scope VARCHAR(20) NOT NULL,
  permissions VARCHAR(1000) NOT NULL
);
CREATE TABLE account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  username VARCHAR(60) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  email VARCHAR(254) NOT NULL,
  mail_opt_in BOOLEAN NOT NULL,
  enabled BOOLEAN NOT NULL
);
CREATE TABLE dictionary_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  group_code VARCHAR(40) NOT NULL,
  code VARCHAR(60) NOT NULL UNIQUE,
  label VARCHAR(120) NOT NULL,
  enabled BOOLEAN NOT NULL
);
CREATE TABLE system_setting (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(60) NOT NULL UNIQUE,
  label VARCHAR(120) NOT NULL,
  parameter_value VARCHAR(1000) NOT NULL
);
CREATE TABLE nav_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(40) NOT NULL UNIQUE,
  label VARCHAR(120) NOT NULL,
  permission VARCHAR(60) NOT NULL,
  position INT NOT NULL,
  enabled BOOLEAN NOT NULL
);
CREATE TABLE disclosure (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(240) NOT NULL,
  right_type VARCHAR(20) NOT NULL,
  summary VARCHAR(6000) NOT NULL,
  department_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  submitter_id BIGINT,
  reviewer_id BIGINT,
  state VARCHAR(30) NOT NULL,
  review_note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  reviewed_at DATETIME(6)
);
CREATE TABLE ip_matter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(60) NOT NULL UNIQUE,
  title VARCHAR(240) NOT NULL,
  right_type VARCHAR(20) NOT NULL,
  jurisdiction VARCHAR(80) NOT NULL,
  applicant VARCHAR(240) NOT NULL,
  application_no VARCHAR(120) NOT NULL,
  filed_on DATE,
  department_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  agent_id BIGINT,
  proposal_id BIGINT NOT NULL UNIQUE,
  state VARCHAR(30) NOT NULL,
  close_by_id BIGINT,
  review_note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE evidence_file (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  matter_id BIGINT NOT NULL,
  file_name VARCHAR(160) NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  digest VARCHAR(64) NOT NULL,
  version_number INT NOT NULL,
  creator_id BIGINT NOT NULL,
  shared BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL,
  note VARCHAR(600) NOT NULL,
  content LONGBLOB NOT NULL
);
CREATE TABLE case_deadline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  matter_id BIGINT NOT NULL,
  previous_id BIGINT,
  title VARCHAR(240) NOT NULL,
  kind_id BIGINT NOT NULL,
  due_on DATE NOT NULL,
  source_file_id BIGINT NOT NULL,
  responsible_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  reviewer_id BIGINT,
  state VARCHAR(30) NOT NULL,
  reason VARCHAR(1600) NOT NULL,
  review_note VARCHAR(1600) NOT NULL,
  completion_by_id BIGINT,
  completion_file_id BIGINT,
  completion_note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE case_fee (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  matter_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  reference VARCHAR(120) NOT NULL,
  payee VARCHAR(240) NOT NULL,
  amount_cents BIGINT NOT NULL,
  description VARCHAR(1600) NOT NULL,
  source_file_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  submitter_id BIGINT,
  reviewer_id BIGINT,
  state VARCHAR(30) NOT NULL,
  review_note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE payment_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  fee_id BIGINT NOT NULL,
  reverse_of_id BIGINT,
  reference VARCHAR(120) NOT NULL UNIQUE,
  amount_cents BIGINT NOT NULL,
  paid_on DATE NOT NULL,
  proof_file_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  reviewer_id BIGINT,
  state VARCHAR(30) NOT NULL,
  note VARCHAR(1600) NOT NULL,
  review_note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  deadline_id BIGINT NOT NULL,
  recipient_id BIGINT NOT NULL,
  dedupe_key VARCHAR(240) NOT NULL UNIQUE,
  channel VARCHAR(20) NOT NULL,
  state VARCHAR(30) NOT NULL,
  email VARCHAR(254) NOT NULL,
  attempts INT NOT NULL,
  reason VARCHAR(120) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  claimed_at DATETIME(6),
  sent_at DATETIME(6)
);
CREATE TABLE audit_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  actor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  matter_id BIGINT,
  action VARCHAR(80) NOT NULL,
  object_ref VARCHAR(120) NOT NULL,
  note VARCHAR(1600) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
ALTER TABLE account ADD CONSTRAINT fk_account_role_id FOREIGN KEY (role_id) REFERENCES access_role(id);
ALTER TABLE account ADD CONSTRAINT fk_account_department_id FOREIGN KEY (department_id) REFERENCES department(id);
ALTER TABLE disclosure ADD CONSTRAINT fk_disclosure_department_id FOREIGN KEY (department_id) REFERENCES department(id);
ALTER TABLE disclosure ADD CONSTRAINT fk_disclosure_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE disclosure ADD CONSTRAINT fk_disclosure_submitter_id FOREIGN KEY (submitter_id) REFERENCES account(id);
ALTER TABLE disclosure ADD CONSTRAINT fk_disclosure_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES account(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_department_id FOREIGN KEY (department_id) REFERENCES department(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_owner_id FOREIGN KEY (owner_id) REFERENCES account(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_agent_id FOREIGN KEY (agent_id) REFERENCES account(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_proposal_id FOREIGN KEY (proposal_id) REFERENCES disclosure(id);
ALTER TABLE ip_matter ADD CONSTRAINT fk_ip_matter_close_by_id FOREIGN KEY (close_by_id) REFERENCES account(id);
ALTER TABLE evidence_file ADD CONSTRAINT fk_evidence_file_matter_id FOREIGN KEY (matter_id) REFERENCES ip_matter(id);
ALTER TABLE evidence_file ADD CONSTRAINT fk_evidence_file_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_matter_id FOREIGN KEY (matter_id) REFERENCES ip_matter(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_previous_id FOREIGN KEY (previous_id) REFERENCES case_deadline(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_kind_id FOREIGN KEY (kind_id) REFERENCES dictionary_entry(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_source_file_id FOREIGN KEY (source_file_id) REFERENCES evidence_file(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_responsible_id FOREIGN KEY (responsible_id) REFERENCES account(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES account(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_completion_by_id FOREIGN KEY (completion_by_id) REFERENCES account(id);
ALTER TABLE case_deadline ADD CONSTRAINT fk_case_deadline_completion_file_id FOREIGN KEY (completion_file_id) REFERENCES evidence_file(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_matter_id FOREIGN KEY (matter_id) REFERENCES ip_matter(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_category_id FOREIGN KEY (category_id) REFERENCES dictionary_entry(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_source_file_id FOREIGN KEY (source_file_id) REFERENCES evidence_file(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_submitter_id FOREIGN KEY (submitter_id) REFERENCES account(id);
ALTER TABLE case_fee ADD CONSTRAINT fk_case_fee_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES account(id);
ALTER TABLE payment_record ADD CONSTRAINT fk_payment_record_fee_id FOREIGN KEY (fee_id) REFERENCES case_fee(id);
ALTER TABLE payment_record ADD CONSTRAINT fk_payment_record_reverse_of_id FOREIGN KEY (reverse_of_id) REFERENCES payment_record(id);
ALTER TABLE payment_record ADD CONSTRAINT fk_payment_record_proof_file_id FOREIGN KEY (proof_file_id) REFERENCES evidence_file(id);
ALTER TABLE payment_record ADD CONSTRAINT fk_payment_record_creator_id FOREIGN KEY (creator_id) REFERENCES account(id);
ALTER TABLE payment_record ADD CONSTRAINT fk_payment_record_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES account(id);
ALTER TABLE notification ADD CONSTRAINT fk_notification_deadline_id FOREIGN KEY (deadline_id) REFERENCES case_deadline(id);
ALTER TABLE notification ADD CONSTRAINT fk_notification_recipient_id FOREIGN KEY (recipient_id) REFERENCES account(id);
ALTER TABLE audit_event ADD CONSTRAINT fk_audit_event_actor_id FOREIGN KEY (actor_id) REFERENCES account(id);
ALTER TABLE audit_event ADD CONSTRAINT fk_audit_event_department_id FOREIGN KEY (department_id) REFERENCES department(id);
ALTER TABLE audit_event ADD CONSTRAINT fk_audit_event_matter_id FOREIGN KEY (matter_id) REFERENCES ip_matter(id);
CREATE UNIQUE INDEX uq_fee_reference ON case_fee(matter_id, reference);
CREATE INDEX idx_deadline_due ON case_deadline(state,due_on);
CREATE INDEX idx_notification_state ON notification(state,channel);
CREATE INDEX idx_matter_dept ON ip_matter(department_id);
