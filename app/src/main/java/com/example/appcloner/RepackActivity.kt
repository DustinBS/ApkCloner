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
import com.example.appcloner.JobListActivity

class RepackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepackBinding
    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRepackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appSettings = AppSettings(this)

        val apkArray = intent.getStringArrayListExtra(AppConstants.APK_PATH_EXTRA)
        val apkPathSingle = intent.getStringExtra(AppConstants.APK_PATH_EXTRA)
        val apkPaths = when {
            apkArray != null && apkArray.isNotEmpty() -> apkArray
            apkPathSingle != null -> arrayListOf(apkPathSingle)
            else -> arrayListOf()
        }
        val originalPackage = intent.getStringExtra(AppConstants.SELECTED_PACKAGE_EXTRA) ?: "unknown.package"

        binding.tvApkPath.text = when {
            apkPaths.isEmpty() -> getString(com.example.appcloner.R.string.no_apk_selected)
            apkPaths.size == 1 -> File(apkPaths[0]).name
            else -> "${apkPaths.size} APKs selected"
        }
        
        val segments = originalPackage.split(".")
        val defaultPackageName = if (segments.size > 1) {
            "${segments[0]}.appcloner.${segments.drop(1).joinToString(".")}"
        } else {
            "$originalPackage.appcloner"
        }
        binding.etTargetPackage.setText(defaultPackageName)
        
        binding.layoutPackageName.helperText = getString(com.example.appcloner.R.string.warning_modify_package)

        binding.btnRepack.setOnClickListener {
            if (apkPaths.isEmpty()) {
                com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.no_apk_path_provided), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                return@setOnClickListener
            }

            val targetPackage = binding.etTargetPackage.text.toString().takeIf { it.isNotEmpty() } ?: getString(com.example.appcloner.R.string.default_clone_package)
            val targetAppName = binding.etTargetAppName.text.toString().takeIf { it.isNotEmpty() } ?: getString(com.example.appcloner.R.string.cloned_app_hint)

            // Immediate UI feedback
            binding.btnRepack.isEnabled = false
            binding.layoutProgress.visibility = android.view.View.VISIBLE
            binding.btnViewDownloads.visibility = android.view.View.GONE

            val cleanFileName = targetPackage.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")

            val input = workDataOf(
                RepackWorker.KEY_APK_PATH to apkPaths.toTypedArray(),
                RepackWorker.KEY_TARGET_PACKAGE to targetPackage,
                RepackWorker.KEY_TARGET_APP_NAME to targetAppName,
                RepackWorker.KEY_CUSTOM_URI to appSettings.customOutputFolderUri,
                RepackWorker.KEY_ORIGINAL_PACKAGE to originalPackage
            )

            lifecycleScope.launch {
                val wm = WorkManager.getInstance(applicationContext)

                // Check for existing work with the same unique name to avoid accidental duplicates
                val existing = withContext(Dispatchers.IO) {
                    try {
                        wm.getWorkInfosForUniqueWork(RepackWorker.UNIQUE_WORK_PREFIX + cleanFileName).get()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val alreadyRunning = existing.any { !it.state.isFinished }
                if (alreadyRunning) {
                    // Attach UI to existing running job so user sees progress instead of silently ignoring
                    val runningInfo = existing.firstOrNull { !it.state.isFinished }
                    runningInfo?.let { wi ->
                        wm.getWorkInfoByIdLiveData(wi.id).observe(this@RepackActivity, Observer { info ->
                            if (info == null) return@Observer
                            val progress = info.progress.getInt("progress", 0)
                            when {
                                info.state.isFinished -> {
                                    binding.layoutProgress.visibility = android.view.View.GONE
                                    binding.btnRepack.isEnabled = true
                                    binding.btnRepack.text = getString(com.example.appcloner.R.string.clone_again)
                                }
                                else -> {
                                    binding.layoutProgress.visibility = android.view.View.VISIBLE
                                    binding.tvProgressText.text = "Progress: $progress%"
                                }
                            }
                        })
                    }

                    com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.already_running_clone_job), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    binding.btnRepack.isEnabled = true
                    return@launch
                }

                val request = OneTimeWorkRequestBuilder<RepackWorker>()
                    .setInputData(input)
                    .addTag(RepackWorker.TAG_BASE)
                    .addTag(RepackWorker.TAG_PREFIX + cleanFileName)
                    .build()

                wm.enqueueUniqueWork(RepackWorker.UNIQUE_WORK_PREFIX + cleanFileName, ExistingWorkPolicy.KEEP, request)

                com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.clone_job_queued), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)

                // Observe work progress and completion
                wm.getWorkInfoByIdLiveData(request.id).observe(this@RepackActivity, Observer { info ->
                    if (info == null) return@Observer
                    val progress = info.progress.getInt("progress", 0)
                    when {
                        info.state.isFinished -> {
                            binding.layoutProgress.visibility = android.view.View.GONE
                                    binding.btnRepack.isEnabled = true
                                    binding.btnRepack.text = getString(com.example.appcloner.R.string.clone_again)
                            val output = info.outputData.getString(RepackWorker.KEY_OUTPUT_PATH)
                            if (!output.isNullOrEmpty()) {
                                // Show view downloads if custom URI provided
                                if (!appSettings.customOutputFolderUri.isNullOrEmpty()) {
                                    binding.btnViewDownloads.visibility = android.view.View.VISIBLE
                                    binding.btnViewDownloads.setOnClickListener {
                                        com.example.appcloner.util.UiUtils.openFolder(this@RepackActivity, appSettings.customOutputFolderUri, binding.root)
                                    }
                                }

                                if (appSettings.installImmediately) {
                                    // Try to open/install internal output file
                                    val outFile = File(filesDir, "$cleanFileName.apk")
                                    com.example.appcloner.util.UiUtils.openOutput(this@RepackActivity, outFile.absolutePath, appSettings.customOutputFolderUri, binding.root)
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

        binding.btnViewJobs.setOnClickListener {
            startActivity(Intent(this, JobListActivity::class.java))
        }
    }

    private fun showDesktopInstructions(apkPath: String, reason: String) {
        val suggestedOut = "repacked_${System.currentTimeMillis()}.apk"
        val cmd = "repack.ps1 -Input \"$apkPath\" -Out \"$suggestedOut\" -NewPackage com.example.clone -Label \"My Clone\" -Keystore <keystore.jks> -KeyAlias <alias> -StorePass <pass> -KeyPass <pass>"
        
        AlertDialog.Builder(this)
            .setTitle(getString(com.example.appcloner.R.string.on_device_repack_incomplete))
            .setMessage(getString(com.example.appcloner.R.string.desktop_repack_message, reason))
                .setPositiveButton(getString(com.example.appcloner.R.string.copy_pc_command)) { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("repack-cmd", cmd)
                clipboard.setPrimaryClip(clip)
                com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.command_copied), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            }
            .setNegativeButton(getString(com.example.appcloner.R.string.understood), null)
            .show()
    }

    // Installation / opening is handled by `UiUtils.openOutput` to avoid code duplication.
}
