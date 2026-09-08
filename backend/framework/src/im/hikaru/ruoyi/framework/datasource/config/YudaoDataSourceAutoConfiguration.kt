package im.hikaru.ruoyi.framework.datasource.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * 数据库配置类 (Exposed + HikariCP 版)
 *
 * 迁移说明 (决策表 #8):
 *  - Druid 连接池 + DruidAdRemoveFilter + DruidStatProperties → HikariCP (Spring Boot 默认)
 *  - dynamic-datasource 多数据源 → 暂用单数据源 (Spring Boot 原生 DataSource)
 *  - Exposed 的 Database 连接由 exposed-spring-boot-starter 自动配置
 *
 * @author 芋道源码
 */
@AutoConfiguration
@EnableTransactionManagement(proxyTargetClass = true) // 启动事务管理
@ConditionalOnClass(DataSource::class)
class YudaoDataSourceAutoConfiguration
