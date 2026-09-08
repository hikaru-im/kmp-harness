package im.hikaru.ruoyi.module.system.service.dict

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO
import im.hikaru.ruoyi.module.system.dal.mysql.dict.DictTypeDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_HAS_CHILDREN
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DICT_TYPE_TYPE_DUPLICATE
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import kotlin.time.Clock

@Service
@Validated
class DictTypeServiceImpl(
    private val dictDataService: DictDataService,
) : DictTypeService {
    override fun getDictTypePage(pageReqVO: DictTypePageReqVO): PageResult<DictTypeDO> = DictTypeDao.selectPage(pageReqVO)
    override fun getDictType(id: Long): DictTypeDO? = DictTypeDao.selectById(id)
    override fun getDictType(type: String): DictTypeDO? = DictTypeDao.selectByType(type)

    override fun createDictType(createReqVO: DictTypeSaveReqVO): Long {
        validateNameUnique(null, requireNotNull(createReqVO.name))
        validateTypeUnique(null, createReqVO.type)
        return DictTypeDao.insert(createReqVO.toEntity())
    }

    override fun updateDictType(updateReqVO: DictTypeSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateExists(id)
        validateNameUnique(id, requireNotNull(updateReqVO.name))
        validateTypeUnique(id, updateReqVO.type)
        DictTypeDao.updateById(updateReqVO.toEntity())
    }

    override fun deleteDictType(id: Long) {
        val dictType = validateExists(id)
        if (dictDataService.getDictDataCountByDictType(requireNotNull(dictType.type)) > 0) {
            throw exception(DICT_TYPE_HAS_CHILDREN)
        }
        DictTypeDao.updateToDelete(id, now())
    }

    override fun deleteDictTypeList(ids: List<Long>) {
        DictTypeDao.selectByIds(ids).forEach { type ->
            if (dictDataService.getDictDataCountByDictType(requireNotNull(type.type)) > 0) {
                throw exception(DICT_TYPE_HAS_CHILDREN)
            }
        }
        val current = now()
        ids.forEach { DictTypeDao.updateToDelete(it, current) }
    }

    override fun getDictTypeList(): List<DictTypeDO> = DictTypeDao.selectList()

    private fun validateExists(id: Long): DictTypeDO =
        DictTypeDao.selectById(id) ?: throw exception(DICT_TYPE_NOT_EXISTS)

    private fun validateNameUnique(id: Long?, name: String) {
        val existing = DictTypeDao.selectByName(name) ?: return
        if (id == null || existing.id != id) throw exception(DICT_TYPE_NAME_DUPLICATE)
    }

    private fun validateTypeUnique(id: Long?, type: String?) {
        if (type.isNullOrBlank()) return
        val existing = DictTypeDao.selectByType(type) ?: return
        if (id == null || existing.id != id) throw exception(DICT_TYPE_TYPE_DUPLICATE)
    }

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun DictTypeSaveReqVO.toEntity() = DictTypeDO().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        type = this@toEntity.type
        status = this@toEntity.status
        remark = this@toEntity.remark
    }
}
