package com.example.appcloner

import android.content.Context
import java.io.File

object RepackUtils {
    private val CLEAN_NAME_REGEX = Regex("[^a-zA-Z0-9.\\-]")
    const val DEEP_UNPACK_DIR = "deep_unpacks"
    const val WORKSPACE_PREFIX = "workspace/"
    const val TEMP_PREFIX = "appcloner_src"

    fun cleanFileName(pkg: String?): String {
        val base = pkg ?: "cloned"
        return base.replace(CLEAN_NAME_REGEX, "_")
    }

    fun deepUnpackDir(context: Context, cleanName: String): File {
        return File(File(context.filesDir, DEEP_UNPACK_DIR), cleanName)
    }

    fun createTempApkFile(cacheDir: File, prefix: String = TEMP_PREFIX): File {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val tmpPath = kotlin.io.path.createTempDirectory(cacheDir.toPath(), prefix)
            tmpPath.resolve("src.apk").toFile()
        } else {
            @Suppress("DEPRECATION")
            val tmpDir = kotlin.io.createTempDir(prefix, null, cacheDir)
            File(tmpDir, "src.apk")
        }
    }
}
