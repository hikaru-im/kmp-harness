package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class SocialClientSaveReqVO {
    var id: Long? = null

    @field:NotBlank(message = "Application name must not be blank")
    var name: String? = null

    @field:NotNull(message = "Social type must not be null")
    @field:InEnum(SocialTypeEnum::class)
    var socialType: Int? = null

    @field:NotNull(message = "User type must not be null")
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null

    @field:NotBlank(message = "Client id must not be blank")
    var clientId: String? = null

    @field:NotBlank(message = "Client secret must not be blank")
    var clientSecret: String? = null

    var agentId: String? = null
    var publicKey: String? = null

    @field:NotNull(message = "Status must not be null")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null

    @get:AssertTrue(message = "agentId must be provided for WeCom")
    @get:JsonIgnore
    val agentIdValid: Boolean
        get() = socialType != SocialTypeEnum.WECHAT_ENTERPRISE.type || !agentId.isNullOrBlank()

    @get:AssertTrue(message = "publicKey must be provided for Alipay")
    @get:JsonIgnore
    val publicKeyValid: Boolean
        get() = socialType != SocialTypeEnum.ALIPAY_MINI_PROGRAM.type || !publicKey.isNullOrBlank()
}
