package im.hikaru.ruoyi.module.mp.service.user

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import me.chanjar.weixin.mp.bean.result.WxMpUser

interface MpUserService {
    fun getUser(id: Long): MpUserDO?
    fun getUser(appId: String, openId: String): MpUserDO?
    fun getRequiredUser(id: Long): MpUserDO = requireNotNull(getUser(id)) { "Mp user($id) does not exist" }
    fun getUserList(ids: Collection<Long>): List<MpUserDO>
    fun getUserPage(pageReqVO: MpUserPageReqVO): PageResult<MpUserDO>
    fun saveUser(appId: String, wxMpUser: WxMpUser): MpUserDO
    fun syncUser(accountId: Long): Unit
    fun updateUserUnsubscribe(appId: String, openId: String): Unit
    fun updateUser(updateReqVO: MpUserUpdateReqVO): Unit
}
