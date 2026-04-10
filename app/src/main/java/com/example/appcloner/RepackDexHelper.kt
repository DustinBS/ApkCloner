package com.example.appcloner

import java.io.File

object RepackDexHelper {

    /**
     * Prototype: attempt to remap package references inside dex files by disassembling
     * to smali, performing textual replacements, and reassembling. Uses baksmali/smali
     * via reflection when available. This is a heavy-weight operation and should be
     * treated as experimental — it may fail for complex dex constructs.
     */
    private fun findBridgeClass(): Class<*>? {
        val candidates = listOf(
            "com.example.appcloner.dextools.TestSmaliBridge",
            "com.example.appcloner.dextools.SmaliBridge"
        )
        for (c in candidates) {
            try {
                return Class.forName(c)
            } catch (_: Throwable) {
                // try next candidate
            }
        }
        return null
    }
    fun remapDexUsingSmali(apkFile: File, tmpDir: File, oldPackage: String, newPackage: String): Boolean {
        try {
            if (!apkFile.exists()) return false

            val workspace = File(tmpDir, "dex_workspace")
            if (workspace.exists()) workspace.deleteRecursively()
            workspace.mkdirs()

            // extract dex files
            java.util.zip.ZipFile(apkFile).use { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val entry = en.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        val out = File(workspace, entry.name)
                        out.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { ins -> out.outputStream().use { outStream -> ins.copyTo(outStream) } }
                    }
                }
            }

            val dexDir = File(workspace, "dex")
            if (!dexDir.exists()) dexDir.mkdirs()
            workspace.listFiles()?.filter { it.name.endsWith(".dex") }?.forEach { it.renameTo(File(dexDir, it.name)) }

            val dexFiles = dexDir.listFiles()?.filter { it.name.endsWith(".dex") } ?: emptyList()
            for (dex in dexFiles) {
                val smaliOut = File(workspace, dex.name + ".smali")
                if (!smaliOut.exists()) smaliOut.mkdirs()

                // Disassemble via SmaliBridge wrapper provided by :dextools (shaded)
                try {
                    val bridgeClass = findBridgeClass() ?: run {
                        println("RepackDexHelper: no Smali bridge implementation found on classpath")
                        return false
                    }
                    val dis = bridgeClass.getMethod("disassemble", String::class.java, String::class.java)
                    val ok = dis.invoke(null, dex.absolutePath, smaliOut.absolutePath) as? Boolean ?: false
                    if (!ok) return false
                } catch (e: Throwable) {
                    e.printStackTrace()
                    return false
                }

                // Textual pass: replace type descriptors & plain package occurrences in .smali files
                val oldSlash = oldPackage.replace('.', '/')
                val newSlash = newPackage.replace('.', '/')
                smaliOut.walkTopDown().filter { it.isFile && it.extension == "smali" }.forEach { f ->
                    try {
                        val s = f.readText(Charsets.UTF_8)
                        val ns = s.replace(oldSlash, newSlash).replace(oldPackage, newPackage)
                        if (ns != s) f.writeText(ns, Charsets.UTF_8)
                    } catch (_: Exception) { }
                }

                // Reassemble using SmaliBridge wrapper
                try {
                    val bridgeClass = findBridgeClass() ?: run {
                        println("RepackDexHelper: no Smali bridge implementation found on classpath (assemble)")
                        return false
                    }
                    val asm = bridgeClass.getMethod("assemble", String::class.java, String::class.java)
                    val ok = asm.invoke(null, smaliOut.absolutePath, dex.absolutePath) as? Boolean ?: false
                    if (!ok) return false
                } catch (e: Throwable) {
                    e.printStackTrace()
                    return false
                }
            }

            // Repack APK with replaced dex files
            val outApk = File(apkFile.parentFile, "dex_remapped.apk")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(outApk)).use { zos ->
                java.util.zip.ZipFile(apkFile).use { zf ->
                    val en = zf.entries()
                    while (en.hasMoreElements()) {
                        val entry = en.nextElement()
                        zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (entry.name.endsWith(".dex")) {
                            val newDex = File(dexDir, File(entry.name).name)
                            if (newDex.exists()) newDex.inputStream().use { it.copyTo(zos) } else zf.getInputStream(entry).use { it.copyTo(zos) }
                        } else {
                            zf.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
