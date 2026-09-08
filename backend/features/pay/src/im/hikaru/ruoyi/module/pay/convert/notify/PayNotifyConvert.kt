package im.hikaru.ruoyi.module.pay.convert.notify

import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskDetailRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyLogDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyTaskDO
import kotlinx.datetime.toJavaLocalDateTime

object PayNotifyConvert {
    fun convert(bean: PayNotifyTaskDO, appName: String? = null) = PayNotifyTaskRespVO().apply { id = bean.id; appId = bean.appId; this.appName = appName; type = bean.type?.toByte(); dataId = bean.dataId; merchantOrderId = bean.merchantOrderId; merchantRefundId = bean.merchantRefundId; merchantTransferId = bean.merchantTransferId; status = bean.status?.toByte(); nextNotifyTime = bean.nextNotifyTime?.toJavaLocalDateTime(); lastExecuteTime = bean.lastExecuteTime?.toJavaLocalDateTime(); notifyTimes = bean.notifyTimes?.toByte(); maxNotifyTimes = bean.maxNotifyTimes?.toByte(); createTime = bean.createTime?.toJavaLocalDateTime(); updateTime = bean.updateTime?.toJavaLocalDateTime() }
    fun detail(bean: PayNotifyTaskDO, logs: List<PayNotifyLogDO>, appName: String? = null) = PayNotifyTaskDetailRespVO().apply { val base = convert(bean, appName); id = base.id; appId = base.appId; this.appName = base.appName; type = base.type; dataId = base.dataId; merchantOrderId = base.merchantOrderId; merchantRefundId = base.merchantRefundId; merchantTransferId = base.merchantTransferId; status = base.status; nextNotifyTime = base.nextNotifyTime; lastExecuteTime = base.lastExecuteTime; notifyTimes = base.notifyTimes; maxNotifyTimes = base.maxNotifyTimes; createTime = base.createTime; updateTime = base.updateTime; this.logs = logs.map { PayNotifyTaskDetailRespVO.Log().apply { id = it.id; notifyTimes = it.notifyTimes?.toByte(); response = it.response; status = it.status?.toByte(); createTime = it.createTime?.toJavaLocalDateTime() } } }
}
