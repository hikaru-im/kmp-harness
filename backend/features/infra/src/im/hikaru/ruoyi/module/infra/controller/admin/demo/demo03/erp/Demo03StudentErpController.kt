package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo.Demo03StudentErpSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
import im.hikaru.ruoyi.module.infra.service.demo.demo03.erp.Demo03StudentErpService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Demo03 student (ERP)")
@RestController
@RequestMapping("/infra/demo03-student-erp")
@Validated
class Demo03StudentErpController(
    private val service: Demo03StudentErpService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create student")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:create')")
    fun create(@Valid @RequestBody req: Demo03StudentErpSaveReqVO): CommonResult<Long> =
        CommonResult.success(service.createDemo03Student(req))

    @PutMapping("/update")
    @Operation(summary = "Update student")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:update')")
    fun update(@Valid @RequestBody req: Demo03StudentErpSaveReqVO): CommonResult<Boolean> {
        service.updateDemo03Student(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete student")
    @Parameter(name = "id", description = "Student id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        service.deleteDemo03Student(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete students")
    @Parameter(name = "ids", description = "Student ids", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        service.deleteDemo03StudentList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get student")
    @Parameter(name = "id", description = "Student id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<Demo03StudentErpRespVO?> =
        CommonResult.success(service.getDemo03Student(id)?.toResp())

    @GetMapping("/page")
    @Operation(summary = "Page students")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun page(@Valid req: Demo03StudentErpPageReqVO): CommonResult<PageResult<Demo03StudentErpRespVO>> =
        CommonResult.success(service.getDemo03StudentPage(req).toRespPage())

    @GetMapping("/export-excel")
    @Operation(summary = "Export students")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:export')")
    fun export(req: Demo03StudentErpPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val rows = service.getDemo03StudentPage(req).list.map { it.toResp() }
        ExcelUtils.write(response, "demo03-students-erp.xls", "data", Demo03StudentErpRespVO::class.java, rows)
    }

    @GetMapping("/demo03-course/page")
    @Operation(summary = "Page student courses")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun coursePage(pageReqVO: PageParam, @RequestParam("studentId") studentId: Long): CommonResult<PageResult<Demo03CourseDO>> =
        CommonResult.success(service.getDemo03CoursePage(pageReqVO, studentId))

    @PostMapping("/demo03-course/create")
    @Operation(summary = "Create student course")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:create')")
    fun createCourse(@Valid @RequestBody course: Demo03CourseDO): CommonResult<Long> =
        CommonResult.success(service.createDemo03Course(course))

    @PutMapping("/demo03-course/update")
    @Operation(summary = "Update student course")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:update')")
    fun updateCourse(@Valid @RequestBody course: Demo03CourseDO): CommonResult<Boolean> {
        service.updateDemo03Course(course)
        return CommonResult.success(true)
    }

    @DeleteMapping("/demo03-course/delete")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun deleteCourse(@RequestParam("id") id: Long): CommonResult<Boolean> {
        service.deleteDemo03Course(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/demo03-course/delete-list")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun deleteCourseList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        service.deleteDemo03CourseList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/demo03-course/get")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun getCourse(@RequestParam("id") id: Long): CommonResult<Demo03CourseDO?> =
        CommonResult.success(service.getDemo03Course(id))

    @GetMapping("/demo03-grade/page")
    @Operation(summary = "Page student grades")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun gradePage(pageReqVO: PageParam, @RequestParam("studentId") studentId: Long): CommonResult<PageResult<Demo03GradeDO>> =
        CommonResult.success(service.getDemo03GradePage(pageReqVO, studentId))

    @PostMapping("/demo03-grade/create")
    @Operation(summary = "Create student grade")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:create')")
    fun createGrade(@Valid @RequestBody grade: Demo03GradeDO): CommonResult<Long> =
        CommonResult.success(service.createDemo03Grade(grade))

    @PutMapping("/demo03-grade/update")
    @Operation(summary = "Update student grade")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:update')")
    fun updateGrade(@Valid @RequestBody grade: Demo03GradeDO): CommonResult<Boolean> {
        service.updateDemo03Grade(grade)
        return CommonResult.success(true)
    }

    @DeleteMapping("/demo03-grade/delete")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun deleteGrade(@RequestParam("id") id: Long): CommonResult<Boolean> {
        service.deleteDemo03Grade(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/demo03-grade/delete-list")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:delete')")
    fun deleteGradeList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        service.deleteDemo03GradeList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/demo03-grade/get")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun getGrade(@RequestParam("id") id: Long): CommonResult<Demo03GradeDO?> =
        CommonResult.success(service.getDemo03Grade(id))

    private fun Demo03StudentDO.toResp() = Demo03StudentErpRespVO().apply {
        id = this@toResp.id
        name = this@toResp.name
        sex = this@toResp.sex
        birthday = this@toResp.birthday?.toJavaLocalDateTime()
        description = this@toResp.description
        createTime = this@toResp.createTime?.toJavaLocalDateTime()
    }

    private fun PageResult<Demo03StudentDO>.toRespPage() =
        PageResult(total = total ?: 0L, list = list.map { it.toResp() })
}
