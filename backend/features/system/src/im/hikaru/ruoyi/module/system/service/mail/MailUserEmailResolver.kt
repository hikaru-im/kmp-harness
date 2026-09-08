package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import org.springframework.stereotype.Component

fun interface MailUserEmailResolver {
    fun resolve(userId: Long?, userType: Int?): String?
}

@Component
class DefaultMailUserEmailResolver(
    private val adminUserService: AdminUserService,
    private val memberService: MemberService,
) : MailUserEmailResolver {
    override fun resolve(userId: Long?, userType: Int?): String? = when {
        userId == null || userType == null -> null
        userType == UserTypeEnum.ADMIN.value -> adminUserService.getUser(userId)?.email
        userType == UserTypeEnum.MEMBER.value -> memberService.getMemberUserEmail(userId)
        else -> null
    }
}
