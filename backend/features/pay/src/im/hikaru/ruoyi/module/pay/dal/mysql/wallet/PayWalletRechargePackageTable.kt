package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PayWalletRechargePackageTable : BaseTable("pay_wallet_recharge_package") {
    val id = long("id").autoIncrement("pay_wallet_recharge_package_seq")
    val name = varchar("name", 255).nullable()
    val payPrice = integer("pay_price").nullable()
    val bonusPrice = integer("bonus_price").nullable()
    val status = integer("status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
