package im.hikaru.ruoyi.module.infra.service.demo.demo02

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategorySaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo02.Demo02CategoryDO
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo02.Demo02CategoryDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_EXITS_CHILDREN
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_NAME_DUPLICATE
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_PARENT_ERROR
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_PARENT_IS_CHILD
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO02_CATEGORY_PARENT_NOT_EXITS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class Demo02CategoryServiceImpl : Demo02CategoryService {
    override fun createDemo02Category(createReqVO: Demo02CategorySaveReqVO): Long {
        val parentId = requireNotNull(createReqVO.parentId)
        validateParentDemo02Category(null, parentId)
        validateNameUnique(null, parentId, requireNotNull(createReqVO.name))
        return Demo02CategoryDao.insert(requireNotNull(BeanUtils.toBean(createReqVO, Demo02CategoryDO::class.java)))
    }

    override fun updateDemo02Category(updateReqVO: Demo02CategorySaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        val parentId = requireNotNull(updateReqVO.parentId)
        validateExists(id)
        validateParentDemo02Category(id, parentId)
        validateNameUnique(id, parentId, requireNotNull(updateReqVO.name))
        Demo02CategoryDao.updateById(requireNotNull(BeanUtils.toBean(updateReqVO, Demo02CategoryDO::class.java)))
    }

    override fun deleteDemo02Category(id: Long) {
        validateExists(id)
        if (Demo02CategoryDao.selectCountByParentId(id) > 0) {
            throw exception(DEMO02_CATEGORY_EXITS_CHILDREN)
        }
        Demo02CategoryDao.deleteById(id)
    }

    override fun getDemo02Category(id: Long): Demo02CategoryDO? = Demo02CategoryDao.selectById(id)

    override fun getDemo02CategoryList(listReqVO: Demo02CategoryListReqVO): List<Demo02CategoryDO> =
        Demo02CategoryDao.selectList(listReqVO)

    private fun validateExists(id: Long): Demo02CategoryDO =
        Demo02CategoryDao.selectById(id) ?: throw exception(DEMO02_CATEGORY_NOT_EXISTS)

    private fun validateParentDemo02Category(id: Long?, parentId: Long) {
        if (parentId == Demo02CategoryDO.PARENT_ID_ROOT) return
        if (id == parentId) throw exception(DEMO02_CATEGORY_PARENT_ERROR)
        var parent = Demo02CategoryDao.selectById(parentId) ?: throw exception(DEMO02_CATEGORY_PARENT_NOT_EXITS)
        if (id == null) return
        repeat(Short.MAX_VALUE.toInt()) {
            val ancestorId = parent.parentId
            if (ancestorId == id) throw exception(DEMO02_CATEGORY_PARENT_IS_CHILD)
            if (ancestorId == null || ancestorId == Demo02CategoryDO.PARENT_ID_ROOT) return
            parent = Demo02CategoryDao.selectById(ancestorId) ?: return
        }
    }

    private fun validateNameUnique(id: Long?, parentId: Long, name: String) {
        val existing = Demo02CategoryDao.selectByParentIdAndName(parentId, name) ?: return
        if (id == null || existing.id != id) throw exception(DEMO02_CATEGORY_NAME_DUPLICATE)
    }

}
