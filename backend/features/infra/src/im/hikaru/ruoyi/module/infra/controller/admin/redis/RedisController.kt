package im.hikaru.ruoyi.module.infra.controller.admin.redis

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.infra.controller.admin.redis.vo.RedisMonitorRespVO
import im.hikaru.ruoyi.module.infra.service.redis.RedisService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "管理后台 - Redis 监控")
@RestController
@RequestMapping("/infra/redis")
class RedisController(
    private val redisService: RedisService,
) {

    @GetMapping("/get-monitor-info")
    @Operation(summary = "获得 Redis 监控信息")
    @PreAuthorize("@ss.hasPermission('infra:redis:get-monitor-info')")
    fun getRedisMonitorInfo(): CommonResult<RedisMonitorRespVO> =
        CommonResult.success(redisService.getRedisMonitorInfo())
}
