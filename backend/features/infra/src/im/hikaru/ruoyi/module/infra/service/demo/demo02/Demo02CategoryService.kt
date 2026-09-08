package im.hikaru.ruoyi.module.infra.service.demo.demo02

import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategorySaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo02.Demo02CategoryDO

interface Demo02CategoryService {
    fun createDemo02Category(createReqVO: Demo02CategorySaveReqVO): Long
    fun updateDemo02Category(updateReqVO: Demo02CategorySaveReqVO)
    fun deleteDemo02Category(id: Long)
    fun getDemo02Category(id: Long): Demo02CategoryDO?
    fun getDemo02CategoryList(listReqVO: Demo02CategoryListReqVO): List<Demo02CategoryDO>
}
