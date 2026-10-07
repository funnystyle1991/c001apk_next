package com.example.c001apk.ui.article

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.OSSUploadPrepareResponse
import com.example.c001apk.logic.model.StringEntity
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.logic.repository.RecentEmojiRepo
import com.example.c001apk.util.Event
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArticlePublishViewModel @Inject constructor(
    private val recentEmojiRepo: RecentEmojiRepo,
    private val networkRepo: NetworkRepo
) : ViewModel() {

    /** 最近用过的表情，表情面板第一页；和发表动态页读的是同一张表 */
    val recentEmojiLiveData: LiveData<List<StringEntity>> = recentEmojiRepo.loadAllListLive()

    /** 最近一栏还是空的（首次用）：面板默认停到「默认」页，别停在一个空页上 */
    var isInit = true

    val toastText = MutableLiveData<Event<String?>>()
    val over = MutableLiveData<Event<Boolean>>()
    val uploadImage = MutableLiveData<Event<OSSUploadPrepareResponse.Data>>()

    /**
     * 记一次表情使用。逻辑与发表动态页一致：最近一栏上限 27 个，
     * 满了就把最旧的那条就地改成新表情（而不是删一条插一条）。
     */
    fun updateRecentEmoji(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (recentEmojiRepo.checkEmoji(data)) {
                recentEmojiRepo.updateEmoji(data, System.currentTimeMillis())
            } else {
                if (recentEmojiLiveData.value?.size == 27)
                    recentEmojiLiveData.value?.last()?.data?.let {
                        recentEmojiRepo.updateEmoji(it, data, System.currentTimeMillis())
                    }
                else
                    recentEmojiRepo.insertEmoji(StringEntity(data))
            }
        }
    }

    /** debug 包长按「最近」清空用 */
    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            recentEmojiRepo.deleteAll()
        }
    }

    /** createFeed 的完整表单数据，发布前由 Activity 组装 */
    var feedData = HashMap<String, String>()

    private val ossUploadPrepareData: HashMap<String, String> = HashMap()
    fun onPostOSSUploadPrepare(uploadFileList: List<ArticleUploadFile>) {
        ossUploadPrepareData["uploadBucket"] = "image"
        ossUploadPrepareData["uploadDir"] = "feed"
        ossUploadPrepareData["is_anonymous"] = "0"
        ossUploadPrepareData["uploadFileList"] = Gson().toJson(uploadFileList)
        ossUploadPrepareData["toUid"] = ""
        ossUploadPrepareData["feed_type"] = "feed"

        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postOSSUploadPrepare(ossUploadPrepareData)
                .collect { result ->
                    val data = result.getOrNull()
                    if (data != null) {
                        if (data.message != null) {
                            toastText.postValue(Event("uploadPrepare error: ${data.message}"))
                        } else if (data.data != null) {
                            uploadImage.postValue(Event(data.data))
                        }
                    } else {
                        toastText.postValue(Event("response is null"))
                    }
                }
        }
    }

    fun onPostCreateFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postCreateFeed(feedData)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data?.id != null) {
                            over.postValue(Event(true))
                        } else {
                            response.message?.let {
                                toastText.postValue(Event(it))
                            }
                        }
                    } else {
                        toastText.postValue(Event("response is null"))
                    }
                }
        }
    }
}
