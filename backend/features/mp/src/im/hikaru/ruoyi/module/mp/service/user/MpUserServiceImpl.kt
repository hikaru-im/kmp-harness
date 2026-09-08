package im.hikaru.ruoyi.module.mp.service.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserUpdateReqVO
import im.hikaru.ruoyi.module.mp.convert.user.MpUserConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import im.hikaru.ruoyi.module.mp.dal.mysql.user.MpUserDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.USER_UPDATE_TAG_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.bean.result.WxMpUser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpUserServiceImpl(
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
) : MpUserService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()

    override fun getUser(id: Long): MpUserDO? = MpUserDao.selectById(id)
    override fun getUser(appId: String, openId: String): MpUserDO? = MpUserDao.selectByAppIdAndOpenid(appId, openId)
    override fun getUserList(ids: Collection<Long>): List<MpUserDO> = MpUserDao.selectByIds(ids)
    override fun getUserPage(pageReqVO: MpUserPageReqVO): PageResult<MpUserDO> = MpUserDao.selectPage(pageReqVO)

    override fun saveUser(appId: String, wxMpUser: WxMpUser): MpUserDO {
        val user = MpUserConvert.convert(mpAccountService.getAccountFromCache(appId), wxMpUser)
        val existing = MpUserDao.selectByAppIdAndOpenid(appId, requireNotNull(wxMpUser.openId))
        if (existing == null) {
            MpUserDao.insert(user)
        } else {
            user.id = existing.id
            MpUserDao.updateById(user)
        }
        return user
    }

    @Async
    override fun syncUser(accountId: Long) {
        val account = mpAccountService.getRequiredAccount(accountId)
        var nextOpenid: String? = null
        repeat(Short.MAX_VALUE.toInt()) { index ->
            try {
                nextOpenid = syncUserPage(accountId, requireNotNull(account.appId), nextOpenid)
            } catch (ex: Exception) {
                logger.error("Failed to synchronize WeChat users on page {}", index, ex)
                return
            }
            if (nextOpenid.isNullOrEmpty()) return
        }
    }

    private fun syncUserPage(accountId: Long, appId: String, nextOpenid: String?): String? {
        val service = mpServiceFactory.getRequiredMpService(accountId)
        val userList = service.userService.userList(nextOpenid)
        val openids = userList.openids.orEmpty()
        if (openids.isEmpty()) return null
        openids.chunked(100).forEach { batch ->
            val wxUsers = service.userService.userInfoList(batch).orEmpty()
            val existing = MpUserDao.selectListByAppIdAndOpenid(appId, wxUsers.mapNotNull { it.openId })
                .associateBy { it.openid }
            wxUsers.forEach { wxUser ->
                val user = MpUserConvert.convert(mpAccountService.getAccountFromCache(appId), wxUser)
                val dbUser = existing[user.openid]
                if (dbUser == null) {
                    MpUserDao.insert(user)
                } else {
                    user.id = dbUser.id
                    MpUserDao.updateById(user)
                }
            }
        }
        return userList.nextOpenid
    }

    override fun updateUserUnsubscribe(appId: String, openId: String) {
        val user = MpUserDao.selectByAppIdAndOpenid(appId, openId) ?: return
        MpUserDao.updateById(MpUserDO().apply {
            id = user.id
            subscribeStatus = CommonStatusEnum.DISABLE.status
            unsubscribeTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        })
    }

    override fun updateUser(updateReqVO: MpUserUpdateReqVO) {
        val user = MpUserDao.selectById(requireNotNull(updateReqVO.id)) ?: throw exception(USER_NOT_EXISTS)
        updateUserTag(requireNotNull(user.appId), requireNotNull(user.openid), updateReqVO.tagIds.orEmpty())
        MpUserDao.updateById(MpUserConvert.convert(updateReqVO).apply { id = user.id })
    }

    private fun updateUserTag(appId: String, openid: String, tagIds: List<Long>) {
        try {
            val tagService = mpServiceFactory.getRequiredMpService(appId).userTagService
            tagService.userTagList(openid).orEmpty().forEach { tagService.batchUntagging(it, arrayOf(openid)) }
            tagIds.forEach { tagService.batchTagging(it, arrayOf(openid)) }
        } catch (ex: Exception) {
            val message = (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
            throw exception(USER_UPDATE_TAG_FAIL, message)
        }
    }
}
