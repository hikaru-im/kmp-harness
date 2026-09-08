package im.hikaru.ruoyi.module.member.dal.mysql.address

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean

object MemberAddressTable : BaseTable("member_address") {
    val id = long("id").autoIncrement("member_address_seq")
    val userId = long("user_id").nullable()
    val name = varchar("name", 255).nullable()
    val mobile = varchar("mobile", 32).nullable()
    val areaId = long("area_id").nullable()
    val detailAddress = varchar("detail_address", 255).nullable()
    val defaultStatus = smallIntBoolean("default_status").nullable()
    val version = long("version").default(1L)
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
