package im.hikaru.ruoyi.module.system.controller.admin.dept

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.*
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - Department")
@RestController
@RequestMapping("/system/dept")
@Validated
class DeptController(private val service: DeptService) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:dept:create')")
    fun create(@Valid @RequestBody req: DeptSaveReqVO) = CommonResult.success(service.createDept(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:dept:update')")
    fun update(@Valid @RequestBody req: DeptSaveReqVO): CommonResult<Boolean> { service.updateDept(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deleteDept(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deleteDeptList(ids); return CommonResult.success(true) }
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('system:dept:query')")
    fun list(req: DeptListReqVO): CommonResult<List<DeptRespVO>> = CommonResult.success(service.getDeptList(req).map { it.toResp() })
    @GetMapping("/list-all-simple", "/simple-list")
    fun simpleList(): CommonResult<List<DeptSimpleRespVO>> = CommonResult.success(service.getDeptList(DeptListReqVO().apply { status = CommonStatusEnum.ENABLE.status }).map { DeptSimpleRespVO(it.id, it.name, it.parentId) })
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:dept:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<DeptRespVO?> = CommonResult.success(service.getDept(id)?.toResp())
    private fun im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO.toResp() = DeptRespVO().apply { id = this@toResp.id; name = this@toResp.name; parentId = this@toResp.parentId; sort = this@toResp.sort; leaderUserId = this@toResp.leaderUserId; phone = this@toResp.phone; email = this@toResp.email; status = this@toResp.status; createTime = this@toResp.createTime?.toJava() }
    private fun kotlinx.datetime.LocalDateTime.toJava() = java.time.LocalDateTime.of(year, month.ordinal + 1, day, hour, minute, second, nanosecond)
}
