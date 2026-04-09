package com.example.appcloner

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.appcloner.databinding.BottomSheetAppDetailsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAppDetailsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_PACKAGE_NAME = "package_name"

        fun newInstance(packageName: String): AppDetailsBottomSheet {
            val fragment = AppDetailsBottomSheet()
            val args = Bundle().apply {
                putString(ARG_PACKAGE_NAME, packageName)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: return
        val pm = requireContext().packageManager

        try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
            val packageInfo = pm.getPackageInfo(packageName, flags)

            // Header Section
            binding.ivAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
            binding.tvAppName.text = pm.getApplicationLabel(appInfo)
            binding.tvPackageName.text = packageName
            binding.chipApiBadge.text = "Target API ${appInfo.targetSdkVersion}"

            // Information Grid
            binding.tvVersion.text = packageInfo.versionName ?: "Unknown"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.tvMinApi.text = "API ${appInfo.minSdkVersion}"
            } else {
                binding.tvMinApi.text = "Legacy"
            }
            
            binding.tvTargetApi.text = "API ${appInfo.targetSdkVersion}"

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvInstalledDate.text = formatter.format(Date(packageInfo.firstInstallTime))
            binding.tvUpdatedDate.text = formatter.format(Date(packageInfo.lastUpdateTime))

            val installerInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            binding.tvInstaller.text = installerInfo ?: "Unknown Source"

            // Component Chips
            val permCount = packageInfo.requestedPermissions?.size ?: 0
            val actCount = packageInfo.activities?.size ?: 0
            val srvCount = packageInfo.services?.size ?: 0
            val provCount = packageInfo.providers?.size ?: 0

            binding.chipPermissions.text = "Permissions ($permCount)"
            binding.chipActivities.text = "Activities ($actCount)"
            binding.chipServices.text = "Services ($srvCount)"
            binding.chipProviders.text = "Providers ($provCount)"

            // Make chips interactive: show a simple list dialog for each component type
            fun showList(title: String, items: Array<String>?) {
                val message = if (items == null || items.isEmpty()) "None" else items.joinToString(separator = "\n")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            binding.chipPermissions.setOnClickListener {
                showList("Permissions", packageInfo.requestedPermissions)
            }

            binding.chipActivities.setOnClickListener {
                val arr = packageInfo.activities?.map { it.name ?: "<unknown>" }?.toTypedArray()
                showList("Activities", arr)
            }

            binding.chipServices.setOnClickListener {
                val arr = packageInfo.services?.map { it.name ?: "<unknown>" }?.toTypedArray()
                showList("Services", arr)
            }

            binding.chipProviders.setOnClickListener {
                val arr = packageInfo.providers?.map { p ->
                    val auth = try { p.authority ?: "" } catch (e: Exception) { "" }
                    val name = try { p.name ?: "" } catch (e: Exception) { "" }
                    if (auth.isNotEmpty()) "$auth — $name" else name
                }?.toTypedArray()
                showList("Providers", arr)
            }

            // Footers
            binding.btnLaunchApp.setOnClickListener {
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    com.example.appcloner.util.UiUtils.showSnack(binding.root, "App cannot be launched")
                }
                dismiss()
            }

            binding.btnCloneApp.setOnClickListener {
                val intent = Intent(requireActivity(), RepackActivity::class.java).apply {
                    putExtra(AppConstants.APK_PATH_EXTRA, appInfo.sourceDir)
                    putExtra(AppConstants.SELECTED_PACKAGE_EXTRA, packageName)
                }
                startActivity(intent)
                dismiss()
            }

            binding.btnAppInfo.setOnClickListener {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${packageName}")
                }
                startActivity(intent)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            com.example.appcloner.util.UiUtils.showSnack(binding.root, "Failed to load app details")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}