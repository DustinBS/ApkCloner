package com.example.appcloner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import com.example.appcloner.util.FileUtils

class MainActivity : AppCompatActivity() {

    private val pickApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(this, "Selected APK: $uri", Toast.LENGTH_SHORT).show()
            Thread {
                val local = copyUriToFile(uri)
                runOnUiThread {
                    if (local != null) {
                        startRepackActivity(local.absolutePath)
                    } else {
                        Toast.makeText(this, "Failed to copy selected APK", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } else {
            Toast.makeText(this, "No APK selected", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var installedPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        installedPickerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val pkg = data.getStringExtra(AppConstants.SELECTED_PACKAGE_EXTRA)
                if (pkg != null) {
                    Toast.makeText(this, "Selected package: $pkg", Toast.LENGTH_SHORT).show()
                    copyApkFromPackage(pkg)
                }
            }
        }

        val btnPickInstalled = findViewById<Button>(R.id.btnPickInstalled)
        val btnPickApk = findViewById<Button>(R.id.btnPickApk)

        btnPickInstalled.setOnClickListener {
            val intent = Intent(this, InstalledAppsActivity::class.java)
            installedPickerLauncher.launch(intent)
        }

        btnPickApk.setOnClickListener {
            pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
        }
    }

    private fun copyApkFromPackage(packageName: String) {
        try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            val src = ai.sourceDir
            val inFile = File(src)
            val outFile = File(filesDir, "${packageName.replace('.', '_')}_original.apk")
            Thread {
                try {
                    FileUtils.copyFile(inFile, outFile)
                    runOnUiThread {
                        Toast.makeText(this, "APK copied to: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        startRepackActivity(outFile.absolutePath)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to copy APK: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val fileName = uri.lastPathSegment?.replace('/', '_') ?: "imported.apk"
            val outFile = File(filesDir, fileName)
            FileUtils.copyStreamToFile(input, outFile)
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun startRepackActivity(apkPath: String) {
        val intent = Intent(this, RepackActivity::class.java).apply {
            putExtra(AppConstants.APK_PATH_EXTRA, apkPath)
        }
        startActivity(intent)
    }
}
