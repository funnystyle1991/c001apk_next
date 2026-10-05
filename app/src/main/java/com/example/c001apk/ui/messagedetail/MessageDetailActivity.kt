package com.example.c001apk.ui.messagedetail

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c001apk.databinding.ActivityMessageDetailBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.MessageKit
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import dagger.hilt.android.AndroidEntryPoint

/**
 * 私信聊天页（独立二级页，保证右滑返回能跟手预览）。
 *
 * 两个入口：
 * - 会话列表点击：带 ukey + uid + uname；
 * - 别人主页的「私信」圆钮：只有 uid，**从没聊过也能进来**，
 *   发出第一条消息时由服务端隐式建会话。
 */
@AndroidEntryPoint
class MessageDetailActivity : BaseActivity<ActivityMessageDetailBinding>() {

    private val viewModel by viewModels<MessageDetailViewModel>()
    private val adapter by lazy {
        MessageDetailAdapter(
            myUid = PrefManager.uid,
            myAvatar = PrefManager.userAvatar,
            partnerAvatar = intent.getStringExtra("avatar").orEmpty()
        )
    }

    /** 只读会话（酷安小秘书）：能看登录提醒 / 站内信，但不给输入框 */
    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.ukey = intent.getStringExtra("ukey").orEmpty()
        viewModel.uid = intent.getStringExtra("uid").orEmpty()

        initView()
        initObserve()

        viewModel.loadHistory()
        viewModel.markRead()
    }

    private fun initView() {
        // 标题直接在代码里给，跟项目里其它 Toolbar 页保持一致（别走 app:title 的 databinding）
        binding.toolBar.title = intent.getStringExtra("uname").orEmpty()
        binding.toolBar.setNavigationOnClickListener { finish() }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MessageDetailActivity)
            adapter = this@MessageDetailActivity.adapter
        }

        binding.sendBtn.setOnClickListener { send() }
        applyReadOnly()
        applyInputBarInsets()
    }

    /**
     * 会话列表进来会带 readOnly；主页那类只有 uid 的入口则按对方昵称兜底判断，
     * 保证不管从哪条路进小秘书的会话，都发不出消息。
     */
    private fun applyReadOnly() {
        readOnly = intent.getBooleanExtra("readOnly", false) ||
                MessageKit.isSecretaryName(intent.getStringExtra("uname"))
        if (!readOnly) return
        binding.editText.isVisible = false
        binding.sendBtn.isVisible = false
        binding.readOnlyTip.isVisible = true
    }

    /**
     * 输入栏底部内边距：键盘弹起时抬到键盘上方，平时让开导航栏。
     * 取 max 是为了兼容「系统真的 resize 了窗口」和「只发 ime insets」两种情况，避免双重留白。
     */
    private fun applyInputBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.inputBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + 6.dp)
            insets
        }
    }

    private fun initObserve() {
        viewModel.chatData.observe(this) { list ->
            adapter.submitList(list) {
                // diff 完之后再滚，否则位置还对不上旧列表
                binding.recyclerView.post {
                    if (list.isNotEmpty())
                        binding.recyclerView.scrollToPosition(list.size - 1)
                }
            }
        }

        viewModel.toastText.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun send() {
        if (readOnly) return
        val text = binding.editText.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.editText.setText("")
        viewModel.sendMessage(text)
    }

}
