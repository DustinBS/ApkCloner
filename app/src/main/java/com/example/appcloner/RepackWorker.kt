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
        const val KEY_DEEP_UNPACK = "deep_unpack"
        const val KEY_WORKSPACE_PATH = "workspace_path"
        const val KEY_AUTO_FIX = "auto_fix"
        const val KEY_PROGRESS = "progress"

        private const val CHANNEL_ID = "appcloner_repack"
        private const val NOTIF_ID = 1001
        const val TAG_BASE = "repack"
        const val TAG_PREFIX = "repack:"
        const val UNIQUE_WORK_PREFIX = "repack_"
        const val KEY_WARNINGS = "repack_warnings"
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
        val deepUnpack = inputData.getBoolean(KEY_DEEP_UNPACK, false)
        val autoFix = inputData.getBoolean(KEY_AUTO_FIX, false)

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
            setProgress(workDataOf(KEY_PROGRESS to 5))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 5, "Starting")

            val filesDir = applicationContext.filesDir
            val cleanFileName = RepackUtils.cleanFileName(targetPackage)
            val outFile = File(filesDir, "$cleanFileName.apk")

            setProgress(workDataOf(KEY_PROGRESS to 20))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 20, "Preparing resources (20%)")

            var workspacePath: String? = null
            var workspaceWarnings = emptyList<String>()
            if (deepUnpack) {
                try {
                    setProgress(workDataOf(KEY_PROGRESS to 10))
                    NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 10, "Unpacking APK(s) for deep analysis")
                    val workspaceDir = RepackUtils.deepUnpackDir(applicationContext, cleanFileName)
                    if (workspaceDir.exists()) workspaceDir.deleteRecursively()
                    workspaceDir.mkdirs()
                    for (p in apkPaths) {
                        try {
                            RepackHelper.unpackApkToWorkspace(p, workspaceDir)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    workspacePath = workspaceDir.absolutePath
                    // quick workspace scan
                    workspaceWarnings = try { RepackHelper.detectIssuesInWorkspace(workspaceDir, originalPackage) } catch (e: Exception) { emptyList() }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val success = RepackHelper.repack(apkPaths, outFile.absolutePath, targetPackage, targetAppName, autoFix, workspacePath)
            if (!success) return Result.failure(workDataOf("error" to "repack_failed"))

            setProgress(workDataOf(KEY_PROGRESS to 75))
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

            // Note: history insertion is deferred until after analysis so we can persist warnings

            setProgress(workDataOf(KEY_PROGRESS to 100))
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 100, "Repack complete", finalText = "Saved to $finalPath", ongoing = false, smallIcon = android.R.drawable.stat_sys_download_done)

            // Post-repack analysis: detect leftover references that may cause runtime crashes
            val warnings = try {
                RepackHelper.detectPotentialIssues(File(finalPath), originalPackage)
            } catch (e: Exception) {
                e.printStackTrace(); emptyList<String>()
            }

            // Merge workspace warnings (from deep unpack) and post-repack warnings
            val mergedWarnings = mutableListOf<String>()
            mergedWarnings.addAll(workspaceWarnings)
            mergedWarnings.addAll(warnings)

            // Persist history including warnings
            try {
                val historyDao = AppDatabase.getDatabase(applicationContext).cloneHistoryDao()
                historyDao.insert(
                    CloneHistory(
                        originalPackageName = originalPackage,
                        clonedPackageName = targetPackage ?: "unknown",
                        appName = targetAppName ?: "Cloned App",
                        cloneDate = System.currentTimeMillis(),
                        version = "1.0",
                        outputPath = finalPath,
                        warnings = if (mergedWarnings.isNotEmpty()) mergedWarnings.joinToString("\n") else null
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val baseOut = mutableMapOf<String, Any>(KEY_OUTPUT_PATH to finalPath)
            if (!workspacePath.isNullOrEmpty()) baseOut[KEY_WORKSPACE_PATH] = workspacePath
            val outData = if (mergedWarnings.isNotEmpty()) {
                baseOut[KEY_WARNINGS] = mergedWarnings.joinToString("\n")
                workDataOf(*baseOut.map { it.key to it.value.toString() }.toTypedArray())
            } else {
                workDataOf(*baseOut.map { it.key to it.value.toString() }.toTypedArray())
            }

            return Result.success(outData)
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationUtils.updateNotification(applicationContext, CHANNEL_ID, NOTIF_ID, 0, "Repack failed: ${e.message}", ongoing = false)
            return Result.failure(workDataOf("error" to (e.message ?: "repack_failed")))
        }
    }

}
