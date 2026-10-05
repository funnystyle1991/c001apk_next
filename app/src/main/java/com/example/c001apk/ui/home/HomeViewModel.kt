package com.example.c001apk.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.HomeMenu
import com.example.c001apk.logic.repository.HomeMenuRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.PrefManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeMenuRepo: HomeMenuRepo,
    private val networkRepo: NetworkRepo,
) : ViewModel() {

    var isInit = true
    var position: Int = 0

    val tabListLiveData: LiveData<List<HomeMenu>> = homeMenuRepo.loadAllListLive()
    val restart = MutableLiveData<Boolean>()

    /** 首页右上角消息入口的红点数字 */
    val unreadCount = MutableLiveData<Int>()

    val defaultList by lazy {
        listOf(
            HomeMenu(0, "关注", true),
            HomeMenu(1, "应用", true),
            HomeMenu(2, "头条", true),
            HomeMenu(3, "热榜", true),
            HomeMenu(4, "话题", true),
            HomeMenu(5, "数码", true),
            HomeMenu(6, "酷图", true)
        )
    }

    fun initTab() {
        viewModelScope.launch(Dispatchers.IO) {
            homeMenuRepo.insertList(defaultList)
        }
    }

    fun updateTab(menuList: List<HomeMenu>) {
        viewModelScope.launch(Dispatchers.IO) {
            homeMenuRepo.updateList(menuList)
            restart.postValue(true)
        }
    }

    /**
     * 拉一次未读数喂给首页红点，顺带把 CookieUtil 里的缓存刷成最新
     * （底部导航的角标也读这份缓存）。未登录直接清零，不发请求。
     */
    fun fetchUnreadCount() {
        if (!PrefManager.isLogin) {
            unreadCount.postValue(0)
            return
        }
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
                        unreadCount.postValue(CookieUtil.unreadTotal)
                    }
                }
        }
    }

}
