package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo.Demo03StudentNormalSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
import im.hikaru.ruoyi.module.infra.service.demo.demo03.normal.Demo03StudentNormalService
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

@Tag(name = "Admin - Demo03 student (normal)")
@RestController
@RequestMapping("/infra/demo03-student-normal")
@Validated
class Demo03StudentNormalController(
    private val service: Demo03StudentNormalService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create student")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:create')")
    fun create(@Valid @RequestBody req: Demo03StudentNormalSaveReqVO): CommonResult<Long> =
        CommonResult.success(service.createDemo03Student(req))

    @PutMapping("/update")
    @Operation(summary = "Update student")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:update')")
    fun update(@Valid @RequestBody req: Demo03StudentNormalSaveReqVO): CommonResult<Boolean> {
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
    fun get(@RequestParam("id") id: Long): CommonResult<Demo03StudentNormalRespVO?> =
        CommonResult.success(service.getDemo03Student(id)?.toResp())

    @GetMapping("/page")
    @Operation(summary = "Page students")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun page(@Valid req: Demo03StudentNormalPageReqVO): CommonResult<PageResult<Demo03StudentNormalRespVO>> =
        CommonResult.success(service.getDemo03StudentPage(req).toRespPage())

    @GetMapping("/export-excel")
    @Operation(summary = "Export students")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:export')")
    fun export(req: Demo03StudentNormalPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val rows = service.getDemo03StudentPage(req).list.map { it.toResp() }
        ExcelUtils.write(response, "demo03-students-normal.xls", "data", Demo03StudentNormalRespVO::class.java, rows)
    }

    @GetMapping("/demo03-course/list-by-student-id")
    @Operation(summary = "List student courses")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun courseList(@RequestParam("studentId") studentId: Long): CommonResult<List<Demo03CourseDO>> =
        CommonResult.success(service.getDemo03CourseListByStudentId(studentId))

    @GetMapping("/demo03-grade/get-by-student-id")
    @Operation(summary = "Get student grade")
    @PreAuthorize("@ss.hasPermission('infra:demo03-student:query')")
    fun grade(@RequestParam("studentId") studentId: Long): CommonResult<Demo03GradeDO?> =
        CommonResult.success(service.getDemo03GradeByStudentId(studentId))

    private fun Demo03StudentDO.toResp() = Demo03StudentNormalRespVO().apply {
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
