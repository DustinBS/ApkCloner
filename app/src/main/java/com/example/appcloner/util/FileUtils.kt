package com.example.appcloner.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.InputStream

object FileUtils {
    /**
     * Copies [source] into the document tree pointed to by [treeUriString].
     * Returns the destination Uri string on success, or null on failure.
     */
    fun copyFileToTree(context: Context, source: File, treeUriString: String): String? {
        try {
            val treeUri = Uri.parse(treeUriString)
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            if (tree != null && tree.exists()) {
                val newDFile = tree.createFile("application/vnd.android.package-archive", source.name)
                if (newDFile != null) {
                    context.contentResolver.openOutputStream(newDFile.uri)?.use { outStream ->
                        source.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    return newDFile.uri.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

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

    /**
     * Return a human-friendly display name for a saved output path (content:// or filesystem path).
     */
    fun getDisplayName(context: Context, path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                doc?.name ?: uri.lastPathSegment
            } else {
                val f = File(path)
                if (f.exists()) f.name else path.substringAfterLast('/')
            }
        } catch (e: Exception) {
            e.printStackTrace()
            path
        }
    }
}
