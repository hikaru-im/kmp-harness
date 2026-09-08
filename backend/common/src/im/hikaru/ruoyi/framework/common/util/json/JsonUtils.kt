package im.hikaru.ruoyi.framework.common.util.json

import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampLocalDateTimeDeserializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampLocalDateTimeSerializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampKotlinLocalDateTimeDeserializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampKotlinLocalDateTimeSerializer
import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.ArrayList
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime

/**
 * JSON 工具类
 *
 * 迁移说明（决策表 #11）：
 *  - Lombok (@Slf4j / @SneakyThrows / @Getter) → Kotlin 顶层 log + 显式 throws + 普通 var
 *  - Hutool StrUtil/ArrayUtil/JSONUtil → Kotlin stdlib / Jackson / 自实现
 *  - 保留 Jackson 作为底层 (Web 层 JSON 序列化)
 *
 * @author 芋道源码
 */
object JsonUtils {
    private val log = LoggerFactory.getLogger(JsonUtils::class.java)

    @JvmStatic
    var objectMapper: ObjectMapper = buildObjectMapper()
        private set

    private fun buildObjectMapper(): ObjectMapper {
        val simpleModule = SimpleModule()
            // 解决 LocalDateTime 的序列化
            .addSerializer(LocalDateTime::class.java, TimestampLocalDateTimeSerializer.INSTANCE)
            .addDeserializer(LocalDateTime::class.java, TimestampLocalDateTimeDeserializer.INSTANCE)
            .addSerializer(KotlinLocalDateTime::class.java, TimestampKotlinLocalDateTimeSerializer.INSTANCE)
            .addDeserializer(KotlinLocalDateTime::class.java, TimestampKotlinLocalDateTimeDeserializer.INSTANCE)
        return JsonMapper.builder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion {
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
            }
            .addModule(simpleModule)
            .build()
    }

    /**
     * 初始化 objectMapper 属性
     *
     * 通过这样的方式，使用 Spring 创建的 ObjectMapper Bean
     *
     * @param objectMapper ObjectMapper 对象
     */
    @JvmStatic
    fun init(objectMapper: ObjectMapper) {
        JsonUtils.objectMapper = objectMapper
    }

    @JvmStatic
    @Throws(JacksonException::class)
    fun toJsonString(o: Any?): String = objectMapper.writeValueAsString(o)

    @JvmStatic
    @Throws(JacksonException::class)
    fun toJsonByte(o: Any?): ByteArray = objectMapper.writeValueAsBytes(o)

    @JvmStatic
    @Throws(JacksonException::class)
    fun toJsonPrettyString(o: Any?): String =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o)

    @JvmStatic
    fun <T> parseObject(text: String?, clazz: Class<T>): T? {
        if (text.isNullOrEmpty()) return null
        return try {
            objectMapper.readValue(text, clazz)
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> parseObject(text: String?, path: String, clazz: Class<T>): T? {
        if (text.isNullOrEmpty()) return null
        return try {
            val treeNode = objectMapper.readTree(text)
            val pathNode = treeNode.path(path)
            objectMapper.readValue(pathNode.toString(), clazz)
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> parseObject(text: String?, type: Type): T? {
        if (text.isNullOrEmpty()) return null
        return try {
            objectMapper.readValue(text, objectMapper.typeFactory.constructType(type))
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> parseObject(text: ByteArray?, type: Type): T? {
        if (text == null || text.isEmpty()) return null
        return try {
            objectMapper.readValue(text, objectMapper.typeFactory.constructType(type))
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    /**
     * 将字符串解析成指定类型的对象
     * 使用 [parseObject] 时，在 @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) 的场景下，
     * 如果 text 没有 class 属性，则会报错。此时，使用这个方法，可以解决。
     *
     * 原实现走 Hutool JSONUtil.toBean，现统一走 Jackson 但忽略未知属性。
     *
     * @param text 字符串
     * @param clazz 类型
     * @return 对象
     */
    @JvmStatic
    fun <T> parseObject2(text: String?, clazz: Class<T>): T? {
        if (text.isNullOrEmpty()) return null
        return objectMapper.readValue(text, clazz)
    }

    @JvmStatic
    fun <T> parseObject(bytes: ByteArray?, clazz: Class<T>): T? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            objectMapper.readValue(bytes, clazz)
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", bytes, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> parseObject(text: String?, typeReference: TypeReference<T>): T? = try {
        objectMapper.readValue(text, typeReference)
    } catch (e: JacksonException) {
        log.error("json parse err,json:{}", text, e)
        throw RuntimeException(e)
    }

    /**
     * 解析 JSON 字符串成指定类型的对象，如果解析失败，则返回 null
     *
     * @param text 字符串
     * @param typeReference 类型引用
     * @return 指定类型的对象
     */
    @JvmStatic
    fun <T> parseObjectQuietly(text: String?, typeReference: TypeReference<T>): T? = try {
        objectMapper.readValue(text, typeReference)
    } catch (e: JacksonException) {
        null
    }

    /**
     * 解析 JSON 字符串成 Map，空字符串或解析失败返回 null
     *
     * @param text JSON 字符串
     * @return Map 对象
     */
    @JvmStatic
    fun parseMap(text: String?): Map<String, Any?>? {
        if (text.isNullOrEmpty()) return null
        return try {
            objectMapper.readValue(text, object : TypeReference<Map<String, Any?>>() {})
        } catch (e: JacksonException) {
            null
        }
    }

    /**
     * 解析 JSON 字符串成指定类型的对象，如果解析失败，则返回 null
     *
     * @param text 字符串
     * @param clazz 类型
     * @return 指定类型的对象
     */
    @JvmStatic
    fun <T> parseObjectQuietly(text: String?, clazz: Class<T>): T? {
        if (text.isNullOrEmpty()) return null
        return try {
            objectMapper.readValue(text, clazz)
        } catch (e: JacksonException) {
            null
        }
    }

    @JvmStatic
    fun <T> parseArray(text: String?, clazz: Class<T>): List<T> {
        if (text.isNullOrEmpty()) return ArrayList()
        return try {
            objectMapper.readValue(text, objectMapper.typeFactory.constructCollectionType(List::class.java, clazz))
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> parseArray(text: String?, path: String, clazz: Class<T>): List<T>? {
        if (text.isNullOrEmpty()) return null
        return try {
            val treeNode = objectMapper.readTree(text)
            val pathNode = treeNode.path(path)
            objectMapper.readValue(
                pathNode.toString(),
                objectMapper.typeFactory.constructCollectionType(List::class.java, clazz),
            )
        } catch (e: JacksonException) {
            log.error("json parse err,json:{}", text, e)
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun parseTree(text: String?): JsonNode = try {
        objectMapper.readTree(text)
    } catch (e: JacksonException) {
        log.error("json parse err,json:{}", text, e)
        throw RuntimeException(e)
    }

    @JvmStatic
    fun parseTree(text: ByteArray?): JsonNode = try {
        objectMapper.readTree(text)
    } catch (e: JacksonException) {
        log.error("json parse err,json:{}", text, e)
        throw RuntimeException(e)
    }

    @JvmStatic
    fun getText(node: JsonNode?, fieldName: String): String? {
        if (node == null) return null
        val value = node.get(fieldName)
        return if (value != null && !value.isNull) value.asText() else null
    }

    @JvmStatic
    fun isJson(text: String?): Boolean {
        // 原 Hutool JSONUtil.isTypeJSON：判断是否以 { 或 [ 开头 (trim 后)
        if (text == null) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return trimmed[0] == '{' || trimmed[0] == '['
    }

    /**
     * 判断字符串是否为 JSON 对象类型的字符串
     *
     * @param str 字符串
     */
    @JvmStatic
    fun isJsonObject(str: String?): Boolean {
        // 原 Hutool JSONUtil.isTypeJSONObject：仅判断是否以 { 开头 (trim 后)
        if (str == null) return false
        val trimmed = str.trim()
        return trimmed.startsWith("{")
    }

    /**
     * 将 Object 转换为目标类型
     *
     * 避免先转 jsonString 再 parseObject 的性能损耗
     *
     * @param obj 源对象（可以是 Map、POJO 等）
     * @param clazz 目标类型
     * @return 转换后的对象
     */
    @JvmStatic
    fun <T> convertObject(obj: Any?, clazz: Class<T>): T? {
        if (obj == null) return null
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj)
        }
        return objectMapper.convertValue(obj, clazz)
    }

    /**
     * 将 Object 转换为目标类型（支持泛型）
     *
     * @param obj 源对象
     * @param typeReference 目标类型引用
     * @return 转换后的对象
     */
    @JvmStatic
    fun <T> convertObject(obj: Any?, typeReference: TypeReference<T>): T? {
        if (obj == null) return null
        return objectMapper.convertValue(obj, typeReference)
    }

    /**
     * 将 Object 转换为 List 类型
     *
     * 避免先转 jsonString 再 parseArray 的性能损耗
     *
     * @param obj 源对象（可以是 List、数组等）
     * @param clazz 目标元素类型
     * @return 转换后的 List
     */
    @JvmStatic
    fun <T> convertList(obj: Any?, clazz: Class<T>): List<T> {
        if (obj == null) return ArrayList()
        return objectMapper.convertValue(
            obj,
            objectMapper.typeFactory.constructCollectionType(List::class.java, clazz),
        )
    }
}
