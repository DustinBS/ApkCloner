package com.example.appcloner

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepackDexHelperTest {

    private fun createTestApk(entries: Map<String, ByteArray>): File {
        val tmp = File.createTempFile("dex_test_apk_", ".apk")
        if (tmp.exists()) tmp.delete()
        ZipOutputStream(FileOutputStream(tmp)).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return tmp
    }

    @Test
    fun testDexRemapUsesSmaliBridgeStub() {
        val oldPkg = "com.example.orig"
        val newPkg = "com.example.repacked"
        val entries = mapOf("classes.dex" to "DEX_PLACEHOLDER".toByteArray(Charsets.UTF_8))
        val apk = createTestApk(entries)
        val tmpDir = createTempDir(prefix = "dex_workspace_test_")
        try {
            val ok = RepackDexHelper.remapDexUsingSmali(apk, tmpDir, oldPkg, newPkg)
            assertTrue(ok)
            val remapped = File(apk.parentFile, "dex_remapped.apk")
            assertTrue(remapped.exists())
            java.util.zip.ZipFile(remapped).use { zf ->
                val e = zf.getEntry("classes.dex")
                assertNotNull(e)
                zf.getInputStream(e).use { ins ->
                    val data = ins.readBytes().toString(Charsets.UTF_8)
                    assertTrue("Expected new package in remapped dex", data.contains(newPkg) || data.contains(newPkg.replace('.', '/')))
                }
            }
        } finally {
            apk.delete()
            tmpDir.deleteRecursively()
            File(apk.parentFile, "dex_remapped.apk").delete()
        }
    }
}
