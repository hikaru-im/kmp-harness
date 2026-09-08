package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ApproveDO
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ApproveDao
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Service
class OAuth2ApproveServiceImpl(private val oauth2ClientService: OAuth2ClientService) : OAuth2ApproveService {
    override fun checkForPreApproval(
        userId: Long,
        userType: Int,
        clientId: String,
        requestedScopes: Collection<String>,
    ): Boolean {
        val client = oauth2ClientService.validOAuthClientFromCache(clientId)
        if (client.autoApproveScopes.orEmpty().containsAll(requestedScopes)) {
            val expiresTime = expiresTime()
            requestedScopes.forEach { saveApprove(userId, userType, clientId, it, true, expiresTime) }
            return true
        }
        val approvedScopes = getApproveList(userId, userType, clientId)
            .filter { it.approved == true }
            .mapNotNull { it.scope }
            .toSet()
        return approvedScopes.containsAll(requestedScopes)
    }

    override fun updateAfterApproval(
        userId: Long,
        userType: Int,
        clientId: String,
        requestedScopes: Map<String, Boolean>,
    ): Boolean {
        if (requestedScopes.isEmpty()) return true
        val expiresTime = expiresTime()
        requestedScopes.forEach { (scope, approved) ->
            saveApprove(userId, userType, clientId, scope, approved, expiresTime)
        }
        return requestedScopes.values.any { it }
    }

    override fun getApproveList(userId: Long, userType: Int, clientId: String): List<OAuth2ApproveDO> =
        OAuth2ApproveDao.selectListByUserIdAndUserTypeAndClientId(userId, userType, clientId)
            .filter { it.expiresTime?.let { expires -> expires > now() } == true }

    internal fun saveApprove(
        userId: Long,
        userType: Int,
        clientId: String,
        scope: String,
        approved: Boolean,
        expiresTime: kotlinx.datetime.LocalDateTime,
    ) {
        val entity = OAuth2ApproveDO().apply {
            this.userId = userId
            this.userType = userType
            this.clientId = clientId
            this.scope = scope
            this.approved = approved
            this.expiresTime = expiresTime
        }
        if (OAuth2ApproveDao.update(entity) == 0) OAuth2ApproveDao.insert(entity)
    }

    private fun expiresTime() =
        Clock.System.now().plus(TIMEOUT_SECONDS.seconds).toLocalDateTime(TimeZone.currentSystemDefault())

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    companion object {
        private const val TIMEOUT_SECONDS = 30 * 24 * 60 * 60
    }
}
