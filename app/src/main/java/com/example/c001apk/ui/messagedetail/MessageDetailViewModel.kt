package com.example.c001apk.ui.messagedetail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.model.OSSUploadPrepareModel
import com.example.c001apk.logic.model.OSSUploadPrepareResponse
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.Event
import com.example.c001apk.util.MessageKit
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 私信聊天页。
 *
 * 会话有两种进入方式：
 * 1. 从会话列表点进来：已经有 ukey，直接拉历史；
 * 2. 从别人主页点「私信」进来：只有对方的 uid，**还没有会话**，此时列表是空的，
 *    发出第一条消息时服务端才隐式建立会话（`/v6/message/send?uid=`），发完再回头把 ukey 认领回来。
 */
@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val networkRepo: NetworkRepo
) : ViewModel() {

    val chatData = MutableLiveData<List<MessageResponse.Data>>()
    val toastText = MutableLiveData<Event<String>>()

    /** OSS 上传准备就绪：带上 STS 凭证和**服务端分配的对象名**，交给页面去直传 */
    val uploadImage = MutableLiveData<Event<OSSUploadPrepareResponse.Data>>()

    /**
     * `showImage` 换来的签名地址缓存。auth_key 有效期约半小时，一次会话够用；
     * 列表来回滚动会反复 bind，不缓存的话每滚一次都要重新请求一遍。
     */
    private val picUrlCache = HashMap<String, String>()

    var ukey = ""
    var uid = ""

    /** 拉当前会话的历史记录。没有 ukey（还没说过话）时直接给空列表 */
    fun loadHistory() {
        if (ukey.isEmpty()) {
            chatData.postValue(emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getChatHistory(ukey, 1).collect { result ->
                val feed = result.getOrNull()
                if (feed == null) {
                    toastText.postValue(Event("加载聊天记录失败"))
                    return@collect
                }
                if (!feed.message.isNullOrEmpty()) {
                    toastText.postValue(Event(feed.message.toString()))
                    return@collect
                }
                // 服务端返回的顺序不保证，按时间升序排一遍（跟桌面版一致），最后滚到底就是最新一条
                chatData.postValue(feed.data.orEmpty().sortedBy { it.dateline })
            }
        }
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        send(uid, content, "")
    }

    /**
     * 图片走跟发动态同一套 OSS 直传：先 `ossUploadPrepare` 拿 STS 凭证和**服务端分配的对象名**，
     * 传完 OSS 再把这个对象名当 `message_pic` 发出去（见 [sendImageMessage]）。
     *
     * 与发动态的差别只有三个参数（2026-10-06 抓包核对）：bucket / dir 都是 `message`
     * ——图片落在私信专用桶 `coolapk-oss-message`，域名也是另一个；
     * `toUid` 必须填**对方** uid（动态那边传空串）。
     */
    fun prepareImageUpload(image: OSSUploadPrepareModel) {
        if (uid.isEmpty()) return
        val data = hashMapOf(
            "uploadBucket" to "message",
            "uploadDir" to "message",
            "is_anonymous" to "0",
            "uploadFileList" to Gson().toJson(listOf(image)),
            "toUid" to uid,
        )
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postOSSUploadPrepare(data).collect { result ->
                val feed = result.getOrNull()
                if (feed == null) {
                    toastText.postValue(Event("图片上传准备失败"))
                    return@collect
                }
                if (!feed.message.isNullOrEmpty()) {
                    toastText.postValue(Event(feed.message.toString()))
                    return@collect
                }
                val prepared = feed.data
                if (prepared == null || prepared.fileInfo.isEmpty()) {
                    toastText.postValue(Event("图片上传准备失败"))
                    return@collect
                }
                uploadImage.postValue(Event(prepared))
            }
        }
    }

    /**
     * 图片已经传到 OSS，把服务端给的 `uploadFileName` 发出去。
     * `message_pic` 要**带前导斜杠**：prepare 返回的是 `message/2026/1006/xxx.png`，
     * 抓包里发出去的是 `/message/2026/1006/xxx.png`。
     */
    fun sendImageMessage(uploadFileName: String) {
        if (uploadFileName.isEmpty()) return
        send(uid, "", "/$uploadFileName")
    }

    /**
     * 气泡里的图片地址。`message_pic` 只是 OSS 对象名，CDN 裸地址会被 auth_key 挡，
     * 得先问 `showImage` 拿 302 出来的签名地址。结果按消息 id 缓存，见 [picUrlCache]。
     */
    fun loadMessagePic(id: String, onReady: (String?) -> Unit) {
        if (id.isEmpty()) {
            onReady(null)
            return
        }
        picUrlCache[id]?.let {
            onReady(it)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val url = networkRepo.getMessagePicUrl(id).first().getOrNull()
            withContext(Dispatchers.Main) {
                if (url.isNullOrEmpty()) {
                    onReady(null)
                } else {
                    picUrlCache[id] = url
                    onReady(url)
                }
            }
        }
    }

    /** 发消息：文字走 [message]，图片走 [pic]（两者只有一个非空） */
    private fun send(toUid: String, message: String, pic: String) {
        if (toUid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.sendMessage(toUid, message, pic).collect { result ->
                val feed = result.getOrNull()
                if (feed == null) {
                    toastText.postValue(Event("发送失败"))
                    return@collect
                }
                if (!feed.message.isNullOrEmpty()) {
                    toastText.postValue(Event(feed.message.toString()))
                    return@collect
                }
                // 第一条消息发出去后服务端才建会话，这时 ukey 还是空的，回头认领一次
                if (ukey.isEmpty()) {
                    ukey = findUkeyByUid()
                }
                loadHistory()
            }
        }
    }

    /** 把会话标记为已读（新会话没有 ukey，不用调） */
    fun markRead() {
        if (ukey.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.readMessage(ukey).collect { }
        }
    }

    /**
     * 刚发出第一条消息时还没拿到 ukey，回会话列表里按对方 uid 认领。
     * 服务端建会话有延迟，列表可能还没刷出来，所以重试几次。
     */
    private suspend fun findUkeyByUid(): String {
        repeat(3) { attempt ->
            if (attempt > 0) delay(600)
            val result = networkRepo.getMessage("/v6/message/list", 1, null).first()
            val data = result.getOrNull()?.data.orEmpty()
            val ukey = data.firstOrNull { MessageKit.partnerUid(it) == uid }?.ukey.orEmpty()
            if (ukey.isNotEmpty()) return ukey
        }
        return ""
    }

}
