package im.hikaru.ruoyi.module.pay.framework.pay.core.client

interface PayClientFactory {
    fun getPayClient(channelId: Long): PayClient<*>?
    fun createOrUpdatePayClient(channelId: Long, channelCode: String, config: PayClientConfig?): PayClient<*>
}
