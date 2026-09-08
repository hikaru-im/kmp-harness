package im.hikaru.ruoyi.module.infra.service.demo.demo01

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo01.Demo01ContactDO

interface Demo01ContactService {
    fun createDemo01Contact(createReqVO: Demo01ContactSaveReqVO): Long
    fun updateDemo01Contact(updateReqVO: Demo01ContactSaveReqVO)
    fun deleteDemo01Contact(id: Long)
    fun deleteDemo01ContactList(ids: List<Long>)
    fun getDemo01Contact(id: Long): Demo01ContactDO?
    fun getDemo01ContactPage(pageReqVO: Demo01ContactPageReqVO): PageResult<Demo01ContactDO>
}
