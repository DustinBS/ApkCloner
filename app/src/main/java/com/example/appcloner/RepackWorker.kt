package com.example.appcloner

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.appcloner.util.NotificationUtils
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

class RepackWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_APK_PATH = "apk_path"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_TARGET_APP_NAME = "target_app_name"
        const val KEY_CUSTOM_URI = "custom_uri"
        const val KEY_ORIGINAL_PACKAGE = "original_package"
        const val KEY_OUTPUT_PATH = "output_path"

        private const val CHANNEL_ID = "appcloner_repack"
        private const val NOTIF_ID = 1001
        const val TAG_BASE = "repack"
        const val TAG_PREFIX = "repack:"
        const val UNIQUE_WORK_PREFIX = "repack_"
    }

    override suspend fun doWork(): Result {
        // Accept either a single APK path (`String`) or an array (`String[]`) for backwards compatibility
        val apkArray = inputData.getStringArray(KEY_APK_PATH)
        val apkSingle = inputData.getString(KEY_APK_PATH)
        val apkPaths = when {
            apkArray != null -> apkArray.toList()
            apkSingle != null -> listOf(apkSingle)
            else -> emptyList()
        }

        if (apkPaths.isEmpty()) return Result.failure(workDataOf("error" to "no_apk_paths"))

        val targetPackage = inputData.getString(KEY_TARGET_PACKAGE)
        val targetAppName = inputData.getString(KEY_TARGET_APP_NAME)
        val customUri = inputData.getString(KEY_CUSTOM_URI)
        val originalPackage = inputData.getString(KEY_ORIGINAL_PACKAGE) ?: "unknown.package"

        // Setup a foreground notification so long-running repacks show progress in the shade
        NotificationUtils.createChannel(applicationContext, CHANNEL_ID, "AppCloner Repack")

        val initialNotif = NotificationUtils.buildNotification(applicationContext, CHANNEL_ID, "Repack in progress", null, indeterminate = true, ongoing = true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForegroundAsync(ForegroundInfo(NOTIF_ID, initialNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } else {
                setForegroundAsync(ForegroundInfo(NOTIF_ID, initialNotif))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // Kick off progress and update notification throughout the operation
            setProgress(workDataOf("progress" to 5))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 5, "Starting")

            val filesDir = applicationContext.filesDir
            val cleanFileName = (targetPackage ?: "cloned").replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
            val outFile = File(filesDir, "$cleanFileName.apk")

            setProgress(workDataOf("progress" to 20))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 20, "Preparing resources (20%)")

            val success = RepackHelper.repack(apkPaths, outFile.absolutePath, targetPackage, targetAppName)
            if (!success) return Result.failure(workDataOf("error" to "repack_failed"))

            setProgress(workDataOf("progress" to 75))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 75, "Signing and finalizing (75%)")

            var finalPath = outFile.absolutePath
            if (!customUri.isNullOrEmpty()) {
                try {
                    val copied = com.example.appcloner.util.FileUtils.copyFileToTree(applicationContext, outFile, customUri)
                    if (!copied.isNullOrEmpty()) finalPath = copied
                } catch (e: Exception) {
                    // best-effort copy; ignore and keep internal file
                    e.printStackTrace()
                }
            }

            // Save to history DB
            try {
                val historyDao = AppDatabase.getDatabase(applicationContext).cloneHistoryDao()
                historyDao.insert(
                    CloneHistory(
                        originalPackageName = originalPackage,
                        clonedPackageName = targetPackage ?: "unknown",
                        appName = targetAppName ?: "Cloned App",
                        cloneDate = System.currentTimeMillis(),
                        version = "1.0",
                        outputPath = finalPath
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setProgress(workDataOf("progress" to 100))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 100, "Repack complete", finalText = "Saved to $finalPath", ongoing = false, smallIcon = android.R.drawable.stat_sys_download_done)

            return Result.success(workDataOf(KEY_OUTPUT_PATH to finalPath))
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 0, "Repack failed: ${e.message}", ongoing = false)
            return Result.failure(workDataOf("error" to (e.message ?: "repack_failed")))
        }
    }

}
