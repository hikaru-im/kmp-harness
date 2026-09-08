package im.hikaru.ruoyi.module.mp.service.menu

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuSaveReqVO
import im.hikaru.ruoyi.module.mp.convert.menu.MpMenuConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.menu.MpMenuDO
import im.hikaru.ruoyi.module.mp.dal.mysql.menu.MpMenuDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MENU_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MENU_SAVE_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.MpUtils
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import im.hikaru.ruoyi.module.mp.service.message.MpMessageService
import jakarta.validation.Validator
import me.chanjar.weixin.common.bean.menu.WxMenu
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpMenuServiceImpl(
    private val mpMessageService: MpMessageService,
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
    private val validator: Validator,
) : MpMenuService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()

    @Transactional(rollbackFor = [Exception::class])
    override fun saveMenu(createReqVO: MpMenuSaveReqVO) {
        val accountId = requireNotNull(createReqVO.accountId)
        val account = mpAccountService.getRequiredAccount(accountId)
        val menus = createReqVO.menus.orEmpty()
        menus.forEach(::validateMenu)
        try {
            mpServiceFactory.getRequiredMpService(accountId).menuService.menuCreate(
                WxMenu().apply { buttons = MpMenuConvert.convert(menus) },
            )
        } catch (ex: Exception) {
            throw exception(MENU_SAVE_FAIL, wxErrorMessage(ex))
        }
        MpMenuDao.deleteByAccountId(accountId)
        menus.forEach { menu ->
            val parent = createMenu(menu, null, account)
            menu.children.orEmpty().forEach { child -> createMenu(child, parent, account) }
        }
    }

    override fun deleteMenuByAccountId(accountId: Long) {
        try {
            mpServiceFactory.getRequiredMpService(accountId).menuService.menuDelete()
        } catch (ex: Exception) {
            throw exception(MENU_DELETE_FAIL, wxErrorMessage(ex))
        }
        MpMenuDao.deleteByAccountId(accountId)
    }

    override fun reply(appId: String, key: String, openid: String): WxMpXmlOutMessage? {
        val menu = MpMenuDao.selectByAppIdAndMenuKey(appId, key) ?: run {
            logger.warn("No menu found for appId={} and key={}", appId, key)
            return null
        }
        if (menu.replyMessageType.isNullOrBlank()) return null
        return mpMessageService.sendOutMessage(MpMenuConvert.convert(openid, menu))
    }

    override fun getMenuListByAccountId(accountId: Long): List<MpMenuDO> = MpMenuDao.selectListByAccountId(accountId)

    private fun validateMenu(menu: MpMenuSaveReqVO.Menu) {
        MpUtils.validateButton(validator, menu.type, menu.replyMessageType, menu)
        menu.children.orEmpty().forEach(::validateMenu)
    }

    private fun createMenu(source: MpMenuSaveReqVO.Menu, parent: MpMenuDO?, account: MpAccountDO): MpMenuDO {
        val menu = if (source.children.isNullOrEmpty()) MpMenuConvert.convertToEntity(source) else MpMenuDO().apply { name = source.name }
        menu.accountId = account.id
        menu.appId = account.appId
        menu.parentId = parent?.id ?: MpMenuDO.ID_ROOT
        MpMenuDao.insert(menu)
        return menu
    }

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
