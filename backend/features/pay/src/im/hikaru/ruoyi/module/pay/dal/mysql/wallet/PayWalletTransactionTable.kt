package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PayWalletTransactionTable : BaseTable("pay_wallet_transaction") {
    val id = long("id").autoIncrement("pay_wallet_transaction_seq")
    val no = varchar("no", 128).nullable()
    val walletId = long("wallet_id").nullable()
    val bizType = integer("biz_type").nullable()
    val bizId = varchar("biz_id", 128).nullable()
    val title = varchar("title", 255).nullable()
    val price = integer("price").nullable()
    val balance = integer("balance").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
