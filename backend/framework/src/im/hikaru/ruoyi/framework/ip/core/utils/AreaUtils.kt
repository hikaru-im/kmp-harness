package im.hikaru.ruoyi.framework.ip.core.utils

import im.hikaru.ruoyi.framework.common.util.collection.CollectionUtils
import im.hikaru.ruoyi.framework.common.util.obj.ObjectUtils
import im.hikaru.ruoyi.framework.ip.core.Area
import im.hikaru.ruoyi.framework.ip.core.enums.AreaTypeEnum
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.function.Predicate

/**
 * 区域工具类 (迁移自 Java, 去 Hutool CsvUtil/ResourceUtil/Assert)
 *
 * 迁移说明：
 *  - Hutool ResourceUtil.getUtf8Reader → ClassLoader.getResourceAsStream + InputStreamReader
 *  - Hutool CsvUtil.getReader().read().getRows() → JDK BufferedReader readLine + split(",")
 *  - Hutool Assert.isTrue → require
 *
 * @author 芋道源码
 */
object AreaUtils {
    private val log = LoggerFactory.getLogger(AreaUtils::class.java)

    /**
     * Area 内存缓存，提升访问速度
     */
    private val areas: MutableMap<Int, Area> = HashMap()

    init {
        init()
    }

    /**
     * 初始化
     */
    private fun init() {
        try {
            val now = System.currentTimeMillis()
            areas[Area.ID_GLOBAL] = Area(Area.ID_GLOBAL, "全球", 0, null, ArrayList())

            // 从 csv 中加载数据 (替代 Hutool ResourceUtil + CsvUtil)
            val rows = readCsvRows("area.csv")
            for (row in rows) {
                val area = Area(row[0].toInt(), row[1], row[2].toInt(), null, ArrayList())
                areas[area.id!!] = area
            }

            // 构建父子关系：因为 Area 中没有 parentId 字段,所以需要重复读取
            for (row in rows) {
                val area = areas[row[0].toInt()]!! // 自己
                val parent = areas[row[3].toInt()] // 父
                require(area !== parent) { "${area.name}:父子节点相同" }
                area.parent = parent
                parent!!.children.add(area)
            }
            log.info("启动加载 AreaUtils 成功，耗时 ({}) 毫秒", System.currentTimeMillis() - now)
        } catch (e: Exception) {
            throw RuntimeException("AreaUtils 初始化失败", e)
        }
    }

    /**
     * 读取 CSV 文件为行列表 (跳过 header)
     */
    private fun readCsvRows(resourceName: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val classLoader = Thread.currentThread().contextClassLoader ?: AreaUtils::class.java.classLoader
        classLoader.getResourceAsStream(resourceName).use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var firstLine = true
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (firstLine) { firstLine = false; continue } // 跳过 header
                    val fields = line!!.split(",").map { it.trim() }
                    if (fields.size >= 4) rows.add(fields)
                }
            }
        }
        return rows
    }

    /**
     * 获得指定编号对应的区域
     */
    @JvmStatic
    fun getArea(id: Int?): Area? = if (id != null) areas[id] else null

    /**
     * 获得指定区域对应的编号
     *
     * @param pathStr 区域路径，例如说：河南省/石家庄市/新华区
     * @return 区域
     */
    @JvmStatic
    fun parseArea(pathStr: String): Area? {
        val paths = pathStr.split("/")
        var area: Area? = null
        for (path in paths) {
            area = if (area == null) {
                CollectionUtils.findFirst(areas.values, Predicate { it.name == path })
            } else {
                CollectionUtils.findFirst(area.children, Predicate { it.name == path })
            }
        }
        return area
    }

    /**
     * 获取所有节点的全路径名称如：河南省/石家庄市/新华区
     */
    @JvmStatic
    fun getAreaNodePathList(areaList: List<Area>): List<String> {
        val paths = ArrayList<String>()
        areaList.forEach { getAreaNodePathList(it, "", paths) }
        return paths
    }

    private fun getAreaNodePathList(node: Area, path: String, paths: MutableList<String>) {
        // 构建当前节点的路径
        val currentPath = if (path.isEmpty()) node.name!! else "$path/${node.name}"
        paths.add(currentPath)
        // 递归遍历子节点
        for (child in node.children) {
            getAreaNodePathList(child, currentPath, paths)
        }
    }

    /**
     * 格式化区域
     */
    @JvmStatic
    fun format(id: Int?): String? = format(id, " ")

    /**
     * 格式化区域
     *
     * 当区域在中国时，默认不显示中国
     */
    @JvmStatic
    fun format(id: Int?, separator: String): String? {
        // 获得区域
        var area = areas[id] ?: return null
        // 格式化
        val sb = StringBuilder()
        var i = 0
        while (i < AreaTypeEnum.entries.size) { // 避免死循环
            sb.insert(0, area.name)
            // "递归"父节点
            area = area.parent ?: break
            if (ObjectUtils.equalsAny(area.id, Area.ID_GLOBAL, Area.ID_CHINA)) { // 跳过父节点为中国的情况
                break
            }
            sb.insert(0, separator)
            i++
        }
        return sb.toString()
    }

    /**
     * 获取指定类型的区域列表
     */
    @JvmStatic
    fun <T> getByType(type: AreaTypeEnum, func: Function<Area, T>): List<T> =
        CollectionUtils.convertList(areas.values, func) { type.type == it.type }

    /**
     * 根据区域编号、上级区域类型，获取上级区域编号
     */
    @JvmStatic
    fun getParentIdByType(id: Int?, type: AreaTypeEnum): Int? {
        var currentId = id ?: return null
        for (i in 0 until Byte.MAX_VALUE) {
            val area = AreaUtils.getArea(currentId) ?: return null
            // 情况一：匹配到，返回它
            if (type.type == area.type) {
                return area.id
            }
            // 情况二：找到根节点，返回空
            val parent = area.parent
            if (parent == null || parent.id == null) {
                return null
            }
            // 其它：继续向上查找
            currentId = parent.id!!
        }
        return null
    }
}
