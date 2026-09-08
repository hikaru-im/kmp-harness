package im.hikaru.ruoyi.framework.datapermission.core.aop

import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import org.aopalliance.aop.Advice
import org.springframework.aop.Pointcut
import org.springframework.aop.support.AbstractPointcutAdvisor
import org.springframework.aop.support.ComposablePointcut
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut

/**
 * [DataPermission] 注解的 Advisor 实现类
 *
 * 迁移说明：Lombok `@Getter`/`@EqualsAndHashCode(callSuper = true)` → 直接使用公开 `val`；
 * Kotlin 的类相等性默认基于对象标识，由于本 Advisor 作为单例 Bean 注册，足够满足场景。
 *
 * @author 芋道源码
 */
class DataPermissionAnnotationAdvisor : AbstractPointcutAdvisor() {

    private val advice: Advice = DataPermissionAnnotationInterceptor()

    private val pointcut: Pointcut = buildPointcut()

    override fun getAdvice(): Advice = advice

    override fun getPointcut(): Pointcut = pointcut

    private fun buildPointcut(): Pointcut {
        val classPointcut: Pointcut = AnnotationMatchingPointcut(DataPermission::class.java, true)
        val methodPointcut: Pointcut = AnnotationMatchingPointcut(null, DataPermission::class.java, true)
        return ComposablePointcut(classPointcut).union(methodPointcut)
    }
}
