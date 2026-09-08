package im.hikaru.ruoyi.module.member.dal.dataobject.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.TerminalEnum
import im.hikaru.ruoyi.framework.ip.core.Area
import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import im.hikaru.ruoyi.module.system.enums.common.SexEnum
import kotlinx.datetime.LocalDateTime
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class MemberUserDO : TenantBaseDO {
    var id: Long? = null
    var mobile: String? = null
    var email: String? = null
    var password: String? = null
    var status: Int? = null
    var registerIp: String? = null
    var registerTerminal: Int? = null
    var loginIp: String? = null
    var loginDate: LocalDateTime? = null
    var nickname: String? = null
    var avatar: String? = null
    var profileVersion: Long = 1
    var name: String? = null
    var sex: Int? = null
    var birthday: LocalDateTime? = null
    var areaId: Int? = null
    var mark: String? = null
    var point: Int? = null
    var tagIds: List<Long>? = null
    var levelId: Long? = null
    var experience: Int? = null
    var groupId: Long? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
