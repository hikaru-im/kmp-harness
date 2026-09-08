package im.hikaru.ruoyi.framework.ip.core

import im.hikaru.ruoyi.framework.ip.core.enums.AreaTypeEnum
import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonManagedReference

/**
 * 区域节点，包括国家、省份、城市、地区等信息 (迁移自 Java, 去 Lombok)
 *
 * 数据可见 resources/area.csv 文件
 *
 * @author 芋道源码
 */
class Area(
    var id: Int? = null,
    var name: String? = null,
    /** 类型 枚举 [AreaTypeEnum] */
    var type: Int? = null,
    @JsonManagedReference
    var parent: Area? = null,
    @JsonBackReference
    var children: MutableList<Area> = ArrayList(),
) {
    override fun toString(): String = "Area(id=$id, name=$name, type=$type)" // 排除 parent 避免循环

    companion object {
        /** 编号 - 全球，即根目录 */
        const val ID_GLOBAL = 0
        /** 编号 - 中国 */
        const val ID_CHINA = 1
    }
}
