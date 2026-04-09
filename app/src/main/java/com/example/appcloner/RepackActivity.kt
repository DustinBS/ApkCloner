package com.example.appcloner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.appcloner.databinding.ActivityRepackBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.appcloner.RepackHelper.RepackException

class RepackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepackBinding
    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRepackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appSettings = AppSettings(this)

        val apkPath = intent.getStringExtra(AppConstants.APK_PATH_EXTRA)
        val originalPackage = intent.getStringExtra(AppConstants.SELECTED_PACKAGE_EXTRA) ?: "unknown.package"
        
        binding.tvApkPath.text = apkPath?.let { File(it).name } ?: "No APK selected"
        
        val segments = originalPackage.split(".")
        val defaultPackageName = if (segments.size > 1) {
            "${segments[0]}.appcloner.${segments.drop(1).joinToString(".")}"
        } else {
            "$originalPackage.appcloner"
        }
        binding.etTargetPackage.setText(defaultPackageName)
        
        binding.layoutPackageName.helperText = "Warning: Modifying core package name may break apps relying on strict signatures."

        binding.btnRepack.setOnClickListener {
            if (apkPath == null) {
                Snackbar.make(binding.root, "No APK path provided", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val targetPackage = binding.etTargetPackage.text.toString().takeIf { it.isNotEmpty() } ?: "com.example.clone"
            val targetAppName = binding.etTargetAppName.text.toString().takeIf { it.isNotEmpty() } ?: "Cloned App"

            // Enqueue background WorkManager job so repack continues if user leaves the activity
            binding.btnRepack.isEnabled = false
            binding.layoutProgress.visibility = android.view.View.VISIBLE
            binding.btnViewDownloads.visibility = android.view.View.GONE

            val cleanFileName = targetPackage.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")

            val input = workDataOf(
                RepackWorker.KEY_APK_PATH to (apkPath),
                RepackWorker.KEY_TARGET_PACKAGE to targetPackage,
                RepackWorker.KEY_TARGET_APP_NAME to targetAppName,
                RepackWorker.KEY_CUSTOM_URI to appSettings.customOutputFolderUri,
                RepackWorker.KEY_ORIGINAL_PACKAGE to originalPackage
            )

            val request = OneTimeWorkRequestBuilder<RepackWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(this)
                .enqueueUniqueWork("repack_$cleanFileName", ExistingWorkPolicy.REPLACE, request)

            // Observe work progress and completion
            WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id).observe(this, Observer { info ->
                if (info == null) return@Observer
                val progress = info.progress.getInt("progress", 0)
                when {
                    info.state.isFinished -> {
                        binding.layoutProgress.visibility = android.view.View.GONE
                        binding.btnRepack.isEnabled = true
                        binding.btnRepack.text = "Clone Again"
                        val output = info.outputData.getString(RepackWorker.KEY_OUTPUT_PATH)
                        if (!output.isNullOrEmpty()) {
                            // Show view downloads if custom URI provided
                            if (!appSettings.customOutputFolderUri.isNullOrEmpty()) {
                                binding.btnViewDownloads.visibility = android.view.View.VISIBLE
                                binding.btnViewDownloads.setOnClickListener {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse(appSettings.customOutputFolderUri)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                                    else com.google.android.material.snackbar.Snackbar.make(binding.root, "No file manager found.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                                }
                            }

                            if (appSettings.installImmediately) {
                                // Try to install internal output file
                                val outFile = File(filesDir, "$cleanFileName.apk")
                                installApk(outFile)
                            }
                        }
                    }
                    else -> {
                        binding.layoutProgress.visibility = android.view.View.VISIBLE
                        binding.tvProgressText.text = "Progress: $progress%"
                    }
                }
            })
        }
    }

    private fun showDesktopInstructions(apkPath: String, reason: String) {
        val suggestedOut = "repacked_${System.currentTimeMillis()}.apk"
        val cmd = "repack.ps1 -Input \"$apkPath\" -Out \"$suggestedOut\" -NewPackage com.example.clone -Label \"My Clone\" -Keystore <keystore.jks> -KeyAlias <alias> -StorePass <pass> -KeyPass <pass>"
        
        AlertDialog.Builder(this)
            .setTitle("On-Device Repack Incomplete")
            .setMessage("Reason: $reason\n\nTo fully restructure the APK, you must use a PC with proper Zipalign/Apktool support.")
                .setPositiveButton("Copy PC Command") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("repack-cmd", cmd)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, "Command copied to clipboard", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Understood", null)
            .show()
    }

    private fun installApk(apkFile: File) {
        try {
            val authority = "${applicationContext.packageName}.provider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to launch installer: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
