package im.hikaru.ruoyi.module.system.api.social

import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserUnbindReqDTO
import im.hikaru.ruoyi.module.system.service.social.SocialUserService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SocialUserApiImpl(
    private val socialUserService: SocialUserService,
) : SocialUserApi {
    override fun bindSocialUser(reqDTO: SocialUserBindReqDTO): String = socialUserService.bindSocialUser(reqDTO)

    override fun unbindSocialUser(reqDTO: SocialUserUnbindReqDTO) {
        socialUserService.unbindSocialUser(
            requireNotNull(reqDTO.userId),
            requireNotNull(reqDTO.userType),
            requireNotNull(reqDTO.socialType),
            requireNotNull(reqDTO.openid),
        )
    }

    override fun getSocialUserByUserId(userType: Int, userId: Long, socialType: Int): SocialUserRespDTO? =
        socialUserService.getSocialUserByUserId(userType, userId, socialType)

    override fun getSocialUserByCode(userType: Int, socialType: Int, code: String, state: String): SocialUserRespDTO =
        socialUserService.getSocialUserByCode(userType, socialType, code, state)
}
