package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl

import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientFactory
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.wallet.WalletPayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin.WxPayClient
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletTransactionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PayClientFactoryImpl(
    private val walletService: ObjectProvider<PayWalletService>,
    private val transactionService: ObjectProvider<PayWalletTransactionService>,
    private val orderService: ObjectProvider<PayOrderService>,
    private val refundService: ObjectProvider<PayRefundService>,
    private val transferService: ObjectProvider<PayTransferService>,
) : PayClientFactory {
    private val clients = ConcurrentHashMap<Long, PayClient<*>>()
    override fun getPayClient(channelId: Long): PayClient<*>? = clients[channelId]
    override fun createOrUpdatePayClient(channelId: Long, channelCode: String, config: PayClientConfig?): PayClient<*> {
        val actualConfig = config ?: NonePayClientConfig()
        return clients.compute(channelId) { _, old ->
            if (channelCode == PayChannelEnum.WALLET.code) {
                old as? WalletPayClient ?: WalletPayClient(
                    channelId,
                    walletService = { walletService.getObject() },
                    transactionService = { transactionService.getObject() },
                    orderService = { orderService.getObject() },
                    refundService = { refundService.getObject() },
                    transferService = { transferService.getObject() },
                )
            } else if (PayChannelEnum.isAlipay(channelCode)) {
                val alipayConfig = actualConfig as? AlipayPayClientConfig
                    ?: throw IllegalArgumentException("Alipay channel requires AlipayPayClientConfig")
                (old as? AlipayPayClient)?.also { it.refresh(alipayConfig) }
                    ?: AlipayPayClient(channelId, channelCode, alipayConfig)
            } else if (PayChannelEnum.isWeixin(channelCode)) {
                val wechatConfig = actualConfig as? im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin.WxPayClientConfig
                    ?: throw IllegalArgumentException("WeChat channel requires WxPayClientConfig")
                (old as? WxPayClient)?.also { it.refresh(wechatConfig) }
                    ?: WxPayClient(channelId, channelCode, wechatConfig)
            } else if (channelCode == PayChannelEnum.MOCK.code) {
                (old as? SimplePayClient)?.also { it.refresh(actualConfig) }
                    ?: SimplePayClient(channelId, channelCode, actualConfig)
            } else {
                throw IllegalArgumentException("Unsupported payment channel: $channelCode")
            }
        }!!
    }
}
