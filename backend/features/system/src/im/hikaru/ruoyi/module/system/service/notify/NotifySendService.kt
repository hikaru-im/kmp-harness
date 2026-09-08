package im.hikaru.ruoyi.module.system.service.notify

interface NotifySendService {
    fun sendSingleNotifyToAdmin(userId: Long, templateCode: String, templateParams: Map<String, Any?>?): Long?
    fun sendSingleNotifyToMember(userId: Long, templateCode: String, templateParams: Map<String, Any?>?): Long?
    fun sendSingleNotify(userId: Long, userType: Int, templateCode: String, templateParams: Map<String, Any?>?): Long?

    fun sendBatchNotify(
        mobiles: List<String>,
        userIds: List<Long>,
        userType: Int,
        templateCode: String,
        templateParams: Map<String, Any?>?,
    ): Unit = throw UnsupportedOperationException("Batch notification sending is not supported yet")
}
