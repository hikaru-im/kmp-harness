package im.hikaru.ruoyi.framework.datapermission.config

import im.hikaru.ruoyi.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor
import im.hikaru.ruoyi.framework.datapermission.core.db.DataPermissionRuleHandler
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRule
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRuleFactory
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

/**
 * 数据权限的自动配置类
 *
 * @author 芋道源码
 */
@AutoConfiguration
class YudaoDataPermissionAutoConfiguration {

    @Bean
    fun dataPermissionRuleFactory(rules: List<DataPermissionRule>): DataPermissionRuleFactory =
        DataPermissionRuleFactoryImpl(rules)

    @Bean(destroyMethod = "close")
    fun dataPermissionRuleHandler(ruleFactory: DataPermissionRuleFactory): DataPermissionRuleHandler =
        DataPermissionRuleHandler(ruleFactory)

    @Bean
    fun dataPermissionAnnotationAdvisor(): DataPermissionAnnotationAdvisor =
        DataPermissionAnnotationAdvisor()
}
