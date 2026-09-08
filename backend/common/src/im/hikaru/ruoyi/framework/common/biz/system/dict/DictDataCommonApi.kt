package im.hikaru.ruoyi.framework.common.biz.system.dict

import im.hikaru.ruoyi.framework.common.biz.system.dict.dto.DictDataRespDTO

/**
 * 字典数据 API 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface DictDataCommonApi {

    /**
     * 获得指定字典类型的字典数据列表
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    fun getDictDataList(dictType: String): List<DictDataRespDTO>
}
