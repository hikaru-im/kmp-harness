package im.hikaru.ruoyi.module.member.dal.mysql.config

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean

object MemberConfigTable : BaseTable("member_config") {
    val id = long("id").autoIncrement("member_config_seq")
    val pointTradeDeductEnable = smallIntBoolean("point_trade_deduct_enable").nullable()
    val pointTradeDeductUnitPrice = integer("point_trade_deduct_unit_price").nullable()
    val pointTradeDeductMaxPrice = integer("point_trade_deduct_max_price").nullable()
    val pointTradeGivePoint = integer("point_trade_give_point").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
