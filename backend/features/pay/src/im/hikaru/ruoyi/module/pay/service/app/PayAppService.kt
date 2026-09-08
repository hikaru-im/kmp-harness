package im.hikaru.ruoyi.module.pay.service.app

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppUpdateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import jakarta.validation.Valid

interface PayAppService {
    fun createApp(@Valid createReqVO: PayAppCreateReqVO): Long
    fun updateApp(@Valid updateReqVO: PayAppUpdateReqVO): Unit
    fun updateAppStatus(id: Long, status: Int): Unit
    fun deleteApp(id: Long): Unit
    fun getApp(id: Long): PayAppDO
    fun getAppList(ids: Collection<Long>): List<PayAppDO>
    fun getAppList(): List<PayAppDO>
    fun getAppPage(pageReqVO: PayAppPageReqVO): PageResult<PayAppDO>
    fun getAppMap(ids: Collection<Long>): Map<Long, PayAppDO> = emptyMap()
    fun validPayApp(id: Long): PayAppDO
    fun validPayApp(appKey: String): PayAppDO
}
