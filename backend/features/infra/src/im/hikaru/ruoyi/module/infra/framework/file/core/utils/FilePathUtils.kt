package im.hikaru.ruoyi.module.infra.framework.file.core.utils

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_PATH_INVALID
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object FilePathUtils {
    @JvmStatic
    fun validateFileName(name: String?): String? {
        if (name.isNullOrEmpty()) return name
        if (!isPathValid(name) || name.contains('/') || Paths.get(name).fileName.toString() != name) {
            throw exception(FILE_PATH_INVALID)
        }
        return name
    }

    @JvmStatic
    fun isDirectoryValid(directory: String?): Boolean = directory.isNullOrEmpty() || isPathValid(directory)

    @JvmStatic
    fun validateDirectory(directory: String?) {
        if (!isDirectoryValid(directory)) throw exception(FILE_PATH_INVALID)
    }

    @JvmStatic
    fun validatePath(path: String?) {
        if (path.isNullOrEmpty() || !isPathValid(path)) throw exception(FILE_PATH_INVALID)
    }

    private fun isPathValid(path: String): Boolean {
        if (path.startsWith('/') || path.startsWith('\\') || path.contains('\\') || path.indexOf('\u0000') >= 0) return false
        if (path.length >= 2 && path[0].isLetter() && path[1] == ':') return false
        try {
            if (Paths.get(path).isAbsolute) return false
        } catch (_: InvalidPathException) {
            return false
        }
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
