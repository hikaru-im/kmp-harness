package im.hikaru.ruoyi.module.system.api.dict

import im.hikaru.ruoyi.framework.common.biz.system.dict.dto.DictDataRespDTO
import im.hikaru.ruoyi.module.system.service.dict.DictDataService
import org.springframework.stereotype.Service

@Service
class DictDataApiImpl(
    private val dictDataService: DictDataService,
) : DictDataApi {
    override fun validateDictDataList(dictType: String, values: Collection<String>) {
        dictDataService.validateDictDataList(dictType, values)
    }

    override fun getDictDataList(dictType: String): List<DictDataRespDTO> =
        dictDataService.getDictDataListByDictType(dictType).map {
            DictDataRespDTO().apply {
                label = it.label
                value = it.value
                this.dictType = it.dictType
                status = it.status
            }
        }
}
