package im.hikaru.ruoyi.framework.security.config

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.security.core.filter.TokenAuthenticationFilter
import im.hikaru.ruoyi.framework.security.core.handler.AccessDeniedHandlerImpl
import im.hikaru.ruoyi.framework.security.core.handler.AuthenticationEntryPointImpl
import im.hikaru.ruoyi.framework.security.core.context.TransmittableThreadLocalSecurityContextHolderStrategy
import im.hikaru.ruoyi.framework.security.core.service.SecurityFrameworkService
import im.hikaru.ruoyi.framework.security.core.service.SecurityFrameworkServiceImpl
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Spring Security 自动配置类，主要用于相关组件的配置 (迁移自 Java, 去 Lombok)
 *
 * 迁移说明：
 *  - 去除 TokenAuthenticationFilter Bean：依赖 OAuth2TokenCommonApi (common 业务接口)，
 *    待 yudao-module-system 迁移时由业务侧注入。
 *  - 保留 TTL SecurityContextHolderStrategy (TransmittableThreadLocal, 决策表 #14 评估后保留)
 *
 * @author 芋道源码
 */
@AutoConfiguration
@AutoConfigureOrder(-1) // 目的：先于 Spring Security 自动配置
@EnableConfigurationProperties(SecurityProperties::class)
class YudaoSecurityAutoConfiguration {

    /** 认证失败处理类 Bean */
    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint = AuthenticationEntryPointImpl()

    /** 权限不够处理器 Bean */
    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler = AccessDeniedHandlerImpl()

    /**
     * Spring Security 加密器
     *
     * 考虑到安全性，这里采用 BCryptPasswordEncoder 加密器
     */
    @Bean
    fun passwordEncoder(securityProperties: SecurityProperties): PasswordEncoder =
        BCryptPasswordEncoder(securityProperties.passwordEncoderLength)

    @Bean
    fun authenticationTokenFilter(
        securityProperties: SecurityProperties,
        globalExceptionHandler: GlobalExceptionHandler,
        oauth2TokenApi: OAuth2TokenCommonApi,
    ): TokenAuthenticationFilter = TokenAuthenticationFilter(securityProperties, globalExceptionHandler, oauth2TokenApi)

    @Bean("ss") // 使用 Spring Security 的缩写，方便使用
    fun securityFrameworkService(permissionApi: PermissionCommonApi): SecurityFrameworkService =
        SecurityFrameworkServiceImpl(permissionApi)

    /**
     * 声明调用 [SecurityContextHolder.setStrategyName] 方法，
     * 设置使用基于 TransmittableThreadLocal 的 Security 上下文策略 (跨线程传递)。
     *
     */
    @Bean
    fun securityContextHolderMethodInvokingFactoryBean(): MethodInvokingFactoryBean =
        MethodInvokingFactoryBean().apply {
            setTargetClass(SecurityContextHolder::class.java)
            setTargetMethod("setStrategyName")
            setArguments(TransmittableThreadLocalSecurityContextHolderStrategy::class.java.name)
        }
}
