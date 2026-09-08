package im.hikaru.ruoyi.module.infra.api.file

import jakarta.validation.constraints.NotEmpty

/**
 * 文件 API 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface FileApi {

    /**
     * 保存文件，并返回文件的访问路径
     */
    fun createFile(content: ByteArray): String = createFile(content, null, null, null)

    /**
     * 保存文件，并返回文件的访问路径
     */
    fun createFile(content: ByteArray, name: String?): String = createFile(content, name, null, null)

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @param directory 目录，允许空
     * @param type 文件的 MIME 类型，允许空
     * @return 文件路径
     */
    fun createFile(
        @NotEmpty(message = "文件内容不能为空") content: ByteArray,
        name: String?,
        directory: String?,
        type: String?,
    ): String

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    fun presignGetUrl(
        @NotEmpty(message = "URL 不能为空") url: String,
        expirationSeconds: Int?,
    ): String
}
