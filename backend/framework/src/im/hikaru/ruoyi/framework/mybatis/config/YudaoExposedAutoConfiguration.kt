package im.hikaru.ruoyi.framework.mybatis.config

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.spring.transaction.ExposedSpringTransactionAttributeSource
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * Exposed ORM 自动配置 (替代 MyBatis-Plus YudaoMybatisAutoConfiguration)
 *
 * 迁移说明 (决策表 #4):
 *  - MyBatis-Plus: MybatisPlusAutoConfiguration + MybatisPlusInterceptor(分页) + MetaObjectHandler →
 *    Exposed: exposed-spring-boot-starter 自动注册 Database 连接 + 本模块的 BaseTable/BaseDao/分页工具
 *  - MyBatis-Plus 的 JsqlParserGlobal 缓存、IKeyGenerator、Jackson3TypeHandler → Exposed 无需 (类型安全 DSL)
 *
 * 分页: 由 [im.hikaru.ruoyi.framework.mybatis.core.mapper.paginate] 提供 (基于 Exposed limit/offset)
 *
 * @author 芋道源码
 */
@AutoConfiguration(afterName = ["org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"])
@EnableTransactionManagement(proxyTargetClass = true)
class YudaoExposedAutoConfiguration {

    @Value("\${spring.exposed.show-sql:false}")
    private var showSql: Boolean = false

    @Bean
    @ConditionalOnMissingBean(DatabaseConfig::class)
    fun databaseConfig(): DatabaseConfig = DatabaseConfig {}

    @Bean
    fun springTransactionManager(
        dataSource: DataSource,
        databaseConfig: DatabaseConfig,
    ): SpringTransactionManager = SpringTransactionManager(dataSource, databaseConfig, showSql)

    @Bean
    @Primary
    fun exposedSpringTransactionAttributeSource(): ExposedSpringTransactionAttributeSource =
        ExposedSpringTransactionAttributeSource()
}
