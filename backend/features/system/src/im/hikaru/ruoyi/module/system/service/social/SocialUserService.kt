package im.hikaru.ruoyi.module.system.service.social

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserDO

interface SocialUserService {
    fun getSocialUserList(userId: Long, userType: Int): List<SocialUserDO>

    fun bindSocialUser(reqDTO: SocialUserBindReqDTO): String

    fun unbindSocialUser(userId: Long, userType: Int, socialType: Int, openid: String)

    fun getSocialUserByUserId(userType: Int, userId: Long, socialType: Int): SocialUserRespDTO?

    fun getSocialUserByCode(userType: Int, socialType: Int, code: String, state: String): SocialUserRespDTO

    fun getSocialUser(id: Long): SocialUserDO?

    fun getSocialUserPage(req: SocialUserPageReqVO): PageResult<SocialUserDO>
}
