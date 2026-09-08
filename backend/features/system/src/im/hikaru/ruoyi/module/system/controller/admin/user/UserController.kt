package im.hikaru.ruoyi.module.system.controller.admin.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.*
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Admin - User")
@RestController
@RequestMapping("/system/user")
@Validated
class UserController(
    private val userService: AdminUserService,
    private val deptService: DeptService,
) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:user:create')")
    fun create(@Valid @RequestBody req: UserSaveReqVO) = CommonResult.success(userService.createUser(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:user:update')")
    fun update(@Valid @RequestBody req: UserSaveReqVO): CommonResult<Boolean> { userService.updateUser(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:user:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { userService.deleteUser(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:user:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { userService.deleteUserList(ids); return CommonResult.success(true) }
    @PutMapping("/update-password") @PreAuthorize("@ss.hasPermission('system:user:update-password')")
    fun updatePassword(@Valid @RequestBody req: UserUpdatePasswordReqVO): CommonResult<Boolean> { userService.updateUserPassword(requireNotNull(req.id), requireNotNull(req.password)); return CommonResult.success(true) }
    @PutMapping("/update-status") @PreAuthorize("@ss.hasPermission('system:user:update')")
    fun updateStatus(@Valid @RequestBody req: UserUpdateStatusReqVO): CommonResult<Boolean> { userService.updateUserStatus(requireNotNull(req.id), requireNotNull(req.status)); return CommonResult.success(true) }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:user:query')")
    fun page(req: UserPageReqVO): CommonResult<PageResult<UserRespVO>> = CommonResult.success(userService.getUserPage(req).let { result -> PageResult(result.total ?: 0L, result.list.map { it.toResp() }) })
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('system:user:query')")
    fun list(@RequestParam("ids") ids: List<Long>): CommonResult<List<UserRespVO>> = CommonResult.success(userService.getUserList(ids).map { it.toResp() })
    @GetMapping("/list-all-simple", "/simple-list")
    fun simpleList(@RequestParam("deptId", required = false) deptId: Long?): CommonResult<List<UserSimpleRespVO>> {
        val users = if (deptId == null) userService.getUserListByStatus(CommonStatusEnum.ENABLE.status) else userService.getDeptUsers(listOf(deptId))
        return CommonResult.success(users.map { it.toSimpleResp() })
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:user:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<UserRespVO?> = CommonResult.success(userService.getUser(id)?.toResp())
    @GetMapping("/get-simple")
    fun getSimple(@RequestParam("id") id: Long): CommonResult<UserSimpleRespVO?> = CommonResult.success(userService.getUser(id)?.toSimpleResp())
    @GetMapping("/list-by-nickname")
    fun listByNickname(@RequestParam("nickname") nickname: String): CommonResult<List<UserSimpleRespVO>> = CommonResult.success(if (nickname.isBlank()) emptyList() else userService.getUserListByNickname(nickname.trim()).map { it.toSimpleResp() })

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:user:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(req: UserPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val users = userService.getUserPage(req).list.map { it.toResp() }
        ExcelUtils.write(response, "users.xls", "Users", UserRespVO::class.java, users)
    }

    @GetMapping("/get-import-template")
    fun importTemplate(response: HttpServletResponse) {
        val users = listOf(
            importExample("yunai", 1L, "yunai@iocoder.cn", "15601691300", "Yunai", 1, CommonStatusEnum.ENABLE.status),
            importExample("yuanma", 2L, "yuanma@iocoder.cn", "15601701300", "Yuanma", 2, CommonStatusEnum.DISABLE.status),
        )
        ExcelUtils.write(response, "user-import-template.xls", "Users", UserImportExcelVO::class.java, users)
    }

    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('system:user:import')")
    @ApiAccessLog(operateType = [OperateTypeEnum.IMPORT])
    fun importExcel(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "updateSupport", defaultValue = "false") updateSupport: Boolean,
    ): CommonResult<UserImportRespVO> = CommonResult.success(
        userService.importUserList(ExcelUtils.read(file, UserImportExcelVO::class.java), updateSupport),
    )

    private fun AdminUserDO.toResp() = UserRespVO().apply {
        id = this@toResp.id; username = this@toResp.username; nickname = this@toResp.nickname; remark = this@toResp.remark; deptId = this@toResp.deptId; postIds = this@toResp.postIds
        email = this@toResp.email; mobile = this@toResp.mobile; sex = this@toResp.sex; avatar = this@toResp.avatar; status = this@toResp.status; loginIp = this@toResp.loginIp
        loginDate = this@toResp.loginDate?.toJava(); createTime = this@toResp.createTime?.toJava(); deptName = this@toResp.deptId?.let { deptService.getDept(it)?.name }
    }
    private fun AdminUserDO.toSimpleResp() = UserSimpleRespVO(id, nickname, avatar, sex, deptId, deptId?.let { deptService.getDept(it)?.name })
    private fun importExample(
        username: String,
        deptId: Long,
        email: String,
        mobile: String,
        nickname: String,
        sex: Int,
        status: Int,
    ) = UserImportExcelVO().apply {
        this.username = username
        this.deptId = deptId
        this.email = email
        this.mobile = mobile
        this.nickname = nickname
        this.sex = sex
        this.status = status
    }
    private fun kotlinx.datetime.LocalDateTime.toJava() = java.time.LocalDateTime.of(year, month.ordinal + 1, day, hour, minute, second, nanosecond)
}
