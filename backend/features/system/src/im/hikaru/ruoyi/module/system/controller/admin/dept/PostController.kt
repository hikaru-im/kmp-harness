package im.hikaru.ruoyi.module.system.controller.admin.dept

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.*
import im.hikaru.ruoyi.module.system.service.dept.PostService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - Post")
@RestController
@RequestMapping("/system/post")
@Validated
class PostController(private val service: PostService) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:post:create')")
    fun create(@Valid @RequestBody req: PostSaveReqVO) = CommonResult.success(service.createPost(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:post:update')")
    fun update(@Valid @RequestBody req: PostSaveReqVO): CommonResult<Boolean> { service.updatePost(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:post:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deletePost(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:post:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deletePostList(ids); return CommonResult.success(true) }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:post:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<PostRespVO?> = CommonResult.success(service.getPost(id)?.toResp())
    @GetMapping("/list-all-simple", "/simple-list")
    fun simpleList(): CommonResult<List<PostSimpleRespVO>> = CommonResult.success(service.getPostList(null, listOf(CommonStatusEnum.ENABLE.status)).sortedBy { it.sort ?: 0 }.map { PostSimpleRespVO().apply { id = it.id; name = it.name } })
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:post:query')")
    fun page(req: PostPageReqVO): CommonResult<PageResult<PostRespVO>> = CommonResult.success(service.getPostPage(req).let { PageResult(it.total ?: 0L, it.list.map { post -> post.toResp() }) })

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:post:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(response: HttpServletResponse, req: PostPageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(response, "posts.xls", "Posts", PostRespVO::class.java, service.getPostPage(req).list.map { it.toResp() })
    }

    private fun im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO.toResp() = PostRespVO().apply { id = this@toResp.id; name = this@toResp.name; code = this@toResp.code; sort = this@toResp.sort; status = this@toResp.status; remark = this@toResp.remark; createTime = this@toResp.createTime?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) } }
}
