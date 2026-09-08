package im.hikaru.ruoyi.module.pay.dal.mysql.demo

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean
import org.jetbrains.exposed.v1.datetime.datetime

object PayDemoOrderTable : BaseTable("pay_demo_order") {
    val id = long("id").autoIncrement("pay_demo_order_seq")
    val userId = long("user_id").nullable()
    val spuId = long("spu_id").nullable()
    val spuName = varchar("spu_name", 255).nullable()
    val price = integer("price").nullable()
    val payStatus = smallIntBoolean("pay_status").nullable()
    val payOrderId = long("pay_order_id").nullable()
    val payTime = datetime("pay_time").nullable()
    val payChannelCode = varchar("pay_channel_code", 128).nullable()
    val payRefundId = long("pay_refund_id").nullable()
    val refundPrice = integer("refund_price").nullable()
    val refundTime = datetime("refund_time").nullable()
    val transferChannelPackageInfo = varchar("transfer_channel_package_info", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
