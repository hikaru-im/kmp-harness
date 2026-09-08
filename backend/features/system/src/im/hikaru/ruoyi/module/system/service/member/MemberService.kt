package im.hikaru.ruoyi.module.system.service.member

interface MemberService {
    fun getMemberUserMobile(id: Long?): String?
    fun getMemberUserEmail(id: Long?): String?
}
