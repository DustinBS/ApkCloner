package com.example.appcloner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.example.appcloner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appSettings: AppSettings

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            appSettings.customOutputFolderUri = uri.toString()
            updateFolderUI()
            com.example.appcloner.util.UiUtils.showSnack(binding.root, "Folder saved!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appSettings = AppSettings(this)

        binding.switchInstall.isChecked = appSettings.installImmediately

        binding.switchInstall.setOnCheckedChangeListener { _, isChecked ->
            appSettings.installImmediately = isChecked
        }

        updateFolderUI()

        binding.btnPickDir.setOnClickListener {
            selectFolderLauncher.launch(null)
        }
    }

    private fun updateFolderUI() {
        val uriStr = appSettings.customOutputFolderUri
        if (uriStr == null) {
            binding.tvCurrentDir.text = "Default Internal Folder (Requires File Provider fallback)"
        } else {
            val doc = DocumentFile.fromTreeUri(this, Uri.parse(uriStr))
            binding.tvCurrentDir.text = doc?.name ?: uriStr
        }
    }
}