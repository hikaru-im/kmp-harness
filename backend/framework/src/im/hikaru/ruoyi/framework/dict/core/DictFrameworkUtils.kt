package im.hikaru.ruoyi.framework.dict.core

import im.hikaru.ruoyi.framework.common.biz.system.dict.DictDataCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.dict.dto.DictDataRespDTO
import im.hikaru.ruoyi.framework.common.util.cache.CacheUtils
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.LoadingCache
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Objects

/**
 * 字典工具类 (迁移自 Java, 去 Lombok/Hutool, Guava Cache → Caffeine)
 *
 * 迁移说明：
 *  - Hutool CollUtil.findOne → Kotlin find
 *  - Lombok @SneakyThrows → 显式 try/catch
 *  - Guava LoadingCache → Caffeine LoadingCache
 *
 * @author 芋道源码
 */
class DictFrameworkUtils {

    companion object {
        private val log = LoggerFactory.getLogger(DictFrameworkUtils::class.java)

        private lateinit var dictDataApi: DictDataCommonApi

        /**
         * 针对 dictType 的字段数据缓存
         */
        private val GET_DICT_DATA_CACHE: LoadingCache<String, List<DictDataRespDTO>> =
            CacheUtils.buildAsyncReloadingCache(
                Duration.ofMinutes(1L),
                CacheLoader { dictType: String -> dictDataApi.getDictDataList(dictType) },
            )

        fun init(dictDataApi: DictDataCommonApi) {
            this.dictDataApi = dictDataApi
            log.info("[init][初始化 DictFrameworkUtils 成功]")
        }

        fun clearCache() {
            GET_DICT_DATA_CACHE.invalidateAll()
        }

        @JvmStatic
        fun parseDictDataLabel(dictType: String, value: Int?): String? {
            if (value == null) return null
            return parseDictDataLabel(dictType, value.toString())
        }

        @JvmStatic
        fun parseDictDataLabel(dictType: String, value: String): String? {
            val dictDatas: List<DictDataRespDTO> = GET_DICT_DATA_CACHE.get(dictType)
            val dictData = dictDatas.find { e: DictDataRespDTO -> Objects.equals(e.value, value) }
            return dictData?.label
        }

        @JvmStatic
        fun getDictDataLabelList(dictType: String): List<String> {
            val dictDatas: List<DictDataRespDTO> = GET_DICT_DATA_CACHE.get(dictType)
            return dictDatas.map { e: DictDataRespDTO -> e.label!! }
        }

        @JvmStatic
        fun parseDictDataValue(dictType: String, label: String?): String? {
            val dictDatas: List<DictDataRespDTO> = GET_DICT_DATA_CACHE.get(dictType)
            val dictData = dictDatas.find { e: DictDataRespDTO -> Objects.equals(e.label, label) }
            return dictData?.value
        }

        @JvmStatic
        fun getDictDataValueList(dictType: String): List<String> {
            val dictDatas: List<DictDataRespDTO> = GET_DICT_DATA_CACHE.get(dictType)
            return dictDatas.map { e: DictDataRespDTO -> e.value!! }
        }
    }
}
