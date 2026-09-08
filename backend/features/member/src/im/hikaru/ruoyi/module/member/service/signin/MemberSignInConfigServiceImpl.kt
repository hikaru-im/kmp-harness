package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigUpdateReqVO
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInConfigConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO
import im.hikaru.ruoyi.module.member.dal.mysql.signin.MemberSignInConfigDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.SIGN_IN_CONFIG_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.SIGN_IN_CONFIG_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberSignInConfigServiceImpl : MemberSignInConfigService {
    override fun createSignInConfig(createReqVO: MemberSignInConfigCreateReqVO): Long {
        validateDayUnique(requireNotNull(createReqVO.day), null)
        return MemberSignInConfigDao.insert(MemberSignInConfigConvert.convert(createReqVO))
    }
    override fun updateSignInConfig(updateReqVO: MemberSignInConfigUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateExists(id)
        validateDayUnique(requireNotNull(updateReqVO.day), id)
        MemberSignInConfigDao.updateById(MemberSignInConfigConvert.convert(updateReqVO))
    }
    override fun deleteSignInConfig(id: Long) { validateExists(id); MemberSignInConfigDao.deleteById(id) }
    override fun getSignInConfig(id: Long): MemberSignInConfigDO? = MemberSignInConfigDao.selectById(id)
    override fun getSignInConfigList(): List<MemberSignInConfigDO> = MemberSignInConfigDao.selectList().sortedBy { it.day }
    override fun getSignInConfigList(status: Int): List<MemberSignInConfigDO> = MemberSignInConfigDao.selectListByStatus(status).sortedBy { it.day }
    private fun validateExists(id: Long) { if (MemberSignInConfigDao.selectById(id) == null) throw exception(SIGN_IN_CONFIG_NOT_EXISTS) }
    private fun validateDayUnique(day: Int, id: Long?) {
        val config = MemberSignInConfigDao.selectByDay(day) ?: return
        if (config.id != id) throw exception(SIGN_IN_CONFIG_EXISTS)
    }
}
