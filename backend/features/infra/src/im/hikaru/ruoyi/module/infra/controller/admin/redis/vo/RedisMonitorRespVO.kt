package im.hikaru.ruoyi.module.infra.controller.admin.redis.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Properties

@Schema(description = "管理后台 - Redis 监控信息 Response VO")
data class RedisMonitorRespVO(
    @Schema(description = "Redis info 指令结果,具体字段，查看 Redis 文档", requiredMode = Schema.RequiredMode.REQUIRED)
    var info: Properties? = null,

    @Schema(description = "Redis key 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var dbSize: Long? = null,

    @Schema(description = "CommandStat 数组", requiredMode = Schema.RequiredMode.REQUIRED)
    var commandStats: List<CommandStat> = emptyList(),
) {

    @Schema(description = "Redis 命令统计结果")
    data class CommandStat(
        @Schema(description = "Redis 命令", requiredMode = Schema.RequiredMode.REQUIRED, example = "get")
        var command: String? = null,

        @Schema(description = "调用次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        var calls: Long? = null,

        @Schema(description = "消耗 CPU 秒数", requiredMode = Schema.RequiredMode.REQUIRED, example = "666")
        var usec: Long? = null,
    )
}
