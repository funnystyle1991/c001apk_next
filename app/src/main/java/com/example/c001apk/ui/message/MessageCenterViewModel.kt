package com.example.c001apk.ui.message

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.constant.Constants.LOADING_END
import com.example.c001apk.constant.Constants.LOADING_FAILED
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 消息中心（独立页面）：通知列表 + 各类未读数。
 * 从原来的 MineFragment / MessageViewModel 里拆出来，个人中心不再背消息逻辑。
 */
@HiltViewModel
class MessageCenterViewModel @Inject constructor(
    private val blackListRepo: BlackListRepo,
    private val historyRepo: HistoryFavoriteRepo,
    private val networkRepo: NetworkRepo
) : ViewModel() {

    var isRefreshing: Boolean = false
    var isLoadMore: Boolean = false
    var isEnd: Boolean = false
    var page = 1
    var lastItem: String? = null

    var messCountList = MutableLiveData<Boolean>()
    val footerState = MutableLiveData<FooterState>()
    val messageData = MutableLiveData<List<MessageResponse.Data>>()
    val toastText = MutableLiveData<Event<String>>()

    fun fetchMessage(url: String = "/v6/notification/list") {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getMessage(url, page, lastItem)
                .onStart {
                    if (isLoadMore)
                        footerState.postValue(FooterState.Loading)
                }
                .collect { result ->
                    val messageList = messageData.value?.toMutableList() ?: ArrayList()
                    val feed = result.getOrNull()
                    if (feed != null) {
                        if (!feed.message.isNullOrEmpty()) {
                            footerState.postValue(FooterState.LoadingError(feed.message))
                            return@collect
                        } else if (!feed.data.isNullOrEmpty()) {
                            lastItem = feed.data.last().id
                            if (isRefreshing)
                                messageList.clear()
                            if (isRefreshing || isLoadMore) {
                                feed.data.forEach {
                                    if (it.entityType == "notification")
                                        if (!blackListRepo.checkUid(it.fromuid))
                                            messageList.add(it)
                                }
                            }
                            page++
                            messageData.postValue(messageList)
                            footerState.postValue(FooterState.LoadingDone)
                        } else if (feed.data?.isEmpty() == true) {
                            isEnd = true
                            if (isRefreshing)
                                messageData.postValue(emptyList())
                            footerState.postValue(FooterState.LoadingEnd(LOADING_END))
                        }
                    } else {
                        isEnd = true
                        footerState.postValue(FooterState.LoadingError(LOADING_FAILED))
                        result.exceptionOrNull()?.printStackTrace()
                    }
                    isRefreshing = false
                    isLoadMore = false
                }
        }
    }

    fun onPostDelete(position: Int, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postDelete("/v6/notification/delete", id)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data == "删除成功") {
                            val messList = messageData.value?.toMutableList() ?: ArrayList()
                            messList.removeAt(position)
                            messageData.postValue(messList)
                            toastText.postValue(Event(response.data))
                        } else if (!response.message.isNullOrEmpty()) {
                            toastText.postValue(Event(response.message))
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    fun saveUid(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.saveUid(uid)
        }
    }

    fun saveHistory(
        id: String,
        uid: String,
        username: String,
        userAvatar: String,
        deviceTitle: String,
        message: String,
        dateline: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.saveHistory(
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
            )
        }
    }

    fun onCheckCount() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.checkCount()
                .collect { result ->
                    result.getOrNull()?.data?.let {
                        CookieUtil.atme = it.atme
                        CookieUtil.atcommentme = it.atcommentme
                        CookieUtil.feedlike = it.feedlike
                        CookieUtil.contacts_follow = it.contactsFollow
                        CookieUtil.badge = it.badge
                        CookieUtil.notification = it.notification
                        messCountList.postValue(true)
                    }
                }
        }
    }

    fun refreshMessage() {
        lastItem = null
        page = 1
        isEnd = false
        isRefreshing = true
        isLoadMore = false
        fetchMessage()
    }

}
