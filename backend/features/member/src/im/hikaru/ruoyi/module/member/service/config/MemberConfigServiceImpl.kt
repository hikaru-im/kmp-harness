package im.hikaru.ruoyi.module.member.service.config

import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigSaveReqVO
import im.hikaru.ruoyi.module.member.convert.config.MemberConfigConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO
import im.hikaru.ruoyi.module.member.dal.mysql.config.MemberConfigDao
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberConfigServiceImpl : MemberConfigService {
    override fun saveConfig(saveReqVO: MemberConfigSaveReqVO) {
        val entity = MemberConfigConvert.convert(saveReqVO)
        val current = getConfig()
        if (current == null) MemberConfigDao.insert(entity) else MemberConfigDao.updateById(entity.apply { id = current.id })
    }

    override fun getConfig(): MemberConfigDO? = MemberConfigDao.selectList().firstOrNull()
}
