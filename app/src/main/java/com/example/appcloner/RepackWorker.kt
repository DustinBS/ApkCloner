package com.example.appcloner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
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
    }

    override suspend fun doWork(): Result {
        val apkPaths = inputData.getStringArray(KEY_APK_PATH) ?: return Result.failure()
        val targetPackage = inputData.getString(KEY_TARGET_PACKAGE)
        val targetAppName = inputData.getString(KEY_TARGET_APP_NAME)
        val customUri = inputData.getString(KEY_CUSTOM_URI)
        val originalPackage = inputData.getString(KEY_ORIGINAL_PACKAGE) ?: "unknown.package"

        // Setup a foreground notification so long-running repacks show progress in the shade
        val channelId = "appcloner_repack"
        val notifId = 1001
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(channelId, "AppCloner Repack", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(chan)
        }

        val initialNotif = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Repack in progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForegroundAsync(ForegroundInfo(notifId, initialNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } else {
                setForegroundAsync(ForegroundInfo(notifId, initialNotif))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            setProgress(workDataOf("progress" to 5))
            NotificationManagerCompat.from(applicationContext).notify(
                notifId,
                NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Repacking ${targetAppName ?: "app"}")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(100, 5, true)
                    .build()
            )

            val filesDir = applicationContext.filesDir
            val cleanFileName = (targetPackage ?: "cloned").replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
            val outFile = File(filesDir, "$cleanFileName.apk")

            setProgress(workDataOf("progress" to 20))
            NotificationManagerCompat.from(applicationContext).notify(
                notifId,
                NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Repacking ${targetAppName ?: "app"}")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(100, 20, false)
                    .setContentText("Preparing resources (20%)")
                    .build()
            )

            val success = RepackHelper.repack(apkPaths.toList(), outFile.absolutePath, targetPackage, targetAppName)
            if (!success) return Result.failure()

            setProgress(workDataOf("progress" to 75))
            NotificationManagerCompat.from(applicationContext).notify(
                notifId,
                NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Repacking ${targetAppName ?: "app"}")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(100, 75, false)
                    .setContentText("Signing and finalizing (75%)")
                    .build()
            )

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
            NotificationManagerCompat.from(applicationContext).notify(
                notifId,
                NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Repack complete")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                    .setContentText("Saved to $finalPath")
                    .build()
            )

            return Result.success(workDataOf(KEY_OUTPUT_PATH to finalPath))
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(workDataOf("error" to (e.message ?: "repack_failed")))
        }
    }
}
