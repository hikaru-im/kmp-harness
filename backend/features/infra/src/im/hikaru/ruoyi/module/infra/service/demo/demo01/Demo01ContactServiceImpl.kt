package im.hikaru.ruoyi.module.infra.service.demo.demo01

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo01.Demo01ContactDO
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo01.Demo01ContactDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO01_CONTACT_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class Demo01ContactServiceImpl : Demo01ContactService {
    override fun createDemo01Contact(createReqVO: Demo01ContactSaveReqVO): Long =
        Demo01ContactDao.insert(requireNotNull(BeanUtils.toBean(createReqVO, Demo01ContactDO::class.java)))

    override fun updateDemo01Contact(updateReqVO: Demo01ContactSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateExists(id)
        Demo01ContactDao.updateById(requireNotNull(BeanUtils.toBean(updateReqVO, Demo01ContactDO::class.java)))
    }

    override fun deleteDemo01Contact(id: Long) {
        validateExists(id)
        Demo01ContactDao.deleteById(id)
    }

    override fun deleteDemo01ContactList(ids: List<Long>) {
        val existing = Demo01ContactDao.selectByIds(ids)
        if (existing.size != ids.toSet().size) throw exception(DEMO01_CONTACT_NOT_EXISTS)
        Demo01ContactDao.deleteByIds(ids)
    }

    override fun getDemo01Contact(id: Long): Demo01ContactDO? = Demo01ContactDao.selectById(id)

    override fun getDemo01ContactPage(pageReqVO: Demo01ContactPageReqVO): PageResult<Demo01ContactDO> =
        Demo01ContactDao.selectPage(pageReqVO)

    private fun validateExists(id: Long) {
        if (Demo01ContactDao.selectById(id) == null) throw exception(DEMO01_CONTACT_NOT_EXISTS)
    }
}
