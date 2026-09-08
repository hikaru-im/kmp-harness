package im.hikaru.ruoyi.module.system.service.member

import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MemberServiceImpl(
    private val beanFactory: ListableBeanFactory,
    @param:Value("\${yudao.info.base-package:im.hikaru.ruoyi}") private val basePackage: String,
) : MemberService {
    @Volatile
    private var memberUserApi: Any? = null

    override fun getMemberUserMobile(id: Long?): String? = getMemberUser(id)?.readProperty("getMobile")
    override fun getMemberUserEmail(id: Long?): String? = getMemberUser(id)?.readProperty("getEmail")

    private fun getMemberUser(id: Long?): Any? {
        if (id == null) return null
        return memberApi().javaClass.getMethod("getUser", Long::class.javaObjectType).invoke(memberApi(), id)
    }

    private fun memberApi(): Any {
        memberUserApi?.let { return it }
        synchronized(this) {
            memberUserApi?.let { return it }
            val apiClass = Class.forName("$basePackage.module.member.api.user.MemberUserApi")
            return beanFactory.getBean(apiClass).also { memberUserApi = it }
        }
    }

    private fun Any.readProperty(getter: String): String? = javaClass.getMethod(getter).invoke(this) as? String
}
