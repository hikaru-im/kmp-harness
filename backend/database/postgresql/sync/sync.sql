-- PostgreSQL schema for yudao-module-sync.
-- Generated from backend/database/sync-2026-07-26.sql; source SHA-256: 415234a67de5d5c18b1d1b2baf43c6ebfdaedb120bcdce49f57cfb4b07d06a66.
-- Destructive: drops and recreates only this module's tables and sequences.
-- Run tools/module-sql-sync.mjs --check to verify this file.

BEGIN;

DROP TABLE IF EXISTS app_sync_push_device;
DROP TABLE IF EXISTS app_sync_change_retention;
DROP TABLE IF EXISTS app_sync_change;
DROP TABLE IF EXISTS app_sync_command;
DROP SEQUENCE IF EXISTS app_sync_change_seq;

-- ----------------------------
-- Table structure for app_sync_command
-- ----------------------------
CREATE TABLE app_sync_command (
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  command_id varchar(128) NOT NULL,
  aggregate_type varchar(64) NOT NULL,
  aggregate_id varchar(128) DEFAULT NULL NULL,
  operation varchar(32) NOT NULL,
  base_version bigint DEFAULT NULL NULL,
  request_hash varchar(64) NOT NULL,
  outcome varchar(16) DEFAULT NULL NULL,
  result_json text NULL,
  error_code varchar(64) DEFAULT NULL NULL,
  server_version bigint DEFAULT NULL NULL,
  created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  completed_at timestamp without time zone DEFAULT NULL NULL,
  CONSTRAINT app_sync_command_pkey PRIMARY KEY (tenant_id, user_id, command_id)
);
-- MySQL index name: idx_sync_command_created
CREATE INDEX app_sync_command_idx_sync_command_created ON app_sync_command (tenant_id, user_id, created_at);
COMMENT ON TABLE app_sync_command IS E'App 同步命令幂等记录';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN app_sync_command.tenant_id IS E'租户编号';
COMMENT ON COLUMN app_sync_command.user_id IS E'用户编号';
COMMENT ON COLUMN app_sync_command.command_id IS E'客户端生成的稳定命令编号';
COMMENT ON COLUMN app_sync_command.aggregate_type IS E'聚合类型';
COMMENT ON COLUMN app_sync_command.aggregate_id IS E'聚合编号';
COMMENT ON COLUMN app_sync_command.operation IS E'命令操作';
COMMENT ON COLUMN app_sync_command.base_version IS E'客户端编辑基准版本';
COMMENT ON COLUMN app_sync_command.request_hash IS E'规范化请求哈希';
COMMENT ON COLUMN app_sync_command.outcome IS E'确定结果';
COMMENT ON COLUMN app_sync_command.result_json IS E'首次确定结果 JSON';
COMMENT ON COLUMN app_sync_command.error_code IS E'稳定错误码';
COMMENT ON COLUMN app_sync_command.server_version IS E'服务端业务版本';
COMMENT ON COLUMN app_sync_command.created_at IS E'登记时间';
COMMENT ON COLUMN app_sync_command.completed_at IS E'完成时间';

-- ----------------------------
-- Table structure for app_sync_change
-- ----------------------------
CREATE SEQUENCE app_sync_change_seq AS bigint START WITH 1;
CREATE TABLE app_sync_change (
  cursor bigint DEFAULT nextval('app_sync_change_seq'::regclass) NOT NULL,
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  resource varchar(64) NOT NULL,
  aggregate_id varchar(128) NOT NULL,
  operation varchar(16) NOT NULL,
  aggregate_version bigint NOT NULL,
  payload text NOT NULL,
  created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT app_sync_change_pkey PRIMARY KEY (cursor)
);
ALTER SEQUENCE app_sync_change_seq OWNED BY app_sync_change.cursor;
-- MySQL index name: idx_sync_change_scope
CREATE INDEX app_sync_change_idx_sync_change_scope ON app_sync_change (tenant_id, user_id, resource, cursor);
-- MySQL index name: idx_sync_change_created
CREATE INDEX app_sync_change_idx_sync_change_created ON app_sync_change (created_at, cursor);
COMMENT ON TABLE app_sync_change IS E'App 增量同步变更';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN app_sync_change.cursor IS E'全局单调游标';
COMMENT ON COLUMN app_sync_change.tenant_id IS E'租户编号';
COMMENT ON COLUMN app_sync_change.user_id IS E'用户编号';
COMMENT ON COLUMN app_sync_change.resource IS E'资源类型';
COMMENT ON COLUMN app_sync_change.aggregate_id IS E'聚合编号';
COMMENT ON COLUMN app_sync_change.operation IS E'UPSERT 或 DELETE';
COMMENT ON COLUMN app_sync_change.aggregate_version IS E'资源业务版本';
COMMENT ON COLUMN app_sync_change.payload IS E'资源快照或删除墓碑 JSON';
COMMENT ON COLUMN app_sync_change.created_at IS E'创建时间';

-- ----------------------------
-- Table structure for app_sync_change_retention
-- ----------------------------
CREATE TABLE app_sync_change_retention (
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  resource varchar(64) NOT NULL,
  retention_cursor bigint NOT NULL,
  updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT app_sync_change_retention_pkey PRIMARY KEY (tenant_id, user_id, resource)
);
COMMENT ON TABLE app_sync_change_retention IS E'App 同步变更保留水位';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN app_sync_change_retention.tenant_id IS E'租户编号';
COMMENT ON COLUMN app_sync_change_retention.user_id IS E'用户编号';
COMMENT ON COLUMN app_sync_change_retention.resource IS E'资源类型';
COMMENT ON COLUMN app_sync_change_retention.retention_cursor IS E'已清理变更的最大游标';
COMMENT ON COLUMN app_sync_change_retention.updated_at IS E'水位更新时间';

-- ----------------------------
-- Table structure for app_sync_push_device
-- ----------------------------
CREATE TABLE app_sync_push_device (
  token varchar(512) NOT NULL,
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  platform varchar(16) NOT NULL,
  updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT app_sync_push_device_pkey PRIMARY KEY (token)
);
-- MySQL index name: idx_sync_push_device_scope
CREATE INDEX app_sync_push_device_idx_sync_push_device_scope ON app_sync_push_device (tenant_id, user_id, updated_at);
COMMENT ON TABLE app_sync_push_device IS E'App 同步 Push 设备';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN app_sync_push_device.token IS E'FCM 设备令牌';
COMMENT ON COLUMN app_sync_push_device.tenant_id IS E'当前租户编号';
COMMENT ON COLUMN app_sync_push_device.user_id IS E'当前用户编号';
COMMENT ON COLUMN app_sync_push_device.platform IS E'ANDROID 或 IOS';
COMMENT ON COLUMN app_sync_push_device.updated_at IS E'最近注册时间';

COMMIT;
