package com.example.c001apk.ui.dialog

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.util.ApkDownloadProgress
import com.example.c001apk.util.ApkDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * APK 下载对话框的状态机：确认 → 下载中 → 完成 / 失败。
 *
 * 放在 ViewModel 里而不是对话框内部：转屏或对话框重建时下载不中断，
 * 重建后观察 [state] 会立刻拿到最新进度。
 */
class ApkDownloadViewModel : ViewModel() {

    sealed class State {
        /** 还没开始，等用户确认 */
        object Confirming : State()

        data class Downloading(
            val percent: Int,
            val speed: String,
            val remain: String,
        ) : State()

        data class Done(val file: File) : State()

        data class Failed(val message: String) : State()
    }

    private val _state = MutableLiveData<State>(State.Confirming)
    val state: LiveData<State> = _state

    private var job: Job? = null

    fun start(context: Context, url: String, fileName: String) {
        if (job?.isActive == true) return
        _state.value = State.Downloading(0, "", "")
        job = viewModelScope.launch {
            try {
                val file = ApkDownloader.download(context, url, fileName) {
                    _state.postValue(it.toDownloading())
                }
                _state.postValue(State.Done(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _state.postValue(State.Failed(e.message ?: ""))
            }
        }
    }

    /**
     * 取消：中断下载并删掉安装包。
     * 下载中由 [ApkDownloader] 删半成品，已完成（job 已结束）时在这里删整个包。
     */
    fun cancel() {
        job?.cancel()
        job = null
        (_state.value as? State.Done)?.file?.delete()
        _state.value = State.Confirming
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }

    private fun ApkDownloadProgress.toDownloading(): State.Downloading {
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        return State.Downloading(
            percent = percent.coerceIn(0, 100),
            speed = if (speed > 0) "${formatApkSize(speed)}/s" else "",
            remain = if (speed > 0 && total > downloaded)
                formatApkDuration((total - downloaded) / speed) else "",
        )
    }
}

/** 字节数转可读大小，与酷安「111.6M」的写法保持一致 */
private fun formatApkSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2fG", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1fM", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format(Locale.US, "%.0fK", bytes / 1024.0)
    else -> "${bytes}B"
}

private fun formatApkDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}小时${seconds % 3600 / 60}分"
    seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds}秒"
}
