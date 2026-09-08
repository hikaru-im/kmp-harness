package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PayWalletTable : BaseTable("pay_wallet") {
    val id = long("id").autoIncrement("pay_wallet_seq")
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val balance = integer("balance").nullable()
    val freezePrice = integer("freeze_price").nullable()
    val totalExpense = integer("total_expense").nullable()
    val totalRecharge = integer("total_recharge").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
