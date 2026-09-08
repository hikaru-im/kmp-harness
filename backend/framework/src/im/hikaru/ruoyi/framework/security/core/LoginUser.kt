package im.hikaru.ruoyi.framework.security.core

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDateTime

/**
 * 登录用户信息 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：Hutool MapUtil.get → Kotlin map 取值 + 强转
 *
 * @author 芋道源码
 */
class LoginUser {

    var id: Long? = null

    /**
     * 用户类型 ([im.hikaru.ruoyi.framework.common.enums.UserTypeEnum])
     */
    var userType: Int? = null

    /** 额外的用户信息 */
    var info: Map<String, String>? = null

    /** 租户编号 */
    var tenantId: Long? = null

    /** 授权范围 */
    var scopes: List<String>? = null

    /** 过期时间 */
    var expiresTime: LocalDateTime? = null

    // ========== 上下文 ==========
    /**
     * 上下文字段，不进行持久化
     *
     * 1. 用于基于 LoginUser 维度的临时缓存
     */
    @JsonIgnore
    var context: MutableMap<String, Any>? = null

    /** 访问的租户编号 */
    var visitTenantId: Long? = null

    fun setContext(key: String, value: Any?) {
        if (context == null) {
            context = HashMap()
        }
        context!![key] = value as Any
    }

    fun <T> getContext(key: String, type: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return context?.get(key) as? T
    }

    companion object {
        const val INFO_KEY_NICKNAME = "nickname"
        const val INFO_KEY_DEPT_ID = "deptId"
    }
}
