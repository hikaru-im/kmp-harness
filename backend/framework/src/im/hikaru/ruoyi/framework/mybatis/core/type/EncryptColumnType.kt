package im.hikaru.ruoyi.framework.mybatis.core.type

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 字段加密的 Exposed 列类型 (迁移自 MyBatis-Plus EncryptTypeHandler)
 *
 * 基于 AES 实现字段级加密，可通过构造时的 password 设置密钥。
 *
 * 迁移说明：原实现基于 Hutool SecureUtil.aes / SpringUtil.getProperty，
 * 现改为 JDK Cipher。password 由调用方从配置 `mybatis-plus.encryptor.password` 读取后注入。
 *
 * @author 芋道源码
 */
class EncryptColumnType(
    private val colLength: Int = 1024,
    private val password: String? = null,
) : ColumnType<String>() {

    override fun sqlType(): String = if (colLength > 0) "VARCHAR($colLength)" else "TEXT"

    override fun valueFromDB(value: Any): String = when (value) {
        is String -> decrypt(value)
        else -> error("$value (${value::class}) is not a valid value for EncryptColumnType")
    }

    override fun notNullValueToDB(value: String): Any = encrypt(value)

    override fun nonNullValueToString(value: String): String = "'${encrypt(value)}'"

    private fun encrypt(rawValue: String): String {
        val key = password ?: error("配置项 (mybatis-plus.encryptor.password) 不能为空")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(rawValue.toByteArray()))
    }

    private fun decrypt(value: String): String {
        val key = password ?: error("配置项 (mybatis-plus.encryptor.password) 不能为空")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(cipher.doFinal(Base64.getDecoder().decode(value)))
    }
}

/**
 * 创建加密字段列 (扩展函数)。
 *
 * @param password AES 密钥 (通常来自配置 `mybatis-plus.encryptor.password`)
 */
fun Table.encrypt(name: String, colLength: Int = 1024, password: String?): Column<String> =
    registerColumn(name, EncryptColumnType(colLength, password))
