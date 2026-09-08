CREATE TABLE IF NOT EXISTS `app_sync_push_device` (
  `token` varchar(512) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `platform` varchar(16) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token`) USING BTREE,
  INDEX `idx_sync_push_device_scope` (`tenant_id`, `user_id`, `updated_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;
