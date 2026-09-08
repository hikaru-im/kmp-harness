package im.hikaru.ruoyi.framework.common.biz.system.dict.dto

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import java.io.Serializable

/**
 * 字典数据 Response DTO (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class DictDataRespDTO : Serializable {
    /** 字典标签 */
    var label: String? = null

    /** 字典值 */
    var value: String? = null

    /** 字典类型 */
    var dictType: String? = null

    /** 状态 枚举 [CommonStatusEnum] */
    var status: Int? = null
}
