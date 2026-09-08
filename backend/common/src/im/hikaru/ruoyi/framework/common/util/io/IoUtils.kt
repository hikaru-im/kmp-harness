package im.hikaru.ruoyi.framework.common.util.io

import java.io.InputStream
import java.nio.charset.StandardCharsets

object IoUtils {
    @JvmStatic
    fun readUtf8(input: InputStream, isClose: Boolean): String = try {
        String(input.readBytes(), StandardCharsets.UTF_8)
    } finally {
        if (isClose) input.close()
    }
}
