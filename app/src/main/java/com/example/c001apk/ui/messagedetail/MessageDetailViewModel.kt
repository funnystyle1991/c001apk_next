package com.example.c001apk.ui.messagedetail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.Event
import com.example.c001apk.util.MessageKit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        if (content.isEmpty() || uid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.sendMessage(uid, content).collect { result ->
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
