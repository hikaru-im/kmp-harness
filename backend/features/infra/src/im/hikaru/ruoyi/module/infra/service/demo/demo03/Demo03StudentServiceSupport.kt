package im.hikaru.ruoyi.module.infra.service.demo.demo03

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03.Demo03CourseDao
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03.Demo03GradeDao
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03.Demo03StudentDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO03_STUDENT_NOT_EXISTS
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime

internal object Demo03StudentServiceSupport {
    fun newStudent(
        id: Long?,
        name: String?,
        sex: Int?,
        birthday: LocalDateTime?,
        description: String?,
    ): Demo03StudentDO = Demo03StudentDO().apply {
        this.id = id
        this.name = name
        this.sex = sex
        this.birthday = birthday?.toKotlinLocalDateTime()
        this.description = description
    }

    fun insertStudent(student: Demo03StudentDO): Long = Demo03StudentDao.insert(student)

    fun updateStudent(student: Demo03StudentDO) {
        validateStudentExists(requireNotNull(student.id))
        student.clean()
        Demo03StudentDao.updateById(student)
    }

    fun deleteStudent(id: Long) {
        validateStudentExists(id)
        Demo03StudentDao.deleteById(id)
        Demo03CourseDao.deleteByStudentId(id)
        Demo03GradeDao.deleteByStudentId(id)
    }

    fun deleteStudentList(ids: List<Long>) {
        validateStudentExists(ids)
        Demo03StudentDao.deleteByIds(ids)
        Demo03CourseDao.deleteByStudentIds(ids)
        Demo03GradeDao.deleteByStudentIds(ids)
    }

    fun getStudent(id: Long): Demo03StudentDO? = Demo03StudentDao.selectById(id)

    fun getStudentPage(
        pageParam: PageParam,
        name: String?,
        sex: Int?,
        description: String?,
        createTime: Array<LocalDateTime>?,
    ): PageResult<Demo03StudentDO> =
        Demo03StudentDao.selectPage(pageParam, name, sex, description, createTime)

    fun getCourseList(studentId: Long): List<Demo03CourseDO> = Demo03CourseDao.selectListByStudentId(studentId)

    fun insertCourses(studentId: Long, courses: List<Demo03CourseDO>) {
        courses.forEach {
            it.studentId = studentId
            it.clean()
        }
        Demo03CourseDao.insertBatch(courses)
    }

    fun updateCourses(studentId: Long, courses: List<Demo03CourseDO>) {
        courses.forEach {
            it.studentId = studentId
            it.clean()
        }
        val oldCourses = Demo03CourseDao.selectListByStudentId(studentId)
        val oldIds = oldCourses.mapNotNull { it.id }.toSet()
        val requestedIds = courses.mapNotNull { it.id }.toSet()
        val inserts = courses.filter { it.id == null || it.id !in oldIds }
        val updates = courses.filter { it.id in oldIds }
        val deleteIds = oldIds - requestedIds

        Demo03CourseDao.insertBatch(inserts)
        Demo03CourseDao.updateBatch(updates)
        Demo03CourseDao.deleteByIds(deleteIds)
    }

    fun getGrade(studentId: Long): Demo03GradeDO? = Demo03GradeDao.selectByStudentId(studentId)

    fun insertGrade(studentId: Long, grade: Demo03GradeDO?) {
        if (grade == null) return
        grade.studentId = studentId
        grade.clean()
        Demo03GradeDao.insert(grade)
    }

    fun upsertGrade(studentId: Long, grade: Demo03GradeDO?) {
        if (grade == null) return
        grade.studentId = studentId
        grade.clean()
        Demo03GradeDao.insertOrUpdate(grade)
    }

    private fun validateStudentExists(id: Long) {
        if (Demo03StudentDao.selectById(id) == null) throw exception(DEMO03_STUDENT_NOT_EXISTS)
    }

    private fun validateStudentExists(ids: List<Long>) {
        if (Demo03StudentDao.selectByIds(ids).size != ids.size) throw exception(DEMO03_STUDENT_NOT_EXISTS)
    }
}
