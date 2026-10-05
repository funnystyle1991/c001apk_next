package com.example.c001apk.ui.settings

import android.os.Bundle
import com.example.c001apk.databinding.ActivitySettingsBinding
import com.example.c001apk.ui.base.BaseActivity

/**
 * 设置页面。从 MineFragment 右上角的「设置」图标进入。
 *
 * 内部只承载 [SettingsPreferenceFragment]（不再保留外层 SettingsFragment 包装）。
 * 顶部 MaterialToolbar 由本 Activity 自带，提供返回与标题。
 *
 * 返回一律走系统默认链路，不要在这里接管：
 * - 二级页（外观/推荐流相关/隐私/高级/其他）由 FragmentManager 自带的返回回调出栈，
 *   AndroidX Fragment 1.7 起支持预测性返回，右滑拖动时页面就会跟手预览；
 * - 根页没有可用的返回回调，交回系统 finish，同样是系统预测性返回。
 *
 * 注意：这里**不要**再 addCallback 注册 OnBackPressedCallback。一旦 app 自己接管返回
 * （哪怕只是 popBackStack），系统就不再播放预测性返回动画，右滑时页面不会跟手，
 * 与帖子详情等其它页面的返回手感就不一致了。
 */
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
}