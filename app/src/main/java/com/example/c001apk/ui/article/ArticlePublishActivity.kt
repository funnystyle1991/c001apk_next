package com.example.c001apk.ui.article

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.c001apk.MyApplication
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityArticlePublishBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.ImageUtil.getImageDimensionsAndMD5
import com.example.c001apk.util.ImageUtil.toHex
import com.example.c001apk.util.makeToast
import com.example.c001apk.util.ossUpload
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 发布图文（酷安「图文」= type feed + is_html_article=1）。
 * 结构：标题 + 封面（固定 1600:719 裁剪）+ 正文 + 查看权限。
 *
 * 正文是一个整块的输入框：图片以缩略图的形式**插在光标处**，跟着文字走，
 * 编辑体验和写一条带图的动态一样。官方要的是「text / image 块交替」的 message
 * 数组，这个转换放在发布时做（见 [buildMsgList]），输入过程中不维护块结构。
 */
@AndroidEntryPoint
class ArticlePublishActivity : BaseActivity<ActivityArticlePublishBinding>() {

    private val viewModel: ArticlePublishViewModel by viewModels()

    private var coverUri: Uri? = null
    private var coverMd5Byte: ByteArray? = null
    private var coverMd5 = ""
    private var coverName = ""

    private lateinit var pickCoverLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickBodyLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var cropLauncher: ActivityResultLauncher<Intent>

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, v.paddingTop + top, v.paddingRight, v.paddingBottom)
            insets
        }

        initLauncher()
        initView()
        initObserve()
    }

    private fun initView() {
        binding.back.setOnClickListener { finish() }
        binding.coverContainer.setOnClickListener { pickCover() }
        binding.addImage.setOnClickListener { pickBody() }
        binding.publish.setOnClickListener { publish() }
    }

    private fun initLauncher() {
        pickCoverLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    cropLauncher.launch(
                        Intent(this, CropImageActivity::class.java)
                            .putExtra(CropImageActivity.EXTRA_URI, uri.toString())
                    )
                }
            }
        cropLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.getStringExtra(CropImageActivity.RESULT_URI)
                        ?.let { Uri.parse(it) }
                    if (uri != null) {
                        coverUri = uri
                        coverName = "${UUID.randomUUID().toString().replace("-", "")}.jpg"
                        val res = getImageDimensionsAndMD5(contentResolver, uri)
                        coverMd5Byte = res.second
                        coverMd5 = res.second?.toHex() ?: ""
                        Glide.with(this).load(uri).into(binding.coverImage)
                        binding.coverImage.isVisible = true
                        binding.coverHint.isVisible = false
                    }
                }
            }
        pickBodyLauncher =
            registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
                for (uri in uris) {
                    if (bodyImages().size >= MAX_BODY_IMAGES) {
                        makeToast("正文最多插入${MAX_BODY_IMAGES}张图片")
                        break
                    }
                    insertImage(uri)
                }
            }
    }

    private fun initObserve() {
        viewModel.toastText.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                closeDialog()
                makeToast(it)
            }
        }
        viewModel.over.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                closeDialog()
                makeToast("发布成功")
                finish()
            }
        }
        viewModel.uploadImage.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { responseData ->
                val prefix = responseData.uploadPrepareInfo.uploadImagePrefix
                val fileInfo = responseData.fileInfo

                // message JSON 数组：text/image 块交错；fileInfo[0]=封面，fileInfo[1..]=正文图。
                // 图片顺序直接从输入框里现算（谁在文本里靠前谁编号小），不再维护块列表
                val images = bodyImages().map { it.first.block }
                viewModel.feedData["message"] =
                    Gson().toJson(buildMsgList(prefix, fileInfo.map { it.uploadFileName }))
                viewModel.feedData["message_cover"] = prefix + "/" + fileInfo[0].uploadFileName

                // 上传列表与 uploadFileList 顺序一致：封面 + 正文图
                val uriList = ArrayList<Uri>().apply {
                    add(coverUri!!)
                    images.forEach { add(it.uri) }
                }
                val typeList = ArrayList<String>().apply {
                    add("image/jpeg")
                    images.forEach { add(it.type) }
                }
                val md5List = ArrayList<ByteArray?>().apply {
                    add(coverMd5Byte)
                    images.forEach { add(it.md5Byte) }
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    ossUpload(
                        this@ArticlePublishActivity, responseData, uriList, typeList, md5List,
                        iOnSuccess = { index ->
                            if (index == uriList.lastIndex) {
                                viewModel.onPostCreateFeed()
                            }
                        },
                        iOnFailure = {
                            closeDialog()
                            makeToast("图片上传失败")
                        },
                        closeDialog = { closeDialog() }
                    )
                }
            }
        }
    }

    private fun pickCover() {
        pickCoverLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun pickBody() {
        pickBodyLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** 正文里已插入的图，按在文本里出现的先后排序 */
    private fun bodyImages(): List<Pair<ArticleImageSpan, Int>> {
        val text = binding.articleBody.text ?: return emptyList()
        if (text.isEmpty()) return emptyList()
        return text.getSpans(0, text.length, ArticleImageSpan::class.java)
            .map { it to text.getSpanStart(it) }
            .sortedBy { it.second }
    }

    /**
     * 把图片插到**当前光标处**：插入一个 [PLACEHOLDER] 占位符，再给它挂上缩略图 span。
     * 之后接着打字，图片就留在原地；想换位置就直接删掉它（退格删掉那个字符）再重新插。
     */
    private fun insertImage(uri: Uri) {
        val res = getImageDimensionsAndMD5(contentResolver, uri)
        val md5Byte = res.second
        val width = res.first?.first ?: 0
        val height = res.first?.second ?: 0
        val type = res.first?.third ?: "image/jpeg"
        val ext = if (type.startsWith("image/")) type.substringAfterLast("/") else "jpg"

        val thumbnail = decodeThumbnail(uri) ?: run {
            makeToast("图片读取失败")
            return
        }
        val block = ArticleBlock.Image(
            uri = uri,
            name = "${UUID.randomUUID().toString().replace("-", "")}.$ext",
            resolution = "${width}x${height}",
            md5 = md5Byte?.toHex() ?: "",
            type = type,
            md5Byte = md5Byte,
        )

        val edit = binding.articleBody
        val editable = edit.text ?: Editable.Factory.getInstance().newEditable("").also {
            edit.setText(it)
        }
        val at = edit.selectionStart.coerceIn(0, editable.length)
        editable.insert(at, PLACEHOLDER)
        editable.setSpan(
            ArticleImageSpan(thumbnail, block),
            at,
            at + PLACEHOLDER.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // 光标落到图片之后，继续选图就是依次往后排
        edit.setSelection(at + PLACEHOLDER.length)
    }

    /**
     * 输入框里显示的缩略图：固定 140dp 高、宽度按原图比例。
     * 竖长图会窄一些、横图会宽一些，都不会把一行撑满整屏。
     *
     * 解码时先按比例降采样再精确缩放，避免原图（可能几千万像素）直接进内存。
     */
    private fun decodeThumbnail(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetHeight = (THUMB_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val targetWidth =
            (targetHeight.toFloat() * decoded.width / decoded.height).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        // BitmapDrawable 会按「目标密度 / bitmap 密度」再缩一次，这里把密度对齐成屏幕密度，
        // 尺寸就正好是上面算出来的像素值
        scaled.density = resources.displayMetrics.densityDpi
        return scaled
    }

    /**
     * 把输入框里的「文字 + 内嵌图片」还原成官方要的 message 结构：
     * 以每个图片占位符为切点，切点前的文字攒成一个 text 块，图片本身是一个 image 块。
     * [fileNames] 是上传后拿到的文件名（`[0]` 是封面），正文图从 `[1]` 起按出现顺序对应。
     */
    private fun buildMsgList(prefix: String, fileNames: List<String>): List<Map<String, String>> {
        val text = binding.articleBody.text ?: return emptyList()
        val msgList = ArrayList<Map<String, String>>()
        var cursor = 0
        var bodyIndex = 0
        bodyImages().forEach { (span, start) ->
            val chunk = text.subSequence(cursor, start).toString().trim()
            if (chunk.isNotEmpty()) {
                msgList.add(mapOf("type" to "text", "message" to chunk))
            }
            msgList.add(
                mapOf(
                    "type" to "image",
                    "url" to prefix + "/" + fileNames.getOrNull(1 + bodyIndex),
                    "description" to span.block.description
                )
            )
            bodyIndex++
            cursor = text.getSpanEnd(span)
        }
        val tail = text.subSequence(cursor, text.length).toString().trim()
        if (tail.isNotEmpty()) {
            msgList.add(mapOf("type" to "text", "message" to tail))
        }
        return msgList
    }

    /**
     * 正文里的图片占位：用一个 [PLACEHOLDER] 字符占地，绘制时显示缩略图。
     *
     * 图片的元数据（上传文件名 / 分辨率 / md5 / 原图 uri）挂在这个 span 上 ——
     * 用户在输入框里怎么改文字都不影响它，发布时按 span 的位置把富文本切回块结构。
     */
    private class ArticleImageSpan(
        thumbnail: Bitmap,
        val block: ArticleBlock.Image,
    ) : ImageSpan(MyApplication.context, thumbnail, DynamicDrawableSpan.ALIGN_BOTTOM)

    private fun publish() {
        val title = binding.articleTitle.text.toString().trim()
        if (title.isEmpty()) {
            makeToast("请输入标题")
            return
        }
        if (coverUri == null) {
            makeToast("请选择封面图")
            return
        }
        val images = bodyImages().map { it.first.block }
        if (binding.articleBody.text.isNullOrBlank() && images.isEmpty()) {
            makeToast("请输入正文")
            return
        }

        // uploadFileList：封面 + 正文图
        val uploadFiles = ArrayList<ArticleUploadFile>()
        uploadFiles.add(ArticleUploadFile(coverName, "1600x719", coverMd5, 0))
        images.forEach {
            uploadFiles.add(ArticleUploadFile(it.name, it.resolution, it.md5, 0))
        }

        viewModel.feedData.apply {
            put("id", "")
            put("type", "feed")
            put("status", "1")
            put("publish_status", if (binding.checkBox.isChecked) "1" else "0")
            put("message_title", title)
            put("is_html_article", "1")
            put("pic", "")
        }

        viewModel.onPostOSSUploadPrepare(uploadFiles)
        showDialog()
    }

    @SuppressLint("InflateParams")
    private fun showDialog() {
        dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_MaterialAlertDialog_Rounded
        ).apply {
            setView(
                LayoutInflater.from(this@ArticlePublishActivity)
                    .inflate(R.layout.dialog_refresh, null, false)
            )
            setCancelable(false)
        }.create()
        dialog?.show()
    }

    private fun closeDialog() {
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        /** 图片占位符：U+FFFC OBJECT REPLACEMENT CHARACTER，文本里一个字符代表一张图 */
        private const val PLACEHOLDER = "\uFFFC"

        /** 服务端上限：正文最多 20 张图 */
        private const val MAX_BODY_IMAGES = 20

        /** 输入框里缩略图的显示高度（宽度按原图比例算） */
        private const val THUMB_HEIGHT_DP = 140f
    }
}
