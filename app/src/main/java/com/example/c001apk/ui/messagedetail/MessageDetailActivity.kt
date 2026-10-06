package com.example.c001apk.ui.messagedetail

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityMessageDetailBinding
import com.example.c001apk.logic.model.OSSUploadPrepareModel
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.ImageUtil.getImageDimensionsAndMD5
import com.example.c001apk.util.ImageUtil.toHex
import com.example.c001apk.util.MessageKit
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import com.example.c001apk.util.ossUpload
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

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
            partnerAvatar = intent.getStringExtra("avatar").orEmpty(),
            // 图片消息的地址要现问 showImage 换，只能在 ViewModel 里做（那边有作用域 + 缓存）
            loadPic = { id, onReady -> viewModel.loadMessagePic(id, onReady) }
        )
    }

    /** 只读会话（酷安小秘书）：能看登录提醒 / 站内信，但不给输入框 */
    private var readOnly = false

    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    /** 待上传的图片。私信一次只发一张，选完立刻进上传流程，所以只留一份 */
    private var picUri: Uri? = null
    private var picType = ""
    private var picMd5: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.ukey = intent.getStringExtra("ukey").orEmpty()
        viewModel.uid = intent.getStringExtra("uid").orEmpty()

        initPhotoPick()
        initView()
        initObserve()

        viewModel.loadHistory()
        viewModel.markRead()
    }

    /**
     * 选图。私信一次只发一张，所以用单选的 [ActivityResultContracts.PickVisualMedia]
     * （系统相册，不用申请读存储权限），跟回复页用同一套。
     */
    private fun initPhotoPick() {
        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPickImage(uri)
        }
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
        binding.imageBtn.setOnClickListener { pickImage() }
        applyReadOnly()
        applyInputBarInsets()
    }

    private fun pickImage() {
        if (readOnly) return
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.anim_bottom_sheet_slide_up, R.anim.anim_bottom_sheet_slide_down
        )
        try {
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                options
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 选完图先算尺寸和 md5，交给 [MessageDetailViewModel.prepareImageUpload] 换 OSS 上传凭证；
     * 真正的直传在 [initObserve] 里 uploadImage 的回调中做（跟回复页同一套 OSS 流程）。
     */
    private fun onPickImage(uri: Uri) {
        val result = getImageDimensionsAndMD5(contentResolver, uri)
        val width = result.first?.first ?: 0
        val height = result.first?.second ?: 0
        val type = result.first?.third ?: ""
        if (width == 0 || height == 0) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
            return
        }
        picUri = uri
        picType = type
        picMd5 = result.second
        viewModel.prepareImageUpload(
            OSSUploadPrepareModel(
                name = "${UUID.randomUUID().toString().replace("-", "")}." +
                        if (type.startsWith("image/")) type.substring(6) else type,
                // 服务端拿这个拼对象名（`...@宽x高.png`），必须是原图真实尺寸
                resolution = "${width}x${height}",
                md5 = result.second?.toHex() ?: "",
            )
        )
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
        binding.imageBtn.isVisible = false
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

        // 上传凭证就绪 → 直传 OSS → 传完把服务端分配的对象名当 message_pic 发出去
        viewModel.uploadImage.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { prepared ->
                val uri = picUri ?: return@let
                lifecycleScope.launch(Dispatchers.IO) {
                    ossUpload(
                        this@MessageDetailActivity, prepared, listOf(uri),
                        listOf(picType), listOf(picMd5),
                        iOnSuccess = {
                            viewModel.sendImageMessage(
                                prepared.fileInfo.firstOrNull()?.uploadFileName.orEmpty()
                            )
                        },
                        iOnFailure = {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MessageDetailActivity, "图片上传失败", Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        // 聊天页没有上传进度弹窗，不用关
                        closeDialog = { }
                    )
                }
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
