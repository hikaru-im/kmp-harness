package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2CodeDO
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2CodeDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CODE_EXPIRE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CODE_NOT_EXISTS
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Service
class OAuth2CodeServiceImpl : OAuth2CodeService {
    override fun createAuthorizationCode(
        userId: Long,
        userType: Int,
        clientId: String,
        scopes: List<String>,
        redirectUri: String?,
        state: String?,
    ): OAuth2CodeDO {
        val entity = OAuth2CodeDO().apply {
            code = UUID.randomUUID().toString().replace("-", "")
            this.userId = userId
            this.userType = userType
            this.clientId = clientId
            this.scopes = scopes
            expiresTime = Clock.System.now().plus(TIMEOUT_SECONDS.seconds).toLocalDateTime(TimeZone.currentSystemDefault())
            this.redirectUri = redirectUri
            this.state = state.orEmpty()
        }
        OAuth2CodeDao.insert(entity)
        return entity
    }

    override fun consumeAuthorizationCode(code: String): OAuth2CodeDO {
        val entity = OAuth2CodeDao.selectByCode(code) ?: throw exception(OAUTH2_CODE_NOT_EXISTS)
        if (entity.expiresTime?.let { it <= now() } != false) throw exception(OAUTH2_CODE_EXPIRE)
        OAuth2CodeDao.deleteById(requireNotNull(entity.id))
        return entity
    }

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    companion object {
        private const val TIMEOUT_SECONDS = 5 * 60
    }
}
