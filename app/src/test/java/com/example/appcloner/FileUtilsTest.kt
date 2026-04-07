package com.example.appcloner

import com.example.appcloner.util.FileUtils
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.io.ByteArrayInputStream


class FileUtilsTest {
    @Test
    fun testCopyFile() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val src = File(tempDir, "fileutils_test_src.txt")
        val dst = File(tempDir, "fileutils_test_dst.txt")
        src.writeText("hello")
        if (dst.exists()) dst.delete()
        FileUtils.copyFile(src, dst)
        assertTrue(dst.exists())
        assertEquals("hello", dst.readText())
        src.delete()
        dst.delete()
    }

    @Test
    fun testCopyStreamToFile() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val dst = File(tempDir, "fileutils_test_stream_dst.txt")
        if (dst.exists()) dst.delete()
        val data = "stream-data".toByteArray()
        val input = ByteArrayInputStream(data)
        FileUtils.copyStreamToFile(input, dst)
        assertTrue(dst.exists())
        assertEquals("stream-data", dst.readText())
        dst.delete()
    }
}
