package im.hikaru.ruoyi.module.system.api.dict

import im.hikaru.ruoyi.framework.common.biz.system.dict.DictDataCommonApi

interface DictDataApi : DictDataCommonApi {
    fun validateDictDataList(dictType: String, values: Collection<String>)
}
