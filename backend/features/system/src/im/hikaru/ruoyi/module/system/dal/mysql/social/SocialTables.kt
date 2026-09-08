package im.hikaru.ruoyi.module.system.dal.mysql.social

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object SocialClientTable : BaseTable("system_social_client") {
    val id = long("id").autoIncrement("system_social_client_seq")
    val name = varchar("name", 255)
    val socialType = integer("social_type")
    val userType = integer("user_type")
    val clientId = varchar("client_id", 255)
    val clientSecret = varchar("client_secret", 255)
    val agentId = varchar("agent_id", 255).nullable()
    val publicKey = varchar("public_key", 2048).nullable()
    val status = integer("status")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

object SocialUserTable : BaseTable("system_social_user") {
    val id = long("id").autoIncrement("system_social_user_seq")
    val type = integer("type")
    val openid = varchar("openid", 32)
    val token = varchar("token", 256).nullable()
    val rawTokenInfo = varchar("raw_token_info", 1024)
    val nickname = varchar("nickname", 32)
    val avatar = varchar("avatar", 255).nullable()
    val rawUserInfo = varchar("raw_user_info", 1024)
    val code = varchar("code", 256)
    val state = varchar("state", 256).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

object SocialUserBindTable : BaseTable("system_social_user_bind") {
    val id = long("id").autoIncrement("system_social_user_bind_seq")
    val userId = long("user_id")
    val userType = integer("user_type")
    val socialType = integer("social_type")
    val socialUserId = long("social_user_id")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
