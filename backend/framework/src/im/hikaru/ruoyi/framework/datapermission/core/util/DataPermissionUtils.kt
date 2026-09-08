package im.hikaru.ruoyi.framework.datapermission.core.util

import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.framework.datapermission.core.aop.DataPermissionContextHolder
import java.util.concurrent.Callable

/**
 * 数据权限 Util
 *
 * 迁移说明：
 *  - Lombok `@SneakyThrows` → Kotlin 显式 `throws`/重新抛出 (见 [executeIgnore] Callable 重载)
 *  - Java 静态字段/方法 → Kotlin `object`
 *  - 占位用的 `@DataPermission(enable = false)` 注解，仍通过反射从 [disableMarker] 方法上获取
 *    (注解无法直接实例化，沿用原 Java 的反射获取方式)
 *
 * @author 芋道源码
 */
object DataPermissionUtils {

    @DataPermission(enable = false)
    private fun disableMarker() {
        // 仅用于承载 @DataPermission(enable = false) 注解，方法体无意义
    }

    @Volatile
    private var dataPermissionDisable: DataPermission? = null

    private fun getDisableDataPermission(): DataPermission {
        dataPermissionDisable?.let { return it }
        val annotation = DataPermissionUtils::class.java
            .getDeclaredMethod("disableMarker")
            .getAnnotation(DataPermission::class.java)
        dataPermissionDisable = annotation
        return annotation
    }

    /**
     * 忽略数据权限，执行对应的逻辑
     *
     * @param runnable 逻辑
     */
    @JvmStatic
    fun executeIgnore(runnable: Runnable) {
        addDisableDataPermission()
        try {
            // 执行 runnable
            runnable.run()
        } finally {
            removeDataPermission()
        }
    }

    /**
     * 忽略数据权限，执行对应的逻辑
     *
     * @param callable 逻辑
     * @return 执行结果
     */
    @JvmStatic
    fun <T> executeIgnore(callable: Callable<T>): T {
        addDisableDataPermission()
        return try {
            // 执行 callable
            callable.call()
        } catch (e: Exception) {
            throw e
        } catch (e: Throwable) {
            throw RuntimeException(e)
        } finally {
            removeDataPermission()
        }
    }

    /**
     * 添加忽略数据权限
     */
    @JvmStatic
    fun addDisableDataPermission() {
        val dataPermission = getDisableDataPermission()
        DataPermissionContextHolder.add(dataPermission)
    }

    @JvmStatic
    fun removeDataPermission() {
        DataPermissionContextHolder.remove()
    }
}
