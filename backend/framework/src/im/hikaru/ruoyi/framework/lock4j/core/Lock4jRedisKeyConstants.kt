package im.hikaru.ruoyi.framework.lock4j.core

/**
 * Lock4j Redis Key 枚举类 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface Lock4jRedisKeyConstants {
    companion object {
        /** 分布式锁 KEY 格式 */
        const val LOCK4J = "lock4j:%s"
    }
}
