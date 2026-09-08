package im.hikaru.ruoyi.module.member.service.tag

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagUpdateReqVO
import im.hikaru.ruoyi.module.member.convert.tag.MemberTagConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO
import im.hikaru.ruoyi.module.member.dal.mysql.tag.MemberTagDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.TAG_HAS_USER
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.TAG_NAME_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.TAG_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberTagServiceImpl(
    private val memberUserService: MemberUserService,
) : MemberTagService {
    override fun createTag(createReqVO: MemberTagCreateReqVO): Long {
        validateTagNameUnique(null, requireNotNull(createReqVO.name))
        return MemberTagDao.insert(MemberTagConvert.convert(createReqVO))
    }
    override fun updateTag(updateReqVO: MemberTagUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateTagExists(id)
        validateTagNameUnique(id, requireNotNull(updateReqVO.name))
        MemberTagDao.updateById(MemberTagConvert.convert(updateReqVO))
    }
    override fun deleteTag(id: Long) {
        validateTagExists(id)
        if (memberUserService.getUserCountByTagId(id) > 0) throw exception(TAG_HAS_USER)
        MemberTagDao.deleteById(id)
    }
    override fun getTag(id: Long): MemberTagDO? = MemberTagDao.selectById(id)
    override fun getTagList(ids: Collection<Long>): List<MemberTagDO> = MemberTagDao.selectByIds(ids)
    override fun getTagPage(pageReqVO: MemberTagPageReqVO): PageResult<MemberTagDO> = MemberTagDao.selectPage(pageReqVO)
    override fun getTagList(): List<MemberTagDO> = MemberTagDao.selectList()
    private fun validateTagExists(id: Long) { if (MemberTagDao.selectById(id) == null) throw exception(TAG_NOT_EXISTS) }
    private fun validateTagNameUnique(id: Long?, name: String) {
        val existing = MemberTagDao.selectByName(name) ?: return
        if (existing.id != id) throw exception(TAG_NAME_EXISTS)
    }
}
