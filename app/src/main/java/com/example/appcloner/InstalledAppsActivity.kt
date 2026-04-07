package com.example.appcloner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.appcloner.databinding.ActivityInstalledAppsBinding

class InstalledAppsActivity : AppCompatActivity() {

    data class AppInfo(val label: String, val packageName: String)
    private lateinit var apps: List<AppInfo>
    private lateinit var binding: ActivityInstalledAppsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstalledAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps = installed
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, apps.map { it.label })
        binding.listViewApps.adapter = adapter
        
        binding.listViewApps.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].packageName
            val intent = Intent().apply {
                putExtra(AppConstants.SELECTED_PACKAGE_EXTRA, pkg)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }
}