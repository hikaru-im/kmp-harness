package im.hikaru.ruoyi.module.mp.controller.admin.news

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.news.vo.MpDraftPageReqVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.DRAFT_CREATE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.DRAFT_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.DRAFT_LIST_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.DRAFT_UPDATE_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.material.MpMaterialService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import me.chanjar.weixin.mp.bean.draft.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号草稿")
@RestController
@RequestMapping("/mp/draft")
@Validated
class MpDraftController(
    private val mpServiceFactory: MpServiceFactory,
    private val mpMaterialService: MpMaterialService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得草稿分页")
    @PreAuthorize("@ss.hasPermission('mp:draft:query')")
    fun getDraftPage(reqVO: MpDraftPageReqVO): CommonResult<PageResult<WxMpDraftItem>> {
        val draftList = try {
            mpServiceFactory.getRequiredMpService(requireNotNull(reqVO.accountId)).draftService
                .listDraft((reqVO.pageNo - 1) * reqVO.pageSize, reqVO.pageSize)
        } catch (ex: Exception) {
            throw exception(DRAFT_LIST_FAIL, wxErrorMessage(ex))
        }
        setDraftThumbUrl(draftList.items.orEmpty())
        return CommonResult.success(PageResult(draftList.totalCount?.toLong() ?: 0L, draftList.items.orEmpty()))
    }

    @PostMapping("/create")
    @Operation(summary = "创建草稿")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:draft:create')")
    fun deleteDraft(@RequestParam("accountId") accountId: Long, @RequestBody draft: WxMpAddDraft): CommonResult<String> = try {
        CommonResult.success(mpServiceFactory.getRequiredMpService(accountId).draftService.addDraft(draft))
    } catch (ex: Exception) {
        throw exception(DRAFT_CREATE_FAIL, wxErrorMessage(ex))
    }

    @PutMapping("/update")
    @Operation(summary = "更新草稿")
    @Parameters(Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "1024"), Parameter(name = "mediaId", description = "草稿素材的编号", required = true, example = "xxx"))
    @PreAuthorize("@ss.hasPermission('mp:draft:update')")
    fun deleteDraft(@RequestParam("accountId") accountId: Long, @RequestParam("mediaId") mediaId: String, @RequestBody articles: List<WxMpDraftArticles>): CommonResult<Boolean> = try {
        val service = mpServiceFactory.getRequiredMpService(accountId).draftService
        articles.forEachIndexed { index, article -> service.updateDraft(WxMpUpdateDraft(mediaId, index, article)) }
        CommonResult.success(true)
    } catch (ex: Exception) {
        throw exception(DRAFT_UPDATE_FAIL, wxErrorMessage(ex))
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除草稿")
    @Parameters(Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "1024"), Parameter(name = "mediaId", description = "草稿素材的编号", required = true, example = "xxx"))
    @PreAuthorize("@ss.hasPermission('mp:draft:delete')")
    fun deleteDraft(@RequestParam("accountId") accountId: Long, @RequestParam("mediaId") mediaId: String): CommonResult<Boolean> = try {
        mpServiceFactory.getRequiredMpService(accountId).draftService.delDraft(mediaId)
        CommonResult.success(true)
    } catch (ex: Exception) {
        throw exception(DRAFT_DELETE_FAIL, wxErrorMessage(ex))
    }

    private fun setDraftThumbUrl(items: List<WxMpDraftItem>) {
        val articles = items.flatMap { it.content?.newsItem.orEmpty() }
        val mediaIds = articles.mapNotNull { it.thumbMediaId }.toSet()
        val materials = mpMaterialService.getMaterialListByMediaId(mediaIds).associateBy { it.mediaId }
        articles.forEach { article -> materials[article.thumbMediaId]?.url?.let { article.thumbUrl = it } }
    }

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? me.chanjar.weixin.common.error.WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
