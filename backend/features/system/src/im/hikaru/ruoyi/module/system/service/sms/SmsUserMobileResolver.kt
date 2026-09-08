package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import org.springframework.stereotype.Component

fun interface SmsUserMobileResolver {
    fun resolve(userId: Long?, userType: Int?): String?
}

@Component
class DefaultSmsUserMobileResolver(
    private val adminUserService: AdminUserService,
    private val memberService: MemberService,
) : SmsUserMobileResolver {
    override fun resolve(userId: Long?, userType: Int?): String? = when {
        userId == null || userType == null -> null
        userType == UserTypeEnum.ADMIN.value -> adminUserService.getUser(userId)?.mobile
        userType == UserTypeEnum.MEMBER.value -> memberService.getMemberUserMobile(userId)
        else -> null
    }
}
