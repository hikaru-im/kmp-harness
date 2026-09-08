package im.hikaru.ruoyi.module.member.service.group

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupUpdateReqVO
import im.hikaru.ruoyi.module.member.convert.group.MemberGroupConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO
import im.hikaru.ruoyi.module.member.dal.mysql.group.MemberGroupDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.GROUP_HAS_USER
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.GROUP_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberGroupServiceImpl(
    private val memberUserService: MemberUserService,
) : MemberGroupService {
    override fun createGroup(createReqVO: MemberGroupCreateReqVO): Long = MemberGroupDao.insert(MemberGroupConvert.convert(createReqVO))
    override fun updateGroup(updateReqVO: MemberGroupUpdateReqVO) {
        validateGroupExists(requireNotNull(updateReqVO.id))
        MemberGroupDao.updateById(MemberGroupConvert.convert(updateReqVO))
    }
    override fun deleteGroup(id: Long) {
        validateGroupExists(id)
        if (memberUserService.getUserCountByGroupId(id) > 0) throw exception(GROUP_HAS_USER)
        MemberGroupDao.deleteById(id)
    }
    override fun getGroup(id: Long): MemberGroupDO? = MemberGroupDao.selectById(id)
    override fun getGroupList(ids: Collection<Long>): List<MemberGroupDO> = MemberGroupDao.selectByIds(ids)
    override fun getGroupPage(pageReqVO: MemberGroupPageReqVO): PageResult<MemberGroupDO> = MemberGroupDao.selectPage(pageReqVO)
    override fun getGroupListByStatus(status: Int): List<MemberGroupDO> = MemberGroupDao.selectListByStatus(status)
    override fun getEnableGroupList(): List<MemberGroupDO> = getGroupListByStatus(CommonStatusEnum.ENABLE.status)
    private fun validateGroupExists(id: Long) { if (MemberGroupDao.selectById(id) == null) throw exception(GROUP_NOT_EXISTS) }
}
