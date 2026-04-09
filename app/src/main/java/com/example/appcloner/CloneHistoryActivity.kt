package com.example.appcloner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcloner.databinding.ActivityCloneHistoryBinding
import com.example.appcloner.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloneHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloneHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloneHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = HistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        val dao = AppDatabase.getDatabase(this).cloneHistoryDao()

        lifecycleScope.launch {
            binding.progressHistory.visibility = View.VISIBLE
            dao.getAllHistory().collect { historyList ->
                binding.progressHistory.visibility = View.GONE
                if (historyList.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerHistory.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerHistory.visibility = View.VISIBLE
                    adapter.submitList(historyList)
                }
            }
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch {
                dao.clearAll()
            }
        }
    }
}

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<CloneHistory>()

    fun submitList(newItems: List<CloneHistory>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CloneHistory) {
            binding.tvAppName.text = item.appName
            binding.tvOriginalPkg.text = "Original: ${item.originalPackageName}"
            binding.tvClonedPkg.text = "Cloned: ${item.clonedPackageName}"

            // Create a user-friendly path display (use centralized helper)
            val displayPath = com.example.appcloner.util.FileUtils.getDisplayName(binding.root.context, item.outputPath) ?: item.outputPath

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            binding.tvDate.text = "Date: ${formatter.format(Date(item.cloneDate))}\nPath: $displayPath"
        }
    }
}