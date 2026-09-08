package im.hikaru.ruoyi.module.mp.service.account

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import jakarta.validation.Valid

interface MpAccountService {
    fun initLocalCache(): Unit
    fun createAccount(@Valid createReqVO: MpAccountCreateReqVO): Long
    fun updateAccount(@Valid updateReqVO: MpAccountUpdateReqVO): Unit
    fun deleteAccount(id: Long): Unit
    fun getAccount(id: Long): MpAccountDO?
    fun getRequiredAccount(id: Long): MpAccountDO = requireNotNull(getAccount(id)) { "Mp account($id) does not exist" }
    fun getAccountFromCache(appId: String): MpAccountDO?
    fun getAccountPage(pageReqVO: MpAccountPageReqVO): PageResult<MpAccountDO>
    fun getAccountList(): List<MpAccountDO>
    fun generateAccountQrCode(id: Long): Unit
    fun clearAccountQuota(id: Long): Unit
}
