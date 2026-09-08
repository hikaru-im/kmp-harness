package im.hikaru.ruoyi.module.mp.dal.mysql.account

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MpAccountTable : BaseTable("mp_account") {
    val id = long("id").autoIncrement("mp_account_seq")
    val name = varchar("name", 100).nullable()
    val account = varchar("account", 100).nullable()
    val appId = varchar("app_id", 100).nullable()
    val url = varchar("url", 100).nullable()
    val appSecret = varchar("app_secret", 100).nullable()
    val token = varchar("token", 100).nullable()
    val aesKey = varchar("aes_key", 300).nullable()
    val qrCodeUrl = varchar("qr_code_url", 200).nullable()
    val remark = varchar("remark", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
