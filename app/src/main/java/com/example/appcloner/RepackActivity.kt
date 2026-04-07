package com.example.appcloner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appcloner.databinding.ActivityRepackBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RepackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRepackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apkPath = intent.getStringExtra(AppConstants.APK_PATH_EXTRA)
        
        binding.tvApkPath.text = apkPath ?: "No APK selected"

        binding.btnRepack.setOnClickListener {
            if (apkPath == null) {
                Toast.makeText(this, "No APK path provided", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Starting repack (stub)...", Toast.LENGTH_SHORT).show()
            
            lifecycleScope.launch(Dispatchers.IO) {
                val outFile = File(filesDir, "cloned_${System.currentTimeMillis()}.apk")
                val success = RepackHelper.repack(apkPath, outFile.absolutePath, null)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@RepackActivity, "Repack complete: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        installApk(outFile)
                    } else {
                        showDesktopInstructions(apkPath)
                    }
                }
            }
        }
    }

    private fun showDesktopInstructions(apkPath: String) {
        val suggestedOut = "cloned_${System.currentTimeMillis()}.apk"
        val cmd = "repack.ps1 -Input \"$apkPath\" -Out \"$suggestedOut\" -NewPackage com.example.clone -Label \"My Clone\" -Keystore <keystore.jks> -KeyAlias <alias> -StorePass <pass> -KeyPass <pass>"
        
        AlertDialog.Builder(this)
            .setTitle("Desktop repack required")
            .setMessage("This device cannot safely perform full repackaging. Run the following on your Windows PC in the android-app folder:\n\n$cmd\n\nOr use pull_and_repack.ps1 to pull an installed app and repack it.\n\nAfter repacking, transfer the cloned APK back to the device and open it to install.")
            .setPositiveButton("Copy Command") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("repack-cmd", cmd)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("OK", null)
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
            Toast.makeText(this, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}