package im.hikaru.ruoyi.module.member.service.signin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.springframework.stereotype.Component
import java.time.ZoneId

fun interface MemberSignInDateProvider {
    fun currentDate(): LocalDate
}

@Component
class SystemMemberSignInDateProvider : MemberSignInDateProvider {
    override fun currentDate(): LocalDate = java.time.LocalDate.now(BUSINESS_TIME_ZONE).toKotlinLocalDate()

    private companion object {
        val BUSINESS_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
