package im.hikaru.ruoyi.module.infra.service.demo.demo03.erp

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03.Demo03CourseDao
import im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03.Demo03GradeDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO03_COURSE_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO03_GRADE_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DEMO03_GRADE_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.service.demo.demo03.Demo03StudentServiceSupport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class Demo03StudentErpServiceImpl : Demo03StudentErpService {
    override fun createDemo03Student(createReqVO: Demo03StudentErpSaveReqVO): Long =
        Demo03StudentServiceSupport.insertStudent(createReqVO.toEntity())

    override fun updateDemo03Student(updateReqVO: Demo03StudentErpSaveReqVO) {
        Demo03StudentServiceSupport.updateStudent(updateReqVO.toEntity())
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteDemo03Student(id: Long) = Demo03StudentServiceSupport.deleteStudent(id)

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteDemo03StudentList(ids: List<Long>) = Demo03StudentServiceSupport.deleteStudentList(ids)

    override fun getDemo03Student(id: Long): Demo03StudentDO? = Demo03StudentServiceSupport.getStudent(id)

    override fun getDemo03StudentPage(pageReqVO: Demo03StudentErpPageReqVO): PageResult<Demo03StudentDO> =
        Demo03StudentServiceSupport.getStudentPage(
            pageReqVO,
            pageReqVO.name,
            pageReqVO.sex,
            pageReqVO.description,
            pageReqVO.createTime,
        )

    override fun getDemo03CoursePage(pageReqVO: PageParam, studentId: Long): PageResult<Demo03CourseDO> =
        Demo03CourseDao.selectPage(pageReqVO, studentId)

    override fun createDemo03Course(demo03Course: Demo03CourseDO): Long {
        demo03Course.clean()
        return Demo03CourseDao.insert(demo03Course)
    }

    override fun updateDemo03Course(demo03Course: Demo03CourseDO) {
        val id = requireNotNull(demo03Course.id)
        if (Demo03CourseDao.selectById(id) == null) throw exception(DEMO03_COURSE_NOT_EXISTS)
        demo03Course.clean()
        Demo03CourseDao.updateById(demo03Course)
    }

    override fun deleteDemo03Course(id: Long) {
        Demo03CourseDao.deleteById(id)
    }

    override fun deleteDemo03CourseList(ids: List<Long>) {
        Demo03CourseDao.deleteByIds(ids)
    }

    override fun getDemo03Course(id: Long): Demo03CourseDO? = Demo03CourseDao.selectById(id)

    override fun getDemo03GradePage(pageReqVO: PageParam, studentId: Long): PageResult<Demo03GradeDO> =
        Demo03GradeDao.selectPage(pageReqVO, studentId)

    override fun createDemo03Grade(demo03Grade: Demo03GradeDO): Long {
        val studentId = requireNotNull(demo03Grade.studentId)
        if (Demo03GradeDao.selectByStudentId(studentId) != null) throw exception(DEMO03_GRADE_EXISTS)
        demo03Grade.clean()
        return Demo03GradeDao.insert(demo03Grade)
    }

    override fun updateDemo03Grade(demo03Grade: Demo03GradeDO) {
        val id = requireNotNull(demo03Grade.id)
        if (Demo03GradeDao.selectById(id) == null) throw exception(DEMO03_GRADE_NOT_EXISTS)
        demo03Grade.clean()
        Demo03GradeDao.updateById(demo03Grade)
    }

    override fun deleteDemo03Grade(id: Long) {
        Demo03GradeDao.deleteById(id)
    }

    override fun deleteDemo03GradeList(ids: List<Long>) {
        Demo03GradeDao.deleteByIds(ids)
    }

    override fun getDemo03Grade(id: Long): Demo03GradeDO? = Demo03GradeDao.selectById(id)

    private fun Demo03StudentErpSaveReqVO.toEntity(): Demo03StudentDO =
        Demo03StudentServiceSupport.newStudent(id, name, sex, birthday, description)
}
