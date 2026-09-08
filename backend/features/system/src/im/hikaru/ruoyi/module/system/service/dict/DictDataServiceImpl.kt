package im.hikaru.ruoyi.module.system.service.dict

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictDataDO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO
import im.hikaru.ruoyi.module.system.dal.mysql.dict.DictDataDao
import im.hikaru.ruoyi.module.system.dal.mysql.dict.DictTypeDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_DATA_NOT_ENABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_DATA_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_DATA_VALUE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_NOT_ENABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class DictDataServiceImpl : DictDataService {
    override fun getDictDataList(status: Int?, dictType: String?): List<DictDataDO> =
        DictDataDao.selectListByStatusAndDictType(status, dictType)
            .sortedWith(compareBy<DictDataDO> { it.dictType.orEmpty() }.thenBy { it.sort ?: 0 })

    override fun getDictDataPage(pageReqVO: DictDataPageReqVO): PageResult<DictDataDO> = DictDataDao.selectPage(pageReqVO)

    override fun getDictData(id: Long): DictDataDO? = DictDataDao.selectById(id)

    override fun createDictData(createReqVO: DictDataSaveReqVO): Long {
        validateDictTypeExists(requireNotNull(createReqVO.dictType))
        validateDictDataValueUnique(null, requireNotNull(createReqVO.dictType), requireNotNull(createReqVO.value))
        return DictDataDao.insert(createReqVO.toEntity())
    }

    override fun updateDictData(updateReqVO: DictDataSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateDictDataExists(id)
        validateDictTypeExists(requireNotNull(updateReqVO.dictType))
        validateDictDataValueUnique(id, requireNotNull(updateReqVO.dictType), requireNotNull(updateReqVO.value))
        DictDataDao.updateById(updateReqVO.toEntity())
    }

    override fun deleteDictData(id: Long) {
        validateDictDataExists(id)
        DictDataDao.deleteById(id)
    }

    override fun deleteDictDataList(ids: List<Long>) {
        DictDataDao.deleteByIds(ids)
    }

    override fun getDictDataCountByDictType(dictType: String): Long = DictDataDao.selectCountByDictType(dictType)

    override fun validateDictDataList(dictType: String, values: Collection<String>) {
        if (values.isEmpty()) return
        val byValue = DictDataDao.selectByDictTypeAndValues(dictType, values).associateBy { it.value }
        values.forEach { value ->
            val data = byValue[value] ?: throw exception(DICT_DATA_NOT_EXISTS)
            if (!CommonStatusEnum.isEnable(data.status)) throw exception(DICT_DATA_NOT_ENABLE, data.label)
        }
    }

    override fun getDictData(dictType: String, value: String): DictDataDO? =
        DictDataDao.selectByDictTypeAndValue(dictType, value)

    override fun parseDictData(dictType: String, label: String): DictDataDO? =
        DictDataDao.selectByDictTypeAndLabel(dictType, label)

    override fun getDictDataListByDictType(dictType: String): List<DictDataDO> =
        DictDataDao.selectListByDictType(dictType)

    private fun validateDictDataValueUnique(id: Long?, dictType: String, value: String) {
        val existing = DictDataDao.selectByDictTypeAndValue(dictType, value) ?: return
        if (id == null || existing.id != id) throw exception(DICT_DATA_VALUE_DUPLICATE)
    }

    private fun validateDictDataExists(id: Long) {
        if (DictDataDao.selectById(id) == null) throw exception(DICT_DATA_NOT_EXISTS)
    }

    private fun validateDictTypeExists(type: String) {
        val dictType: DictTypeDO = DictTypeDao.selectByType(type) ?: throw exception(DICT_TYPE_NOT_EXISTS)
        if (!CommonStatusEnum.isEnable(dictType.status)) throw exception(DICT_TYPE_NOT_ENABLE)
    }

    private fun DictDataSaveReqVO.toEntity() = DictDataDO().apply {
        id = this@toEntity.id
        sort = this@toEntity.sort
        label = this@toEntity.label
        value = this@toEntity.value
        dictType = this@toEntity.dictType
        status = this@toEntity.status
        colorType = this@toEntity.colorType
        cssClass = this@toEntity.cssClass
        remark = this@toEntity.remark
    }
}
