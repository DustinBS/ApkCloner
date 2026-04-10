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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.appcloner.RepackHelper.RepackException
import com.example.appcloner.JobListActivity
import com.example.appcloner.util.ZipUtils

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

        // Default to deep-unpack and auto-fix enabled for robust cloning.
        try {
            binding.switchDeepUnpack.isChecked = true
            binding.switchAutoFix.isChecked = true
        } catch (_: Exception) { }

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

            val cleanFileName = RepackUtils.cleanFileName(targetPackage)

            val input = workDataOf(
                RepackWorker.KEY_APK_PATH to apkPaths.toTypedArray(),
                RepackWorker.KEY_TARGET_PACKAGE to targetPackage,
                RepackWorker.KEY_TARGET_APP_NAME to targetAppName,
                RepackWorker.KEY_CUSTOM_URI to appSettings.customOutputFolderUri,
                RepackWorker.KEY_ORIGINAL_PACKAGE to originalPackage
                ,RepackWorker.KEY_DEEP_UNPACK to binding.switchDeepUnpack.isChecked
                ,RepackWorker.KEY_AUTO_FIX to binding.switchAutoFix.isChecked
            )

            lifecycleScope.launch {
                val wm = WorkManager.getInstance(applicationContext)

                // Check for existing work with the same unique name to avoid accidental duplicates
                val existing = withContext(Dispatchers.IO) {
                    try {
                        // Use reflection to avoid compile-time linkage to ListenableFuture
                        val method = wm.javaClass.getMethod("getWorkInfosForUniqueWork", String::class.java)
                        val future = method.invoke(wm, RepackWorker.UNIQUE_WORK_PREFIX + cleanFileName)
                        val getMethod = future.javaClass.getMethod("get")
                        @Suppress("UNCHECKED_CAST")
                        val result = getMethod.invoke(future) as? List<androidx.work.WorkInfo>
                        result ?: emptyList()
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
                            val progress = info.progress.getInt(RepackWorker.KEY_PROGRESS, 0)
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
                    val progress = info.progress.getInt(RepackWorker.KEY_PROGRESS, 0)
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
                                // Show any post-repack warnings collected by the worker
                                val warnings = info.outputData.getString(com.example.appcloner.RepackWorker.KEY_WARNINGS)
                                if (!warnings.isNullOrEmpty()) {
                                    try {
                                        AlertDialog.Builder(this@RepackActivity)
                                            .setTitle(getString(com.example.appcloner.R.string.clone_completed_with_warnings))
                                            .setMessage(getString(com.example.appcloner.R.string.clone_warnings_details) + "\n\n" + warnings)
                                            .setPositiveButton(getString(com.example.appcloner.R.string.view_warnings), null)
                                            .setNeutralButton(getString(com.example.appcloner.R.string.prepare_desktop_bundle)) { _, _ ->
                                                // Attempt to create a desktop bundle (zip) containing the cloned APK and README
                                                val outPath = output
                                                if (!outPath.isNullOrEmpty()) {
                                                    prepareDesktopBundle(outPath, targetPackage, targetAppName)
                                                } else {
                                                    com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.prepare_bundle_failed, "no output path"))
                                                }
                                            }
                                            .setNegativeButton(getString(com.example.appcloner.R.string.understood), null)
                                            .show()
                                    } catch (_: Exception) { /* ignore UI errors */ }
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

    private fun prepareDesktopBundle(outputPath: String, targetPackage: String, targetAppName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outDir = getExternalFilesDir(null) ?: filesDir
                val bundleName = "${RepackUtils.cleanFileName(targetPackage)}_desktop_bundle_${System.currentTimeMillis()}.zip"
                val bundleFile = File(outDir, bundleName)

                ZipOutputStream(FileOutputStream(bundleFile)).use { zos ->
                    // add cloned APK (support content:// and file paths)
                    if (outputPath.startsWith("content://")) {
                        val uri = Uri.parse(outputPath)
                        contentResolver.openInputStream(uri)?.use { ins ->
                            ZipUtils.addStreamToZip(zos, "cloned.apk", ins)
                        }
                    } else {
                        val apkFile = File(outputPath)
                        if (apkFile.exists()) {
                            ZipUtils.addFileToZip(zos, apkFile.name, apkFile)
                        }
                    }

                    // Also include an unpacked workspace of the APK contents to speed up desktop repack
                    try {
                        val cleanFileName = RepackUtils.cleanFileName(targetPackage)
                        val workspaceDir = RepackUtils.deepUnpackDir(this@RepackActivity, cleanFileName)
                        if (workspaceDir.exists() && workspaceDir.isDirectory) {
                            // add files from workspace directory
                            workspaceDir.walkTopDown().forEach { f ->
                                val rel = f.relativeTo(workspaceDir).path.replace('\\', '/')
                                val destName = if (rel.isEmpty()) RepackUtils.WORKSPACE_PREFIX else RepackUtils.WORKSPACE_PREFIX + rel
                                if (f.isDirectory) {
                                    try { ZipUtils.addFileToZip(zos, destName, f) } catch (_: Exception) { }
                                } else {
                                    ZipUtils.addFileToZip(zos, destName, f)
                                }
                            }
                        } else {
                            // Fallback: extract entries from the output APK itself
                            val sourceApkFile = if (outputPath.startsWith("content://")) {
                                val uri = Uri.parse(outputPath)
                                val tmp = RepackUtils.createTempApkFile(cacheDir)
                                contentResolver.openInputStream(uri)?.use { ins ->
                                    tmp.outputStream().use { out -> ins.copyTo(out) }
                                }
                                tmp
                            } else {
                                File(outputPath)
                            }

                            if (sourceApkFile.exists()) {
                                java.util.zip.ZipFile(sourceApkFile).use { zf ->
                                    val en = zf.entries()
                                    while (en.hasMoreElements()) {
                                        val srcEntry = en.nextElement()
                                        val destName = RepackUtils.WORKSPACE_PREFIX + srcEntry.name
                                        try {
                                            zf.getInputStream(srcEntry).use { ins -> ZipUtils.addStreamToZip(zos, destName, ins) }
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            if (outputPath.startsWith("content://")) {
                                try { sourceApkFile.deleteRecursively() } catch (_: Exception) { }
                            }
                        }
                    } catch (e: Exception) {
                        // best-effort only - do not fail bundle creation because workspace copy fails
                        e.printStackTrace()
                    }

                    // README with suggested desktop repack command
                    val readme = StringBuilder()
                    readme.append("Prepared by AppCloner\n")
                    readme.append("Cloned APK: ")
                    readme.append(if (outputPath.startsWith("content://")) "cloned.apk" else File(outputPath).name)
                    readme.append("\n\n")
                    readme.append("Suggested desktop repack command (run on a PC with apktool/zipalign/apksigner):\n")
                    readme.append("repack.ps1 -Input \"<INPUT_APK>\" -Out \"repacked.apk\" -NewPackage ${targetPackage} -Label \"${targetAppName}\" -Keystore <keystore.jks> -KeyAlias <alias> -StorePass <pass> -KeyPass <pass>\n")

                    ZipUtils.addTextEntryToZip(zos, "README.txt", readme.toString())
                }

                // Optionally copy bundle to user-configured folder
                var copiedUri: String? = null
                if (!appSettings.customOutputFolderUri.isNullOrEmpty()) {
                    try {
                        val copied = com.example.appcloner.util.FileUtils.copyFileToTree(this@RepackActivity, bundleFile, appSettings.customOutputFolderUri!!)
                        if (!copied.isNullOrEmpty()) copiedUri = copied
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    if (copiedUri != null) {
                        com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.bundle_prepared))
                    } else {
                        com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.bundle_location, bundleFile.absolutePath), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    com.example.appcloner.util.UiUtils.showSnack(binding.root, getString(com.example.appcloner.R.string.prepare_bundle_failed, e.message ?: ""))
                }
            }
        }
    }
}
