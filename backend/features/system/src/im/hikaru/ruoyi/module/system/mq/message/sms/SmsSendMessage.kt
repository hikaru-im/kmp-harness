package im.hikaru.ruoyi.module.system.mq.message.sms

import im.hikaru.ruoyi.framework.common.core.KeyValue

data class SmsSendMessage(
    val logId: Long,
    val mobile: String,
    val channelId: Long,
    val apiTemplateId: String,
    val templateParams: List<KeyValue<String, Any?>>,
)
