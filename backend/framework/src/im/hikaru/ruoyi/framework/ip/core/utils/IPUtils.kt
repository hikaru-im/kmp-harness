package im.hikaru.ruoyi.framework.ip.core.utils

import im.hikaru.ruoyi.framework.ip.core.Area
import org.lionsoul.ip2region.xdb.Searcher
import org.slf4j.LoggerFactory

/**
 * IP 工具类 (迁移自 Java, 去 Lombok @SneakyThrows/Hutool ResourceUtil)
 *
 * IP 数据源来自 ip2region.xdb 精简版，基于 https://gitee.com/zhijiantianya/ip2region 项目
 *
 * 迁移说明：
 *  - Hutool ResourceUtil.readBytes → ClassLoader.getResourceAsStream + readBytes
 *  - Lombok @SneakyThrows → 显式 try/catch 转 RuntimeException
 *
 * @author wanglhup
 */
object IPUtils {
    private val log = LoggerFactory.getLogger(IPUtils::class.java)

    /**
     * IP 查询器，启动加载到内存中
     */
    private val searcher: Searcher = run { initSearcher() }

    private fun initSearcher(): Searcher {
        try {
            val now = System.currentTimeMillis()
            val classLoader = Thread.currentThread().contextClassLoader ?: IPUtils::class.java.classLoader
            val bytes = classLoader.getResourceAsStream("ip2region.xdb").use { it.readBytes() }
            log.info("启动加载 IPUtils 成功，耗时 ({}) 毫秒", System.currentTimeMillis() - now)
            return Searcher.newWithBuffer(bytes)
        } catch (e: Exception) {
            throw RuntimeException("IPUtils 初始化失败", e)
        }
    }

    /**
     * 查询 IP 对应的地区编号
     *
     * @param ip IP 地址，格式为 127.0.0.1
     * @return 地区id
     */
    @JvmStatic
    fun getAreaId(ip: String): Int =
        try {
            searcher.search(ip.trim()).toInt()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    /**
     * 查询 IP 对应的地区编号
     *
     * @param ip IP 地址的时间戳
     * @return 地区编号
     */
    @JvmStatic
    fun getAreaId(ip: Long): Int =
        try {
            searcher.search(ip).toInt()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    /**
     * 查询 IP 对应的地区
     *
     * @param ip IP 地址，格式为 127.0.0.1
     * @return 地区
     */
    @JvmStatic
    fun getArea(ip: String): Area? = AreaUtils.getArea(getAreaId(ip))

    /**
     * 查询 IP 对应的地区
     *
     * @param ip IP 地址的时间戳
     * @return 地区
     */
    @JvmStatic
    fun getArea(ip: Long): Area? = AreaUtils.getArea(getAreaId(ip))
}
