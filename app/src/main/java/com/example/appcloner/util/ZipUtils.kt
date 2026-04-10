package com.example.appcloner.util

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun addFileToZip(zos: ZipOutputStream, entryName: String, file: File) {
        try {
            if (file.isDirectory) {
                val dirEntry = if (entryName.endsWith("/")) entryName else "$entryName/"
                zos.putNextEntry(ZipEntry(dirEntry))
                zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addStreamToZip(zos: ZipOutputStream, entryName: String, ins: InputStream) {
        try {
            zos.putNextEntry(ZipEntry(entryName))
            ins.use { it.copyTo(zos) }
            zos.closeEntry()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addTextEntryToZip(zos: ZipOutputStream, entryName: String, text: String) {
        try {
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(text.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addBytesEntryToZip(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        try {
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(data)
            zos.closeEntry()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
