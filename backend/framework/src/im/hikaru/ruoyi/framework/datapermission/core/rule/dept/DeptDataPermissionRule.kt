package im.hikaru.ruoyi.framework.datapermission.core.rule.dept

import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRule
import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.slf4j.LoggerFactory

/**
 * 基于部门的 [DataPermissionRule] 数据权限规则实现
 *
 * 注意，使用 DeptDataPermissionRule 时，需要保证表中有 dept_id 部门编号的字段，可自定义。
 *
 * 实际业务场景下，会存在一个经典的问题？当用户修改部门时，冗余的 dept_id 是否需要修改？
 * 1. 一般情况下，dept_id 不进行修改，则会导致用户看不到之前的数据。【yudao-server 采用该方案】
 * 2. 部分情况下，希望该用户还是能看到之前的数据，则有两种方式解决：【需要你改造该 DeptDataPermissionRule 的实现代码】
 *  1）编写洗数据的脚本，将 dept_id 修改成新部门的编号；【建议】 最终过滤条件是 WHERE dept_id = ?
 *  2）洗数据的话，可能涉及的数据量较大，也可以采用 user_id 进行过滤的方式，此时需要获取到 dept_id 对应的所有 user_id 用户编号；
 *      最终过滤条件是 WHERE user_id IN (?, ?, ? ...)
 *  3）想要保证原 dept_id 和 user_id 都可以看的到，此时使用 dept_id 和 user_id 一起过滤；
 *      最终过滤条件是 WHERE dept_id = ? OR user_id IN (?, ?, ? ...)
 *
 * JSqlParser 字符串重写已替换为结构化 Exposed [Op]，配置仍按表名映射部门列和用户列。
 *
 * @author 芋道源码
 */
class DeptDataPermissionRule(
    private val permissionApi: PermissionCommonApi,
) : DataPermissionRule {

    /**
     * LoginUser 的 Context 缓存 Key
     */
    private val contextKey: String = Companion.CONTEXT_KEY

    /** 基于部门的表字段配置 (key: 表名, value: 字段名) */
    private val deptColumns: MutableMap<String, String> = HashMap()

    /** 基于用户的表字段配置 (key: 表名, value: 字段名) */
    private val userColumns: MutableMap<String, String> = HashMap()

    /**
     * 所有表名，是 [deptColumns] 和 [userColumns] 的合集
     */
    private val tableNames: MutableSet<String> = HashSet()

    override fun getTableNames(): Set<String> = tableNames

    override fun getExpression(table: Table): Op<Boolean>? {
        val tableName = table.policyTableName()
        // 只有有登陆用户的情况下，才进行数据权限的处理
        val loginUser: LoginUser = SecurityFrameworkUtils.getLoginUser() ?: return null
        // 只有管理员类型的用户，才进行数据权限的处理
        if (loginUser.userType != UserTypeEnum.ADMIN.value) {
            return null
        }

        // 获得数据权限
        var deptDataPermission = loginUser.getContext(contextKey, DeptDataPermissionRespDTO::class.java)
        // 从上下文中拿不到，则调用逻辑进行获取
        if (deptDataPermission == null) {
            deptDataPermission = permissionApi.getDeptDataPermission(loginUser.id!!)
            if (deptDataPermission == null) {
                log.error("[getExpression][LoginUser({}) 获取数据权限为 null]", JsonUtils.toJsonString(loginUser))
                throw NullPointerException(
                    String.format("LoginUser(%d) Table(%s) 未返回数据权限", loginUser.id, tableName),
                )
            }
            // 添加到上下文中，避免重复计算
            loginUser.setContext(contextKey, deptDataPermission)
        }

        // 情况一，如果是 ALL 可查看全部，则无需拼接条件
        if (deptDataPermission.all == true) {
            return null
        }

        // 情况二，即不能查看部门，又不能查看自己，则说明 100% 无权限
        if (deptDataPermission.deptIds.isNullOrEmpty() && deptDataPermission.self != true) {
            return Op.FALSE
        }

        // 情况三，拼接 Dept 和 User 的条件，最后组合
        val deptExpression = buildDeptExpression(table, deptDataPermission.deptIds)
        val userExpression = buildUserExpression(table, deptDataPermission.self == true, loginUser.id!!)
        if (deptExpression == null && userExpression == null) {
            // TODO 芋艿：获得不到条件的时候，暂时不抛出异常，而是不返回数据
            log.warn(
                "[getExpression][LoginUser({}) Table({}/{}) DeptDataPermission({}) 构建的条件为空]",
                JsonUtils.toJsonString(loginUser), tableName, null, JsonUtils.toJsonString(deptDataPermission),
            )
            return Op.FALSE
        }
        if (deptExpression == null) {
            return userExpression
        }
        if (userExpression == null) {
            return deptExpression
        }
        // 目前，如果有指定部门 + 可查看自己，采用 OR 条件。即，WHERE (dept_id IN ? OR user_id = ?)
        return listOf(deptExpression, userExpression).compoundOr()
    }

    private fun buildDeptExpression(table: Table, deptIds: Set<Long>?): Op<Boolean>? {
        // 如果不存在配置，则无需作为条件
        val columnName = deptColumns[table.policyTableName()] ?: return null
        // 如果为空，则无条件
        if (deptIds.isNullOrEmpty()) {
            return null
        }
        return table.longColumn(columnName) inList deptIds
    }

    private fun buildUserExpression(table: Table, self: Boolean, userId: Long): Op<Boolean>? {
        // 如果不查看自己，则无需作为条件
        if (!self) {
            return null
        }
        val columnName = userColumns[table.policyTableName()] ?: return null
        return table.longColumn(columnName) eq userId
    }

    // ==================== 添加配置 ====================

    fun addDeptColumn(table: BaseTable) {
        addDeptColumn(table, DEPT_COLUMN_NAME)
    }

    fun addDeptColumn(table: BaseTable, columnName: String) {
        addDeptColumn(table.tableName, columnName)
    }

    fun addDeptColumn(tableName: String, columnName: String) {
        deptColumns[tableName] = columnName
        tableNames.add(tableName)
    }

    fun addUserColumn(table: BaseTable) {
        addUserColumn(table, USER_COLUMN_NAME)
    }

    fun addUserColumn(table: BaseTable, columnName: String) {
        addUserColumn(table.tableName, columnName)
    }

    fun addUserColumn(tableName: String, columnName: String) {
        userColumns[tableName] = columnName
        tableNames.add(tableName)
    }

    companion object {
        /** LoginUser 的 Context 缓存 Key */
        const val CONTEXT_KEY: String = "DeptDataPermissionRule"

        private const val DEPT_COLUMN_NAME = "dept_id"
        private const val USER_COLUMN_NAME = "user_id"

        private val log = LoggerFactory.getLogger(DeptDataPermissionRule::class.java)
    }
}

@Suppress("UNCHECKED_CAST")
private fun Table.longColumn(columnName: String): Column<Long> =
    columns.firstOrNull { it.name.equals(columnName, ignoreCase = true) } as? Column<Long>
        ?: error("Table $tableName does not contain configured Long column $columnName")

private fun Table.policyTableName(): String = (this as? Alias<*>)?.delegate?.tableName ?: tableName
