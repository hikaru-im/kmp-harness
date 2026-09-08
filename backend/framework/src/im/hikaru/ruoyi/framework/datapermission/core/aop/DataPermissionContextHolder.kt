package im.hikaru.ruoyi.framework.datapermission.core.aop

import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import com.alibaba.ttl.TransmittableThreadLocal
import java.util.LinkedList

/**
 * [DataPermission] 注解的 Context 上下文
 *
 * 迁移说明：
 *  - 仅将 Java 改写为 Kotlin，TTL 保留 (决策表 #14 评估后保留)
 *  - `peekLast`/`addLast`/`removeLast`/`isEmpty` 均为 JDK LinkedList 原生方法
 *
 * @author 芋道源码
 */
object DataPermissionContextHolder {

    /**
     * 使用 List 的原因，可能存在方法的嵌套调用
     */
    private val DATA_PERMISSIONS: ThreadLocal<LinkedList<DataPermission>> =
        object : TransmittableThreadLocal<LinkedList<DataPermission>>() {
            override fun initialValue(): LinkedList<DataPermission> = LinkedList()
        }

    /**
     * 获得当前的 DataPermission 注解
     *
     * @return DataPermission 注解
     */
    @JvmStatic
    fun get(): DataPermission? = DATA_PERMISSIONS.get().peekLast()

    /**
     * 入栈 DataPermission 注解
     *
     * @param dataPermission DataPermission 注解
     */
    @JvmStatic
    fun add(dataPermission: DataPermission) {
        DATA_PERMISSIONS.get().addLast(dataPermission)
    }

    /**
     * 出栈 DataPermission 注解
     *
     * @return DataPermission 注解
     */
    @JvmStatic
    fun remove(): DataPermission {
        val dataPermission = DATA_PERMISSIONS.get().removeLast()
        // 无元素时，清空 ThreadLocal
        if (DATA_PERMISSIONS.get().isEmpty()) {
            DATA_PERMISSIONS.remove()
        }
        return dataPermission
    }

    /**
     * 获得所有 DataPermission
     *
     * @return DataPermission 队列
     */
    @JvmStatic
    fun getAll(): List<DataPermission> = DATA_PERMISSIONS.get()

    /**
     * 清空上下文
     *
     * 目前仅仅用于单测
     */
    @JvmStatic
    fun clear() {
        DATA_PERMISSIONS.remove()
    }
}
