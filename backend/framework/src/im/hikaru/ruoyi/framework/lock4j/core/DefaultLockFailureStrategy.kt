package im.hikaru.ruoyi.framework.lock4j.core

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import com.baomidou.lock.LockFailureStrategy
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * 自定义获取锁失败策略，抛出 [ServiceException] 异常 (迁移自 Java, 去 Lombok)
 */
class DefaultLockFailureStrategy : LockFailureStrategy {

    override fun onLockFailure(key: String, method: Method, arguments: Array<Any>) {
        log.debug("[onLockFailure][线程:{} 获取锁失败，key:{} 获取失败:{} ]", Thread.currentThread().name, key, arguments)
        throw ServiceException(GlobalErrorCodeConstants.LOCKED)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultLockFailureStrategy::class.java)
    }
}
