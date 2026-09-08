package im.hikaru.ruoyi.module.infra.service.demo.demo03.erp

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO

interface Demo03StudentErpService {
    fun createDemo03Student(createReqVO: Demo03StudentErpSaveReqVO): Long
    fun updateDemo03Student(updateReqVO: Demo03StudentErpSaveReqVO)
    fun deleteDemo03Student(id: Long)
    fun deleteDemo03StudentList(ids: List<Long>)
    fun getDemo03Student(id: Long): Demo03StudentDO?
    fun getDemo03StudentPage(pageReqVO: Demo03StudentErpPageReqVO): PageResult<Demo03StudentDO>

    fun getDemo03CoursePage(pageReqVO: PageParam, studentId: Long): PageResult<Demo03CourseDO>
    fun createDemo03Course(demo03Course: Demo03CourseDO): Long
    fun updateDemo03Course(demo03Course: Demo03CourseDO)
    fun deleteDemo03Course(id: Long)
    fun deleteDemo03CourseList(ids: List<Long>)
    fun getDemo03Course(id: Long): Demo03CourseDO?

    fun getDemo03GradePage(pageReqVO: PageParam, studentId: Long): PageResult<Demo03GradeDO>
    fun createDemo03Grade(demo03Grade: Demo03GradeDO): Long
    fun updateDemo03Grade(demo03Grade: Demo03GradeDO)
    fun deleteDemo03Grade(id: Long)
    fun deleteDemo03GradeList(ids: List<Long>)
    fun getDemo03Grade(id: Long): Demo03GradeDO?
}
