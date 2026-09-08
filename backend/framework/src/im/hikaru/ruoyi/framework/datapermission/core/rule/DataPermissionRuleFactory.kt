package im.hikaru.ruoyi.framework.datapermission.core.rule

/**
 * [DataPermissionRule] 工厂接口
 *
 * 作为 [DataPermissionRule] 的容器，提供管理能力
 *
 * @author 芋道源码
 */
interface DataPermissionRuleFactory {

    /**
     * 获得所有数据权限规则数组
     *
     * @return 数据权限规则数组
     */
    fun getDataPermissionRules(): List<DataPermissionRule>

    /**
     * 获得指定 Mapper 的数据权限规则数组
     *
     * @param mappedStatementId 指定 Mapper 的编号
     * @return 数据权限规则数组
     */
    fun getDataPermissionRule(mappedStatementId: String?): List<DataPermissionRule>
}
