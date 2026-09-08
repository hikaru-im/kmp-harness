BEGIN;

CREATE TABLE IF NOT EXISTS app_sync_push_device (
  token varchar(512) NOT NULL,
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  platform varchar(16) NOT NULL,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT app_sync_push_device_pkey PRIMARY KEY (token)
);

CREATE INDEX IF NOT EXISTS app_sync_push_device_idx_sync_push_device_scope
  ON app_sync_push_device (tenant_id, user_id, updated_at);

COMMIT;
