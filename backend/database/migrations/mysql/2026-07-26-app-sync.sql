ALTER TABLE `member_address`
  ADD COLUMN IF NOT EXISTS `version` bigint NOT NULL DEFAULT 1 COMMENT '业务版本号' AFTER `default_status`;

ALTER TABLE `member_user`
  ADD COLUMN IF NOT EXISTS `profile_version` bigint NOT NULL DEFAULT 1 COMMENT '个人资料业务版本号' AFTER `avatar`;

ALTER TABLE `member_sign_in_record`
  ADD COLUMN IF NOT EXISTS `sign_date` date NULL DEFAULT NULL COMMENT '业务签到日期（历史记录可为空）' AFTER `experience`;

ALTER TABLE `member_sign_in_record`
  ADD UNIQUE INDEX `uk_tenant_user_sign_date` (`tenant_id`, `user_id`, `sign_date`);

CREATE TABLE IF NOT EXISTS `app_sync_command` (
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `command_id` varchar(128) NOT NULL,
  `aggregate_type` varchar(64) NOT NULL,
  `aggregate_id` varchar(128) NULL DEFAULT NULL,
  `operation` varchar(32) NOT NULL,
  `base_version` bigint NULL DEFAULT NULL,
  `request_hash` varchar(64) NOT NULL,
  `outcome` varchar(16) NULL DEFAULT NULL,
  `result_json` text NULL,
  `error_code` varchar(64) NULL DEFAULT NULL,
  `server_version` bigint NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`tenant_id`, `user_id`, `command_id`) USING BTREE,
  INDEX `idx_sync_command_created` (`tenant_id`, `user_id`, `created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `app_sync_change` (
  `cursor` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `resource` varchar(64) NOT NULL,
  `aggregate_id` varchar(128) NOT NULL,
  `operation` varchar(16) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `payload` text NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`cursor`) USING BTREE,
  INDEX `idx_sync_change_scope` (`tenant_id`, `user_id`, `resource`, `cursor`) USING BTREE,
  INDEX `idx_sync_change_created` (`created_at`, `cursor`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `app_sync_change_retention` (
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `resource` varchar(64) NOT NULL,
  `retention_cursor` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`, `user_id`, `resource`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;
