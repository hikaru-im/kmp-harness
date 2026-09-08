package im.hikaru.ruoyi.module.system.framework.operatelog.core

import im.hikaru.ruoyi.framework.ip.core.utils.AreaUtils
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.dict.DictDataService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import com.mzt.logapi.service.IParseFunction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AreaParseFunction : IParseFunction {
    override fun executeBefore() = true
    override fun functionName() = NAME
    override fun apply(value: Any?): String = value.asInt()?.let(AreaUtils::format).orEmpty()

    companion object {
        const val NAME = "getArea"
    }
}

@Component
class PostParseFunction(private val postService: PostService) : IParseFunction {
    override fun functionName() = NAME
    override fun apply(value: Any?): String {
        val id = value.asLong() ?: return ""
        return postService.getPost(id)?.name ?: run {
            log.warn("Post {} was not found while formatting an operation log", id)
            ""
        }
    }

    companion object {
        const val NAME = "getPostById"
        private val log = LoggerFactory.getLogger(PostParseFunction::class.java)
    }
}

@Component
class SexParseFunction(private val dictDataService: DictDataService) : IParseFunction {
    override fun executeBefore() = true
    override fun functionName() = NAME
    override fun apply(value: Any?): String = value.asText()?.let {
        dictDataService.getDictData(DictTypeConstants.USER_SEX, it)?.label
    }.orEmpty()

    companion object {
        const val NAME = "getSex"
    }
}

@Component
class BooleanParseFunction(private val dictDataService: DictDataService) : IParseFunction {
    override fun executeBefore() = true
    override fun functionName() = NAME
    override fun apply(value: Any?): String = value.asText()?.let {
        dictDataService.getDictData(im.hikaru.ruoyi.module.infra.enums.DictTypeConstants.BOOLEAN_STRING, it)?.label
    }.orEmpty()

    companion object {
        const val NAME = "getBoolean"
    }
}

@Component
class AdminUserParseFunction(private val adminUserService: AdminUserService) : IParseFunction {
    override fun functionName() = NAME
    override fun apply(value: Any?): String {
        val id = value.asLong() ?: return ""
        val user = adminUserService.getUser(id) ?: run {
            log.warn("Admin user {} was not found while formatting an operation log", id)
            return ""
        }
        val nickname = user.nickname.orEmpty()
        return user.mobile?.takeIf { it.isNotBlank() }?.let { "$nickname($it)" } ?: nickname
    }

    companion object {
        const val NAME = "getAdminUserById"
        private val log = LoggerFactory.getLogger(AdminUserParseFunction::class.java)
    }
}

@Component
class DeptParseFunction(private val deptService: DeptService) : IParseFunction {
    override fun functionName() = NAME
    override fun apply(value: Any?): String {
        val id = value.asLong() ?: return ""
        return deptService.getDept(id)?.name ?: run {
            log.warn("Department {} was not found while formatting an operation log", id)
            ""
        }
    }

    companion object {
        const val NAME = "getDeptById"
        private val log = LoggerFactory.getLogger(DeptParseFunction::class.java)
    }
}

private fun Any?.asText(): String? = this?.toString()?.takeIf { it.isNotBlank() }
private fun Any?.asLong(): Long? = asText()?.toLongOrNull()
private fun Any?.asInt(): Int? = asText()?.toIntOrNull()
