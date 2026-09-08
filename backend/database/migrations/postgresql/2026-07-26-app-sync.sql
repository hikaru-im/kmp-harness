BEGIN;

ALTER TABLE member_address
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 1;

ALTER TABLE member_user
  ADD COLUMN IF NOT EXISTS profile_version bigint NOT NULL DEFAULT 1;
COMMENT ON COLUMN member_user.profile_version IS '个人资料业务版本号';

ALTER TABLE member_sign_in_record
  ADD COLUMN IF NOT EXISTS sign_date date NULL;
COMMENT ON COLUMN member_sign_in_record.sign_date IS '业务签到日期（历史记录可为空）';

CREATE UNIQUE INDEX IF NOT EXISTS member_sign_in_record_uk_tenant_user_sign_date
  ON member_sign_in_record (tenant_id, user_id, sign_date);

CREATE TABLE IF NOT EXISTS app_sync_command (
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  command_id varchar(128) NOT NULL,
  aggregate_type varchar(64) NOT NULL,
  aggregate_id varchar(128) NULL,
  operation varchar(32) NOT NULL,
  base_version bigint NULL,
  request_hash varchar(64) NOT NULL,
  outcome varchar(16) NULL,
  result_json text NULL,
  error_code varchar(64) NULL,
  server_version bigint NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at timestamp without time zone NULL,
  CONSTRAINT app_sync_command_pkey PRIMARY KEY (tenant_id, user_id, command_id)
);

CREATE INDEX IF NOT EXISTS app_sync_command_idx_sync_command_created
  ON app_sync_command (tenant_id, user_id, created_at);

CREATE SEQUENCE IF NOT EXISTS app_sync_change_seq AS bigint;
CREATE TABLE IF NOT EXISTS app_sync_change (
  cursor bigint NOT NULL DEFAULT nextval('app_sync_change_seq'::regclass),
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  resource varchar(64) NOT NULL,
  aggregate_id varchar(128) NOT NULL,
  operation varchar(16) NOT NULL,
  aggregate_version bigint NOT NULL,
  payload text NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT app_sync_change_pkey PRIMARY KEY (cursor)
);
ALTER SEQUENCE app_sync_change_seq OWNED BY app_sync_change.cursor;

CREATE INDEX IF NOT EXISTS app_sync_change_idx_sync_change_scope
  ON app_sync_change (tenant_id, user_id, resource, cursor);

CREATE INDEX IF NOT EXISTS app_sync_change_idx_sync_change_created
  ON app_sync_change (created_at, cursor);

CREATE TABLE IF NOT EXISTS app_sync_change_retention (
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  resource varchar(64) NOT NULL,
  retention_cursor bigint NOT NULL,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT app_sync_change_retention_pkey PRIMARY KEY (tenant_id, user_id, resource)
);

COMMIT;
