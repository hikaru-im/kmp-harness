package im.hikaru.ruoyi.module.system.service.dict

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictDataDO

interface DictDataService {
    fun createDictData(createReqVO: DictDataSaveReqVO): Long
    fun updateDictData(updateReqVO: DictDataSaveReqVO)
    fun deleteDictData(id: Long)
    fun deleteDictDataList(ids: List<Long>)
    fun getDictDataList(status: Int?, dictType: String?): List<DictDataDO>
    fun getDictDataPage(pageReqVO: DictDataPageReqVO): PageResult<DictDataDO>
    fun getDictData(id: Long): DictDataDO?
    fun getDictDataCountByDictType(dictType: String): Long
    fun validateDictDataList(dictType: String, values: Collection<String>)
    fun getDictData(dictType: String, value: String): DictDataDO?
    fun parseDictData(dictType: String, label: String): DictDataDO?
    fun getDictDataListByDictType(dictType: String): List<DictDataDO>
}
