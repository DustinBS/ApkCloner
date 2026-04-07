package com.example.appcloner

import android.app.ListActivity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter

class InstalledAppsActivity : ListActivity() {

    data class AppInfo(val label: String, val packageName: String)

    private lateinit var apps: List<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps = installed
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, apps.map { it.label })
        listAdapter = adapter
    }

    override fun onListItemClick(l: android.widget.ListView?, v: android.view.View?, position: Int, id: Long) {
        val pkg = apps[position].packageName
        val intent = Intent().apply {
            putExtra(AppConstants.SELECTED_PACKAGE_EXTRA, pkg)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}
