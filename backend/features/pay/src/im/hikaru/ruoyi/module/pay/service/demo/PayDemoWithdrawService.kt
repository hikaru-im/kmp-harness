package im.hikaru.ruoyi.module.pay.service.demo

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw.PayDemoWithdrawCreateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoWithdrawDO
import jakarta.validation.Valid

interface PayDemoWithdrawService {
    fun createDemoWithdraw(@Valid createReqVO: PayDemoWithdrawCreateReqVO): Long
    fun transferDemoWithdraw(id: Long, userId: Long): Long
    fun getDemoWithdrawPage(pageVO: PageParam): PageResult<PayDemoWithdrawDO>
    fun updateDemoWithdrawTransferred(id: Long, payTransferId: Long): Unit
}
