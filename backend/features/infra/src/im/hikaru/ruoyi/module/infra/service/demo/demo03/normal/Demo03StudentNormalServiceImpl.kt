package im.hikaru.ruoyi.module.infra.service.demo.demo03.normal

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
import im.hikaru.ruoyi.module.infra.service.demo.demo03.Demo03StudentServiceSupport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class Demo03StudentNormalServiceImpl : Demo03StudentNormalService {
    @Transactional(rollbackFor = [Exception::class])
    override fun createDemo03Student(createReqVO: Demo03StudentNormalSaveReqVO): Long {
        val id = Demo03StudentServiceSupport.insertStudent(createReqVO.toEntity())
        Demo03StudentServiceSupport.insertCourses(id, createReqVO.demo03Courses.orEmpty())
        Demo03StudentServiceSupport.insertGrade(id, createReqVO.demo03Grade)
        return id
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateDemo03Student(updateReqVO: Demo03StudentNormalSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        Demo03StudentServiceSupport.updateStudent(updateReqVO.toEntity())
        updateReqVO.demo03Courses?.let { Demo03StudentServiceSupport.updateCourses(id, it) }
        Demo03StudentServiceSupport.upsertGrade(id, updateReqVO.demo03Grade)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteDemo03Student(id: Long) = Demo03StudentServiceSupport.deleteStudent(id)

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteDemo03StudentList(ids: List<Long>) = Demo03StudentServiceSupport.deleteStudentList(ids)

    override fun getDemo03Student(id: Long): Demo03StudentDO? = Demo03StudentServiceSupport.getStudent(id)

    override fun getDemo03StudentPage(pageReqVO: Demo03StudentNormalPageReqVO): PageResult<Demo03StudentDO> =
        Demo03StudentServiceSupport.getStudentPage(
            pageReqVO,
            pageReqVO.name,
            pageReqVO.sex,
            pageReqVO.description,
            pageReqVO.createTime,
        )

    override fun getDemo03CourseListByStudentId(studentId: Long): List<Demo03CourseDO> =
        Demo03StudentServiceSupport.getCourseList(studentId)

    override fun getDemo03GradeByStudentId(studentId: Long): Demo03GradeDO? =
        Demo03StudentServiceSupport.getGrade(studentId)

    private fun Demo03StudentNormalSaveReqVO.toEntity(): Demo03StudentDO =
        Demo03StudentServiceSupport.newStudent(id, name, sex, birthday, description)
}
