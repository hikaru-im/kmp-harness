package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO

interface MailAccountService {
    fun createMailAccount(req: MailAccountSaveReqVO): Long
    fun updateMailAccount(req: MailAccountSaveReqVO)
    fun deleteMailAccount(id: Long)
    fun deleteMailAccountList(ids: Collection<Long>)
    fun getMailAccount(id: Long): MailAccountDO?
    fun getMailAccountFromCache(id: Long): MailAccountDO?
    fun getMailAccountPage(req: MailAccountPageReqVO): PageResult<MailAccountDO>
    fun getMailAccountList(): List<MailAccountDO>
}
