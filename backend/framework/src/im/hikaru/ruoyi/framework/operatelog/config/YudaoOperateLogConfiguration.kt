package im.hikaru.ruoyi.framework.operatelog.config

import im.hikaru.ruoyi.framework.common.biz.system.logger.OperateLogCommonApi
import im.hikaru.ruoyi.framework.operatelog.core.service.LogRecordServiceImpl
import com.mzt.logapi.service.ILogRecordService
import com.mzt.logapi.starter.annotation.EnableLogRecord
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Enables BizLog and routes operation records to the system module. */
@EnableLogRecord(tenant = "")
@AutoConfiguration
class YudaoOperateLogConfiguration {

    @Bean
    @Primary
    fun logRecordService(operateLogApi: OperateLogCommonApi): ILogRecordService =
        LogRecordServiceImpl(operateLogApi)
}
