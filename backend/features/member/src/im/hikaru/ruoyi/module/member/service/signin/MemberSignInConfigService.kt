package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO
import jakarta.validation.Valid

interface MemberSignInConfigService {
    fun createSignInConfig(@Valid createReqVO: MemberSignInConfigCreateReqVO): Long
    fun updateSignInConfig(@Valid updateReqVO: MemberSignInConfigUpdateReqVO): Unit
    fun deleteSignInConfig(id: Long): Unit
    fun getSignInConfig(id: Long): MemberSignInConfigDO?
    fun getSignInConfigList(): List<MemberSignInConfigDO>
    fun getSignInConfigList(status: Int): List<MemberSignInConfigDO>
}
