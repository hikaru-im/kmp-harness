package im.hikaru.ruoyi.module.infra.service.codegen

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenCreateListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenUpdateReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTablePageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.DatabaseTableRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenColumnDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenTableDO

interface CodegenService {
    fun createCodegenList(author: String, reqVO: CodegenCreateListReqVO): List<Long>
    fun updateCodegen(updateReqVO: CodegenUpdateReqVO)
    fun syncCodegenFromDB(tableId: Long)
    fun deleteCodegen(tableId: Long)
    fun deleteCodegenList(tableIds: List<Long>)
    fun getCodegenTableList(dataSourceConfigId: Long): List<CodegenTableDO>
    fun getCodegenTablePage(pageReqVO: CodegenTablePageReqVO): PageResult<CodegenTableDO>
    fun getCodegenTable(id: Long): CodegenTableDO?
    fun getCodegenColumnListByTableId(tableId: Long): List<CodegenColumnDO>
    fun generationCodes(tableId: Long): Map<String, String>
    fun getDatabaseTableList(dataSourceConfigId: Long, name: String?, comment: String?): List<DatabaseTableRespVO>
}
