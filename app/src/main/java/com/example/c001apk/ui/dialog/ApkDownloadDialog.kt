package com.example.c001apk.ui.dialog

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.databinding.DialogApkDownloadBinding
import com.example.c001apk.util.makeToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * APK 下载对话框，三态共用一个布局：
 * 1. 确认：标题「确认下载吗？」+ 应用名 + 包大小，「取消 / 下载」
 * 2. 下载中：进度条 + 百分比 + 速度 + 剩余时间，取消可随时中断并删包
 * 3. 完成：「取消（关闭并删包）/ 打开（分享给其它应用）/ 安装（系统安装器）」
 *
 * 下载任务在 [ApkDownloadViewModel]，转屏 / 对话框重建不会中断。
 */
class ApkDownloadDialog : DialogFragment() {

    private var _binding: DialogApkDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<ApkDownloadViewModel>()

    private var url: String = ""
    private var fileName: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogApkDownloadBinding.inflate(layoutInflater)
        url = arguments?.getString(ARG_URL).orEmpty()
        fileName = arguments?.getString(ARG_FILE_NAME).orEmpty()
        binding.name.text = arguments?.getString(ARG_TITLE).orEmpty()
        binding.size.text =
            getString(R.string.download_size, arguments?.getString(ARG_SIZE).orEmpty())

        binding.confirm.setOnClickListener {
            it.isEnabled = false
            viewModel.start(requireContext().applicationContext, url, fileName)
        }
        binding.cancel.setOnClickListener {
            viewModel.cancel()
            dismissAllowingStateLoss()
        }
        binding.open.setOnClickListener { doneFile()?.let { file -> share(file) } }
        binding.install.setOnClickListener { doneFile()?.let { file -> install(file) } }

        viewModel.state.observe(this) { render(it) }
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            // 下载中点返回键会静默中断且不删半成品，这里统一走「取消」按钮
            .setCancelable(false)
            .create()
    }

    private fun doneFile(): File? = (viewModel.state.value as? ApkDownloadViewModel.State.Done)?.file

    private fun render(state: ApkDownloadViewModel.State) {
        val binding = _binding ?: return
        when (state) {
            is ApkDownloadViewModel.State.Confirming -> {
                binding.title.setText(R.string.download_confirm_title)
                binding.name.isVisible = true
                binding.size.isVisible = true
                binding.progressGroup.isVisible = false
                binding.confirm.isVisible = true
                binding.confirm.isEnabled = true
                binding.open.isVisible = false
                binding.install.isVisible = false
            }

            is ApkDownloadViewModel.State.Downloading -> {
                binding.title.setText(R.string.download_downloading)
                binding.name.isVisible = false
                binding.size.isVisible = false
                binding.progressGroup.isVisible = true
                binding.progress.setProgressCompat(state.percent, true)
                binding.percent.text = getString(R.string.download_percent, state.percent)
                binding.speed.text = state.speed
                binding.remain.text =
                    if (state.remain.isEmpty()) "" else getString(R.string.download_remain, state.remain)
                binding.confirm.isVisible = false
                binding.open.isVisible = false
                binding.install.isVisible = false
            }

            is ApkDownloadViewModel.State.Done -> {
                binding.title.setText(R.string.download_done)
                binding.name.isVisible = false
                binding.size.isVisible = false
                binding.progressGroup.isVisible = false
                binding.confirm.isVisible = false
                binding.open.isVisible = true
                binding.install.isVisible = true
            }

            is ApkDownloadViewModel.State.Failed -> {
                requireContext().makeToast(getString(R.string.download_failed, state.message))
                dismissAllowingStateLoss()
            }
        }
        binding.cancel.setText(android.R.string.cancel)
    }

    private fun uriOf(file: File): Uri = FileProvider.getUriForFile(
        requireContext(),
        "${requireContext().packageName}.fileprovider",
        file
    )

    /** 打开：交给系统的分享面板，让用户挑一个应用打开安装包 */
    private fun share(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uriOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.download_open_with)))
        } catch (e: ActivityNotFoundException) {
            requireContext().makeToast(getString(R.string.download_no_app))
        }
    }

    /** 安装：调起系统安装器（Android 8+ 首次会要求授权「安装未知应用」） */
    private fun install(file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(file), APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            requireContext().makeToast(getString(R.string.download_no_installer))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_FILE_NAME = "fileName"
        private const val ARG_TITLE = "title"
        private const val ARG_SIZE = "size"
        private const val APK_MIME = "application/vnd.android.package-archive"

        fun newInstance(url: String, fileName: String, title: String, size: String) =
            ApkDownloadDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_FILE_NAME, fileName)
                    putString(ARG_TITLE, title)
                    putString(ARG_SIZE, size)
                }
            }
    }
}
