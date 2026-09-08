package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ApproveDO

interface OAuth2ApproveService {
    fun checkForPreApproval(userId: Long, userType: Int, clientId: String, requestedScopes: Collection<String>): Boolean
    fun updateAfterApproval(userId: Long, userType: Int, clientId: String, requestedScopes: Map<String, Boolean>): Boolean
    fun getApproveList(userId: Long, userType: Int, clientId: String): List<OAuth2ApproveDO>
}
