package im.hikaru.ruoyi.framework.datapermission.core.rule

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table

/**
 * 数据权限规则接口
 *
 * 通过实现接口，自定义数据规则。例如说，
 *
 * @author 芋道源码
 */
interface DataPermissionRule {

    /**
     * 返回需要生效的表名数组
     *
     * 为什么需要该方法？Data Permission 数组基于 SQL 重写，通过 Where 返回只有权限的数据。
     *
     * 如果需要基于实体名获得表名，可调用各业务表 (Exposed `Table`) 的 `.tableName`。
     *
     * @return 表名数组
     */
    fun getTableNames(): Set<String>

    /**
     * 为指定 Exposed 表生成额外的过滤条件。
     *
     * @return 结构化 Exposed 条件；为 null 表示无附加条件
     */
    fun getExpression(table: Table): Op<Boolean>?
}
