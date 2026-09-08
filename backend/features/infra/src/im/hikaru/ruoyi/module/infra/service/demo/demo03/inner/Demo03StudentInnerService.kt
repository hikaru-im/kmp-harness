package im.hikaru.ruoyi.module.infra.service.demo.demo03.inner

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.inner.vo.Demo03StudentInnerPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.inner.vo.Demo03StudentInnerSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO

interface Demo03StudentInnerService {
    fun createDemo03Student(createReqVO: Demo03StudentInnerSaveReqVO): Long
    fun updateDemo03Student(updateReqVO: Demo03StudentInnerSaveReqVO)
    fun deleteDemo03Student(id: Long)
    fun deleteDemo03StudentList(ids: List<Long>)
    fun getDemo03Student(id: Long): Demo03StudentDO?
    fun getDemo03StudentPage(pageReqVO: Demo03StudentInnerPageReqVO): PageResult<Demo03StudentDO>
    fun getDemo03CourseListByStudentId(studentId: Long): List<Demo03CourseDO>
    fun getDemo03GradeByStudentId(studentId: Long): Demo03GradeDO?
}
