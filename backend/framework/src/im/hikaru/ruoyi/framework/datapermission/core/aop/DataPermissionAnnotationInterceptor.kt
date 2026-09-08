package im.hikaru.ruoyi.framework.datapermission.core.aop

import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.core.MethodClassKey
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [DataPermission] 注解的拦截器
 *
 * 1. 在执行方法前，将 @DataPermission 注解入栈
 * 2. 在执行方法后，将 @DataPermission 注解出栈
 *
 * 迁移说明：
 *  - Lombok `@Getter` → 暴露为公开 `val`
 *  - 原实现使用 `DATA_PERMISSION_NULL` 哨兵对象区分 “未命中注解” 与 “缓存未填充”，依赖 `ConcurrentHashMap` 不允许 null 值；
 *    Kotlin 版直接使用 `ConcurrentHashMap<MethodClassKey, DataPermission?>`，缓存中存在 null 值即表示 “方法/类上无 @DataPermission 注解”，
 *    因此不再需要哨兵对象与本类上的占位 @DataPermission 注解。
 *
 * @author 芋道源码
 */
class DataPermissionAnnotationInterceptor : MethodInterceptor {

    /**
     * 方法 -> @DataPermission 注解 的缓存。
     * [Optional.empty] 表示该方法/类上未声明 @DataPermission 注解。
     */
    val dataPermissionCache: MutableMap<MethodClassKey, Optional<DataPermission>> = ConcurrentHashMap()

    override fun invoke(methodInvocation: MethodInvocation): Any? {
        // 入栈
        val dataPermission = findAnnotation(methodInvocation)
        if (dataPermission != null) {
            DataPermissionContextHolder.add(dataPermission)
        }
        return try {
            // 执行逻辑
            methodInvocation.proceed()
        } finally {
            // 出栈
            if (dataPermission != null) {
                DataPermissionContextHolder.remove()
            }
        }
    }

    private fun findAnnotation(methodInvocation: MethodInvocation): DataPermission? {
        // 1. 从缓存中获取
        val method: Method = methodInvocation.method
        val targetObject = methodInvocation.`this`
        val clazz = targetObject?.javaClass ?: method.declaringClass
        val methodClassKey = MethodClassKey(method, clazz)
        dataPermissionCache[methodClassKey]?.let { return it.orElse(null) }

        // 2.1 从方法中获取
        var dataPermission = AnnotationUtils.findAnnotation(method, DataPermission::class.java)
        // 2.2 从类上获取
        if (dataPermission == null) {
            dataPermission = AnnotationUtils.findAnnotation(clazz, DataPermission::class.java)
        }
        // 2.3 添加到缓存中
        dataPermissionCache[methodClassKey] = Optional.ofNullable(dataPermission)
        return dataPermission
    }
}
