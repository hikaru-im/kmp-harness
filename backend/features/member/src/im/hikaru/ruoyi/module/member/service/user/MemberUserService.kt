package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.*
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import jakarta.validation.Valid

interface MemberUserService {
    fun getUserByMobile(mobile: String): MemberUserDO?
    fun getUserListByNickname(nickname: String): List<MemberUserDO>
    fun createUserIfAbsent(@Mobile mobile: String, registerIp: String, terminal: Int): MemberUserDO
    fun createUser(nickname: String?, avtar: String?, registerIp: String, terminal: Int): MemberUserDO
    fun updateUserLogin(id: Long, loginIp: String): Unit
    fun getUser(id: Long): MemberUserDO?
    fun getUserList(ids: Collection<Long>): List<MemberUserDO>
    fun updateUser(userId: Long, reqVO: AppMemberUserUpdateReqVO): Unit
    fun updateUserMobile(userId: Long, reqVO: AppMemberUserUpdateMobileReqVO): Unit
    fun updateUserMobileByWeixin(userId: Long, reqVO: AppMemberUserUpdateMobileByWeixinReqVO): Unit
    fun updateUserPassword(userId: Long, reqVO: AppMemberUserUpdatePasswordReqVO): Unit
    fun resetUserPassword(reqVO: AppMemberUserResetPasswordReqVO): Unit
    fun isPasswordMatch(rawPassword: String, encodedPassword: String): Boolean
    fun updateUser(@Valid updateReqVO: MemberUserUpdateReqVO): Unit
    fun getUserPage(pageReqVO: MemberUserPageReqVO): PageResult<MemberUserDO>
    fun updateUserLevel(id: Long, levelId: Long, experience: Int): Unit
    fun getUserCountByGroupId(groupId: Long): Long
    fun getUserCountByLevelId(levelId: Long): Long
    fun getUserCountByTagId(tagId: Long): Long
    fun updateUserPoint(userId: Long, point: Int): Boolean
}
