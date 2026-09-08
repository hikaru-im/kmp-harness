package im.hikaru.ruoyi.module.mp.controller.admin.news

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.news.vo.MpFreePublishPageReqVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.FREE_PUBLISH_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.FREE_PUBLISH_LIST_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.FREE_PUBLISH_SUBMIT_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.material.MpMaterialService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import me.chanjar.weixin.mp.bean.freepublish.WxMpFreePublishItem
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号发布能力")
@RestController
@RequestMapping("/mp/free-publish")
@Validated
class MpFreePublishController(
    private val mpServiceFactory: MpServiceFactory,
    private val mpMaterialService: MpMaterialService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得已发布的图文分页")
    @PreAuthorize("@ss.hasPermission('mp:free-publish:query')")
    fun getFreePublishPage(reqVO: MpFreePublishPageReqVO): CommonResult<PageResult<WxMpFreePublishItem>> {
        val records = try {
            mpServiceFactory.getRequiredMpService(requireNotNull(reqVO.accountId)).freePublishService
                .getPublicationRecords((reqVO.pageNo - 1) * reqVO.pageSize, reqVO.pageSize)
        } catch (ex: Exception) {
            throw exception(FREE_PUBLISH_LIST_FAIL, wxErrorMessage(ex))
        }
        setThumbUrl(records.items.orEmpty())
        return CommonResult.success(PageResult(records.totalCount?.toLong() ?: 0L, records.items.orEmpty()))
    }

    @PostMapping("/submit")
    @Operation(summary = "发布草稿")
    @Parameters(Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "1024"), Parameter(name = "mediaId", description = "要发布的草稿的 media_id", required = true, example = "2048"))
    @PreAuthorize("@ss.hasPermission('mp:free-publish:submit')")
    fun submitFreePublish(@RequestParam("accountId") accountId: Long, @RequestParam("mediaId") mediaId: String): CommonResult<String> = try {
        CommonResult.success(mpServiceFactory.getRequiredMpService(accountId).freePublishService.submit(mediaId))
    } catch (ex: Exception) {
        throw exception(FREE_PUBLISH_SUBMIT_FAIL, wxErrorMessage(ex))
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除草稿")
    @Parameters(Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "1024"), Parameter(name = "articleId", description = "发布记录的编号", required = true, example = "2048"))
    @PreAuthorize("@ss.hasPermission('mp:free-publish:delete')")
    fun deleteFreePublish(@RequestParam("accountId") accountId: Long, @RequestParam("articleId") articleId: String): CommonResult<Boolean> = try {
        mpServiceFactory.getRequiredMpService(accountId).freePublishService.deletePushAllArticle(articleId)
        CommonResult.success(true)
    } catch (ex: Exception) {
        throw exception(FREE_PUBLISH_DELETE_FAIL, wxErrorMessage(ex))
    }

    private fun setThumbUrl(items: List<WxMpFreePublishItem>) {
        val articles = items.flatMap { it.content?.newsItem.orEmpty() }
        val mediaIds = articles.mapNotNull { it.thumbMediaId }.toSet()
        val materials = mpMaterialService.getMaterialListByMediaId(mediaIds).associateBy { it.mediaId }
        articles.forEach { article -> materials[article.thumbMediaId]?.url?.let { article.thumbUrl = it } }
    }

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? me.chanjar.weixin.common.error.WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
