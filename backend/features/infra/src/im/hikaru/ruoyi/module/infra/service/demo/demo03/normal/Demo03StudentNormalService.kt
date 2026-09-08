package im.hikaru.ruoyi.module.infra.service.demo.demo03.normal

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO

interface Demo03StudentNormalService {
    fun createDemo03Student(createReqVO: Demo03StudentNormalSaveReqVO): Long
    fun updateDemo03Student(updateReqVO: Demo03StudentNormalSaveReqVO)
    fun deleteDemo03Student(id: Long)
    fun deleteDemo03StudentList(ids: List<Long>)
    fun getDemo03Student(id: Long): Demo03StudentDO?
    fun getDemo03StudentPage(pageReqVO: Demo03StudentNormalPageReqVO): PageResult<Demo03StudentDO>
    fun getDemo03CourseListByStudentId(studentId: Long): List<Demo03CourseDO>
    fun getDemo03GradeByStudentId(studentId: Long): Demo03GradeDO?
}
