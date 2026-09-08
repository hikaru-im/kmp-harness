package im.hikaru.ruoyi.module.infra.service.redis

import im.hikaru.ruoyi.module.infra.controller.admin.redis.vo.RedisMonitorRespVO
import org.springframework.data.redis.connection.RedisServerCommands
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.Properties

/**
 * Redis 监控 Service (迁移自 Java 的 RedisConvert + RedisController, 去 Hutool)
 *
 * 迁移说明：Hutool StrUtil.subAfter/subBetween → Kotlin stdlib (substringAfterLast/substringAfter)
 *
 * @author 芋道源码
 */
@Service
class RedisService(
    private val stringRedisTemplate: StringRedisTemplate,
) {

    /**
     * 获得 Redis 监控信息
     *
     * @return Redis 监控信息
     */
    fun getRedisMonitorInfo(): RedisMonitorRespVO {
        // 获得 Redis 统计信息
        val info: Properties? = stringRedisTemplate.execute(
            RedisCallback<Properties> { it.serverCommands().info() }
        )
        val dbSize: Long? = stringRedisTemplate.execute(RedisServerCommands::dbSize)
        val commandStats: Properties? = stringRedisTemplate.execute(
            RedisCallback<Properties> { it.serverCommands().info("commandstats") }
        )
        // 拼接结果返回
        return build(info, dbSize, commandStats)
    }

    private fun build(info: Properties?, dbSize: Long?, commandStats: Properties?): RedisMonitorRespVO {
        val commandStatList = ArrayList<RedisMonitorRespVO.CommandStat>(commandStats?.size ?: 0)
        commandStats?.forEach { key, value ->
            val keyStr = key.toString()
            val valueStr = value.toString()
            commandStatList.add(
                RedisMonitorRespVO.CommandStat(
                    command = keyStr.substringAfter("cmdstat_"),
                    calls = parseBetween(valueStr, "calls=", ",")?.toLong() ?: 0L,
                    usec = parseBetween(valueStr, "usec=", ",")?.toLong() ?: 0L,
                )
            )
        }
        return RedisMonitorRespVO(
            info = info,
            dbSize = dbSize,
            commandStats = commandStatList,
        )
    }

    /**
     * 解析字符串中 `prefix....suffix` 之间的内容，等价于 Hutool StrUtil.subBetween
     */
    private fun parseBetween(text: String, prefix: String, suffix: String): String? {
        val start = text.indexOf(prefix)
        if (start < 0) return null
        val valueStart = start + prefix.length
        val end = text.indexOf(suffix, valueStart)
        if (end < 0) return null
        return text.substring(valueStart, end)
    }
}
