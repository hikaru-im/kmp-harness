package im.hikaru.ruoyi.module.sync.dal.mysql

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object AppSyncCommandTable : Table("app_sync_command") {
    val tenantId = long("tenant_id")
    val userId = long("user_id")
    val commandId = varchar("command_id", 128)
    val aggregateType = varchar("aggregate_type", 64)
    val aggregateId = varchar("aggregate_id", 128).nullable()
    val operation = varchar("operation", 32)
    val baseVersion = long("base_version").nullable()
    val requestHash = varchar("request_hash", 64)
    val outcome = varchar("outcome", 16).nullable()
    val resultJson = text("result_json").nullable()
    val errorCode = varchar("error_code", 64).nullable()
    val serverVersion = long("server_version").nullable()
    val createdAt = datetime("created_at")
    val completedAt = datetime("completed_at").nullable()

    init {
        index(false, tenantId, userId, createdAt)
    }

    override val primaryKey = PrimaryKey(tenantId, userId, commandId)
}

object AppSyncChangeTable : Table("app_sync_change") {
    val cursor = long("cursor").autoIncrement("app_sync_change_seq")
    val tenantId = long("tenant_id")
    val userId = long("user_id")
    val resource = varchar("resource", 64)
    val aggregateId = varchar("aggregate_id", 128)
    val operation = varchar("operation", 16)
    val aggregateVersion = long("aggregate_version")
    val payload = text("payload")
    val createdAt = datetime("created_at")

    init {
        index(false, tenantId, userId, resource, cursor)
        index(false, createdAt, cursor)
    }

    override val primaryKey = PrimaryKey(cursor)
}

object AppSyncChangeRetentionTable : Table("app_sync_change_retention") {
    val tenantId = long("tenant_id")
    val userId = long("user_id")
    val resource = varchar("resource", 64)
    val retentionCursor = long("retention_cursor")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(tenantId, userId, resource)
}

object AppSyncPushDeviceTable : Table("app_sync_push_device") {
    val token = varchar("token", 512)
    val tenantId = long("tenant_id")
    val userId = long("user_id")
    val platform = varchar("platform", 16)
    val updatedAt = datetime("updated_at")

    init {
        index(false, tenantId, userId, updatedAt)
    }

    override val primaryKey = PrimaryKey(token)
}
