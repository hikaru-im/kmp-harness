package im.hikaru.ruoyi.framework.common.util.io

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID

object FileUtils {
    @JvmStatic fun createTempFile(data: String): File = createTempFile().also {
        Files.writeString(it.toPath(), data, StandardCharsets.UTF_8)
    }
    @JvmStatic fun createTempFile(data: ByteArray): File = createTempFile().also {
        Files.write(it.toPath(), data)
    }
    @JvmStatic fun createTempFile(): File = File.createTempFile(UUID.randomUUID().toString().replace("-", ""), null).also(File::deleteOnExit)
}
