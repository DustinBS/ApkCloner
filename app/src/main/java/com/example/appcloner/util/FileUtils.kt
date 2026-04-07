package com.example.appcloner.util

import java.io.File
import java.io.IOException
import java.io.InputStream

object FileUtils {
    @Throws(IOException::class)
    fun copyStreamToFile(input: InputStream, outFile: File) {
        outFile.outputStream().use { output ->
            input.use { inputStream ->
                inputStream.copyTo(output)
            }
        }
    }

    @Throws(IOException::class)
    fun copyFile(src: File, dst: File) {
        src.inputStream().use { input ->
            dst.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
