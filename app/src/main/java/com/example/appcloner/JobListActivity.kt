package com.example.appcloner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcloner.databinding.ActivityJobListBinding
import com.example.appcloner.databinding.ItemJobBinding
import androidx.work.WorkInfo
import androidx.work.WorkManager

class JobListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobListBinding
    private lateinit var adapter: JobsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = JobsAdapter()
        binding.recyclerViewJobs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewJobs.adapter = adapter

        WorkManager.getInstance(this).getWorkInfosByTagLiveData(RepackWorker.TAG_BASE).observe(this) { list ->
            adapter.setData(list.sortedByDescending { it.state.isFinished })
        }
    }

    inner class JobsAdapter : RecyclerView.Adapter<JobsAdapter.ViewHolder>() {
        private var items: List<WorkInfo> = emptyList()

        fun setData(list: List<WorkInfo>) {
            items = list
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemJobBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val b = ItemJobBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val wi = items[position]
            val b = holder.binding
            val idShort = wi.id.toString().take(8)
            val progress = wi.progress.getInt("progress", 0)
            val statusText = when {
                wi.state.isFinished -> "Finished"
                else -> "${wi.state.name} — $progress%"
            }

            // Prefer friendly name from tags when present (format: "repack:<cleanName>")
            val friendly = wi.tags.firstOrNull { it.startsWith(RepackWorker.TAG_PREFIX) }?.substringAfter(RepackWorker.TAG_PREFIX)
            b.tvJobTitle.text = friendly?.let { "Clone: $it" } ?: "Job ${idShort}"
            b.tvJobStatus.text = statusText

            b.btnCancel.isEnabled = !wi.state.isFinished
            b.btnCancel.setOnClickListener {
                WorkManager.getInstance(this@JobListActivity).cancelWorkById(wi.id)
            }

            b.root.setOnClickListener {
                if (wi.state.isFinished) {
                    val out = wi.outputData.getString(RepackWorker.KEY_OUTPUT_PATH)
                    if (!out.isNullOrEmpty()) com.example.appcloner.util.UiUtils.openOutput(this@JobListActivity, out, null, b.root)
                    else com.example.appcloner.util.UiUtils.showSnack(b.root, getString(com.example.appcloner.R.string.no_output_available))
                } else {
                    com.example.appcloner.util.UiUtils.showSnack(b.root, "Job is ${wi.state.name}")
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
