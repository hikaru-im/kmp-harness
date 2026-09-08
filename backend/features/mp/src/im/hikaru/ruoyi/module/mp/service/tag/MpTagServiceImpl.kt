package im.hikaru.ruoyi.module.mp.service.tag

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagUpdateReqVO
import im.hikaru.ruoyi.module.mp.convert.tag.MpTagConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.tag.MpTagDO
import im.hikaru.ruoyi.module.mp.dal.mysql.tag.MpTagDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.TAG_CREATE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.TAG_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.TAG_GET_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.TAG_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.TAG_UPDATE_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import me.chanjar.weixin.common.error.WxErrorException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpTagServiceImpl(
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
) : MpTagService {
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()
    override fun createTag(createReqVO: MpTagCreateReqVO): Long {
        val accountId = requireNotNull(createReqVO.accountId)
        val account = mpAccountService.getRequiredAccount(accountId)
        val wxTag = try {
            mpServiceFactory.getRequiredMpService(accountId).userTagService.tagCreate(requireNotNull(createReqVO.name))
        } catch (ex: Exception) {
            throw exception(TAG_CREATE_FAIL, wxErrorMessage(ex))
        }
        return MpTagDao.insert(MpTagConvert.convert(wxTag, account))
    }

    override fun updateTag(updateReqVO: MpTagUpdateReqVO) {
        val tag = validateTagExists(requireNotNull(updateReqVO.id))
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(tag.accountId)).userTagService
                .tagUpdate(requireNotNull(tag.tagId), requireNotNull(updateReqVO.name))
        } catch (ex: Exception) {
            throw exception(TAG_UPDATE_FAIL, wxErrorMessage(ex))
        }
        MpTagDao.updateById(MpTagDO().apply {
            id = tag.id
            name = updateReqVO.name
        })
    }

    override fun deleteTag(id: Long) {
        val tag = validateTagExists(id)
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(tag.accountId)).userTagService
                .tagDelete(requireNotNull(tag.tagId))
        } catch (ex: Exception) {
            throw exception(TAG_DELETE_FAIL, wxErrorMessage(ex))
        }
        MpTagDao.deleteById(id)
    }

    override fun getTagPage(pageReqVO: MpTagPageReqVO): PageResult<MpTagDO> = MpTagDao.selectPage(pageReqVO)
    override fun get(id: Long): MpTagDO = validateTagExists(id)
    override fun getTagList(): List<MpTagDO> = MpTagDao.selectList()

    @Transactional(rollbackFor = [Exception::class])
    override fun syncTag(accountId: Long) {
        val account = mpAccountService.getRequiredAccount(accountId)
        val wxTags = try {
            mpServiceFactory.getRequiredMpService(accountId).userTagService.tagGet()
        } catch (ex: Exception) {
            throw exception(TAG_GET_FAIL, wxErrorMessage(ex))
        }
        val existing = MpTagDao.selectListByAccountId(accountId).associateBy { it.tagId }.toMutableMap()
        wxTags.orEmpty().forEach { wxTag ->
            val dbTag = existing.remove(wxTag.id)
            if (dbTag == null) {
                MpTagDao.insert(MpTagConvert.convert(wxTag, account))
            } else {
                MpTagDao.updateById(MpTagDO().apply {
                    id = dbTag.id
                    name = wxTag.name
                    count = wxTag.count
                })
            }
        }
        MpTagDao.deleteByIds(existing.values.mapNotNull { it.id })
    }

    private fun validateTagExists(id: Long): MpTagDO = MpTagDao.selectById(id) ?: throw exception(TAG_NOT_EXISTS)

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
