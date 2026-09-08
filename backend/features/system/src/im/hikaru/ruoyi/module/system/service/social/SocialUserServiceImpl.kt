package im.hikaru.ruoyi.module.system.service.social

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserBindDO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserDO
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialUserBindDao
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialUserDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_USER_NOT_FOUND
import me.zhyd.oauth.model.AuthUser
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SocialUserServiceImpl(
    private val socialClientService: SocialAuthClient,
) : SocialUserService {

    override fun getSocialUserList(userId: Long, userType: Int): List<SocialUserDO> {
        val binds = SocialUserBindDao.selectListByUserIdAndUserType(userId, userType)
        return SocialUserDao.selectByIds(binds.mapNotNull { it.socialUserId }.toSet())
    }

    override fun bindSocialUser(reqDTO: SocialUserBindReqDTO): String = transaction {
        val userId = requireNotNull(reqDTO.userId)
        val userType = requireNotNull(reqDTO.userType)
        val socialUser = authSocialUser(
            requireNotNull(reqDTO.socialType),
            userType,
            requireNotNull(reqDTO.code),
            requireNotNull(reqDTO.state),
        )
        val socialUserId = requireNotNull(socialUser.id)
        val socialType = requireNotNull(socialUser.type)
        SocialUserBindDao.deleteByUserTypeAndSocialUserId(userType, socialUserId)
        SocialUserBindDao.deleteByUserTypeAndUserIdAndSocialType(userType, userId, socialType)
        SocialUserBindDao.insert(SocialUserBindDO().apply {
            this.userId = userId
            this.userType = userType
            this.socialUserId = socialUserId
            this.socialType = socialType
        })
        requireNotNull(socialUser.openid)
    }

    override fun unbindSocialUser(userId: Long, userType: Int, socialType: Int, openid: String) {
        val socialUser = SocialUserDao.selectByTypeAndOpenid(socialType, openid)
            ?: throw ServiceExceptionUtil.exception(SOCIAL_USER_NOT_FOUND)
        SocialUserBindDao.deleteByUserTypeAndUserIdAndSocialType(userType, userId, requireNotNull(socialUser.type))
    }

    override fun getSocialUserByUserId(userType: Int, userId: Long, socialType: Int): SocialUserRespDTO? {
        val bind = SocialUserBindDao.selectByUserIdAndUserTypeAndSocialType(userId, userType, socialType) ?: return null
        val socialUser = SocialUserDao.selectById(requireNotNull(bind.socialUserId))
            ?: throw IllegalStateException("Bound social user does not exist")
        return socialUser.toResponse(bind.userId)
    }

    override fun getSocialUserByCode(userType: Int, socialType: Int, code: String, state: String): SocialUserRespDTO {
        val socialUser = authSocialUser(socialType, userType, code, state)
        val bind = SocialUserBindDao.selectByUserTypeAndSocialUserId(userType, requireNotNull(socialUser.id))
        return socialUser.toResponse(bind?.userId)
    }

    override fun getSocialUser(id: Long): SocialUserDO? = SocialUserDao.selectById(id)

    override fun getSocialUserPage(req: SocialUserPageReqVO): PageResult<SocialUserDO> = SocialUserDao.selectPage(req)

    internal fun authSocialUser(socialType: Int, userType: Int, code: String, state: String): SocialUserDO {
        SocialUserDao.selectByTypeAndCodeAndState(socialType, code, state)?.let { return it }
        val authUser = socialClientService.getAuthUser(socialType, userType, code, state)
        val openid = requireNotNull(authUser.uuid) { "Social provider returned no openid" }
        val socialUser = SocialUserDao.selectByTypeAndOpenid(socialType, openid) ?: SocialUserDO()
        socialUser.applyAuthUser(socialType, code, state, authUser)
        if (socialUser.id == null) SocialUserDao.insert(socialUser) else SocialUserDao.updateById(socialUser)
        return socialUser
    }

    private fun SocialUserDO.applyAuthUser(socialType: Int, code: String, state: String, authUser: AuthUser) {
        type = socialType
        this.code = code
        this.state = state
        openid = authUser.uuid
        token = authUser.token?.accessToken
        rawTokenInfo = JsonUtils.toJsonString(authUser.token)
        nickname = authUser.nickname ?: authUser.username ?: authUser.uuid
        avatar = authUser.avatar
        rawUserInfo = JsonUtils.toJsonString(authUser.rawUserInfo)
    }

    private fun SocialUserDO.toResponse(userId: Long?) = SocialUserRespDTO(openid, nickname, avatar, userId)
}
