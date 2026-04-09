package com.example.appcloner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcloner.databinding.ActivityInstalledAppsBinding
import com.example.appcloner.databinding.ItemInstalledAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppsActivity : AppCompatActivity() {

    data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var binding: ActivityInstalledAppsBinding
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstalledAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppAdapter(
            onClick = { appInfo ->
                val intent = Intent().apply {
                    putExtra(AppConstants.SELECTED_PACKAGE_EXTRA, appInfo.packageName)
                    
                    // Fetch split APK paths
                    try {
                        val pm = packageManager
                        val info = pm.getApplicationInfo(appInfo.packageName, 0)
                        val apkPaths = mutableListOf<String>()
                        info.sourceDir?.let { apkPaths.add(it) }
                        info.splitSourceDirs?.let { apkPaths.addAll(it.toList()) }
                        putStringArrayListExtra(AppConstants.APK_PATH_EXTRA, ArrayList(apkPaths))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setResult(RESULT_OK, intent)
                finish()
            },
            onSettingsClick = { appInfo ->
                val bottomSheet = AppDetailsBottomSheet.newInstance(appInfo.packageName)
                try {
                    bottomSheet.show(supportFragmentManager, "app_details_${appInfo.packageName}")
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    supportFragmentManager.beginTransaction().add(bottomSheet, "app_details_${appInfo.packageName}").commitAllowingStateLoss()
                }
            }
        )

        binding.recyclerViewAppsSwipe.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAppsSwipe.adapter = adapter
        
        binding.recyclerViewSearch.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSearch.adapter = adapter

        binding.searchView.setupWithSearchBar(binding.searchBar)
        
        binding.searchView.editText.setOnEditorActionListener { _, _, _ ->
            binding.searchBar.setText(binding.searchView.text)
            binding.searchView.hide()
            false
        }
        
        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.swipeRefresh.setOnRefreshListener {
            loadApps()
        }

        loadApps()
    }

    private fun loadApps() {
        binding.progressInstalled.visibility = android.view.View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val apps = installed
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    AppInfo(
                        label = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        icon = pm.getApplicationIcon(info)
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                adapter.setData(apps)
                binding.progressInstalled.visibility = android.view.View.GONE
                if (binding.swipeRefresh.isRefreshing) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    class AppAdapter(
        private val onClick: (AppInfo) -> Unit,
        private val onSettingsClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>(), Filterable {

        private var appList = listOf<AppInfo>()
        private var filteredList = listOf<AppInfo>()

        fun setData(apps: List<AppInfo>) {
            appList = apps
            filteredList = apps
            notifyDataSetHeightChanged()
        }
        
        private fun notifyDataSetHeightChanged() { notifyDataSetChanged() }

        class ViewHolder(val binding: ItemInstalledAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemInstalledAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = filteredList[position]
            holder.binding.tvAppName.text = app.label
            holder.binding.tvAppPackage.text = app.packageName
            holder.binding.ivAppIcon.setImageDrawable(app.icon)

            holder.binding.root.setOnClickListener {
                onClick(app)
            }

            holder.binding.root.setOnLongClickListener {
                onSettingsClick(app)
                true
            }

            holder.binding.btnSettings.setOnClickListener {
                onSettingsClick(app)
            }
        }

        override fun getItemCount() = filteredList.size

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = constraint?.toString()?.lowercase() ?: ""
                    val resultList = if (query.isEmpty()) {
                        appList
                    } else {
                        appList.filter {
                            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                        }
                    }
                    return FilterResults().apply { values = resultList }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filteredList = results?.values as? List<AppInfo> ?: emptyList()
                    notifyDataSetChanged()
                }
            }
        }
    }
}
