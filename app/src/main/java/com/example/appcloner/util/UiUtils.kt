package com.example.appcloner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File

object UiUtils {
    fun showSnack(anchor: View?, message: String, length: Int = Snackbar.LENGTH_SHORT) {
        anchor?.let { Snackbar.make(it, message, length).show() }
    }

    /**
     * Attempt to open an output path which may be a content:// URI or a file path.
     * Returns true if an activity was launched.
     */
    fun openOutput(context: Context, output: String?, fallbackFolderUri: String?, anchor: View? = null): Boolean {
        if (output.isNullOrEmpty()) {
            showSnack(anchor, "No output available")
            return false
        }
        try {
            if (output.startsWith("content://")) {
                val outUri = Uri.parse(output)
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(outUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (openIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(openIntent)
                    return true
                }

                // fallback to viewing configured folder
                if (!fallbackFolderUri.isNullOrEmpty()) {
                    val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                        val parsedUri = Uri.parse(fallbackFolderUri)
                        setDataAndType(parsedUri, "vnd.android.document/directory")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    if (folderIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(folderIntent)
                        return true
                    }
                    showSnack(anchor, "No app can view the output URI.")
                    return false
                }

                showSnack(anchor, "Cannot open cloned APK directly.")
                return false
            } else {
                val file = if (output.startsWith("file://")) File(Uri.parse(output).path!!) else File(output)
                if (!file.exists()) {
                    showSnack(anchor, "Output file not found.")
                    return false
                }
                val authority = "${context.packageName}.provider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
                showSnack(anchor, "No app to open APK.")
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showSnack(anchor, "Cannot open output: ${e.message}")
            return false
        }
    }

    /**
     * Open a configured folder URI (DocumentTree) in an external app if possible.
     */
    fun openFolder(context: Context, folderUriString: String?, anchor: View? = null): Boolean {
        if (folderUriString.isNullOrEmpty()) {
            showSnack(anchor, "No folder configured")
            return false
        }
        try {
            val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                val parsedUri = Uri.parse(folderUriString)
                setDataAndType(parsedUri, "vnd.android.document/directory")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (folderIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(folderIntent)
                return true
            }
            showSnack(anchor, "No app can view the configured folder.")
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            showSnack(anchor, "Cannot open folder: ${e.message}")
            return false
        }
    }
}
