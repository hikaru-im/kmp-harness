SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for app_sync_command
-- ----------------------------
DROP TABLE IF EXISTS `app_sync_command`;
CREATE TABLE `app_sync_command` (
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `command_id` varchar(128) NOT NULL COMMENT '客户端生成的稳定命令编号',
  `aggregate_type` varchar(64) NOT NULL COMMENT '聚合类型',
  `aggregate_id` varchar(128) NULL DEFAULT NULL COMMENT '聚合编号',
  `operation` varchar(32) NOT NULL COMMENT '命令操作',
  `base_version` bigint NULL DEFAULT NULL COMMENT '客户端编辑基准版本',
  `request_hash` varchar(64) NOT NULL COMMENT '规范化请求哈希',
  `outcome` varchar(16) NULL DEFAULT NULL COMMENT '确定结果',
  `result_json` text NULL COMMENT '首次确定结果 JSON',
  `error_code` varchar(64) NULL DEFAULT NULL COMMENT '稳定错误码',
  `server_version` bigint NULL DEFAULT NULL COMMENT '服务端业务版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  `completed_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`tenant_id`, `user_id`, `command_id`) USING BTREE,
  INDEX `idx_sync_command_created` (`tenant_id` ASC, `user_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = 'App 同步命令幂等记录';

-- ----------------------------
-- Table structure for app_sync_change
-- ----------------------------
DROP TABLE IF EXISTS `app_sync_change`;
CREATE TABLE `app_sync_change` (
  `cursor` bigint NOT NULL AUTO_INCREMENT COMMENT '全局单调游标',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `resource` varchar(64) NOT NULL COMMENT '资源类型',
  `aggregate_id` varchar(128) NOT NULL COMMENT '聚合编号',
  `operation` varchar(16) NOT NULL COMMENT 'UPSERT 或 DELETE',
  `aggregate_version` bigint NOT NULL COMMENT '资源业务版本',
  `payload` text NOT NULL COMMENT '资源快照或删除墓碑 JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`cursor`) USING BTREE,
  INDEX `idx_sync_change_scope` (`tenant_id` ASC, `user_id` ASC, `resource` ASC, `cursor` ASC) USING BTREE,
  INDEX `idx_sync_change_created` (`created_at` ASC, `cursor` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = 'App 增量同步变更';

-- ----------------------------
-- Table structure for app_sync_change_retention
-- ----------------------------
DROP TABLE IF EXISTS `app_sync_change_retention`;
CREATE TABLE `app_sync_change_retention` (
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `resource` varchar(64) NOT NULL COMMENT '资源类型',
  `retention_cursor` bigint NOT NULL COMMENT '已清理变更的最大游标',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '水位更新时间',
  PRIMARY KEY (`tenant_id`, `user_id`, `resource`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = 'App 同步变更保留水位';

-- ----------------------------
-- Table structure for app_sync_push_device
-- ----------------------------
DROP TABLE IF EXISTS `app_sync_push_device`;
CREATE TABLE `app_sync_push_device` (
  `token` varchar(512) NOT NULL COMMENT 'FCM 设备令牌',
  `tenant_id` bigint NOT NULL COMMENT '当前租户编号',
  `user_id` bigint NOT NULL COMMENT '当前用户编号',
  `platform` varchar(16) NOT NULL COMMENT 'ANDROID 或 IOS',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近注册时间',
  PRIMARY KEY (`token`) USING BTREE,
  INDEX `idx_sync_push_device_scope` (`tenant_id` ASC, `user_id` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = 'App 同步 Push 设备';

SET FOREIGN_KEY_CHECKS = 1;
