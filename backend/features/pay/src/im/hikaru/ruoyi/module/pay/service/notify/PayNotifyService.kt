package im.hikaru.ruoyi.module.pay.service.notify

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyLogDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyTaskDO

interface PayNotifyService {
    fun createPayNotifyTask(type: Int, dataId: Long): Unit
    fun executeNotify(): Int
    fun getNotifyTask(id: Long): PayNotifyTaskDO
    fun getNotifyTaskPage(pageReqVO: PayNotifyTaskPageReqVO): PageResult<PayNotifyTaskDO>
    fun getNotifyLogList(taskId: Long): List<PayNotifyLogDO>
}
