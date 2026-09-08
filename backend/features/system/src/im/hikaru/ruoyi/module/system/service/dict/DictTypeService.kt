package im.hikaru.ruoyi.module.system.service.dict

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO

interface DictTypeService {
    fun createDictType(createReqVO: DictTypeSaveReqVO): Long
    fun updateDictType(updateReqVO: DictTypeSaveReqVO)
    fun deleteDictType(id: Long)
    fun deleteDictTypeList(ids: List<Long>)
    fun getDictTypePage(pageReqVO: DictTypePageReqVO): PageResult<DictTypeDO>
    fun getDictType(id: Long): DictTypeDO?
    fun getDictType(type: String): DictTypeDO?
    fun getDictTypeList(): List<DictTypeDO>
}
