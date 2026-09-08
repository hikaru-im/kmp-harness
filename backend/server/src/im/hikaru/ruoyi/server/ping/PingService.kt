package im.hikaru.ruoyi.server.ping

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import org.springframework.stereotype.Service

/**
 * 验证 allOpen(spring preset) 生效:
 * Kotlin 默认 class 为 final,Spring preset 会将 @Service 标注的类变为 open,
 * 使 CGLIB 代理可正常工作。
 */
@Service
class PingService {
    fun ping(): CommonResult<String> = CommonResult.success("pong")
}
