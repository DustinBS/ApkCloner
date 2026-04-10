package com.example.appcloner

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepackHelperUnitTest {

    private fun createTestApkWithEntries(entries: Map<String, ByteArray>): File {
        val tmp = File.createTempFile("test_apk_", ".apk")
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
    fun detectPotentialIssues_detectsGoogleServicesAndPlainText() {
        val oldPkg = "com.example.orig"
        val googleJson = """{"project_info":{"package_name":"$oldPkg"}}"""
        val dexLike = "SomeBinary L${oldPkg.replace('.', '/')}/ some $oldPkg".toByteArray(Charsets.UTF_8)
        val entries = mapOf(
            "assets/google-services.json" to googleJson.toByteArray(Charsets.UTF_8),
            "classes.dex" to dexLike
        )
        val apk = createTestApkWithEntries(entries)
        try {
            val warnings = RepackHelper.detectPotentialIssues(apk, oldPkg)
            println("detectPotentialIssues warnings: $warnings")
            assertTrue("Expected at least one warning or detection", warnings.isNotEmpty())
        } finally {
            apk.delete()
        }
    }

    @Test
    fun unpackApkToWorkspace_extractsEntries() {
        val entries = mapOf(
            "assets/foo.txt" to "hello".toByteArray(),
            "res/values/strings.xml" to "<resources><string name=\"app_name\">Test</string></resources>".toByteArray()
        )
        val apk = createTestApkWithEntries(entries)
        val tmpdir = createTempDir(prefix = "workspace_test_")
        try {
            val ok = RepackHelper.unpackApkToWorkspace(apk.absolutePath, tmpdir)
            assertTrue(ok)
            assertTrue(File(tmpdir, "assets/foo.txt").exists())
            assertTrue(File(tmpdir, "res/values/strings.xml").exists())
        } finally {
            apk.delete()
            tmpdir.deleteRecursively()
        }
    }

    @Test
    fun detectIssuesInWorkspace_findsGoogleJsonAndPlainText() {
        val oldPkg = "com.example.orig"
        val tmpdir = createTempDir(prefix = "workspace_test2_")
        try {
            val assetsDir = File(tmpdir, "assets")
            assetsDir.mkdirs()
            File(assetsDir, "google-services.json").writeText("{" + "\"project_info\":{\"package_name\":\"$oldPkg\"}}")

            val rawFile = File(tmpdir, "res/raw/data.txt")
            rawFile.parentFile.mkdirs()
            rawFile.writeText("This references $oldPkg in a string")

            val warns = RepackHelper.detectIssuesInWorkspace(tmpdir, oldPkg)
            println("detectIssuesInWorkspace warnings: $warns")
            assertTrue("Expected workspace detector to find at least one warning", warns.isNotEmpty())
        } finally {
            tmpdir.deleteRecursively()
        }
    }
}
