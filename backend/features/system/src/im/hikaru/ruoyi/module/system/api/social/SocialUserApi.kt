package im.hikaru.ruoyi.module.system.api.social

import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserUnbindReqDTO
import jakarta.validation.Valid

interface SocialUserApi {
    fun bindSocialUser(@Valid reqDTO: SocialUserBindReqDTO): String
    fun unbindSocialUser(@Valid reqDTO: SocialUserUnbindReqDTO)
    fun getSocialUserByUserId(userType: Int, userId: Long, socialType: Int): SocialUserRespDTO?
    fun getSocialUserByCode(userType: Int, socialType: Int, code: String, state: String): SocialUserRespDTO?
}
