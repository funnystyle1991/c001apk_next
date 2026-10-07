package com.example.c001apk.ui.article

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.c001apk.databinding.ActivityCropImageBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.makeToast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 裁剪页：默认固定比例 1600:719（图文封面），输出精确 1600x719 的 JPEG。
 * 传 EXTRA_OUT_W / EXTRA_OUT_H 可以换比例（头像、主页背景图传 600 / 1080 这种正方形）。
 * 结果通过 RESULT_URI 返回（file:// Uri 字符串）。
 */
class CropImageActivity : BaseActivity<ActivityCropImageBinding>() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_OUT_W = "extra_out_w"
        const val EXTRA_OUT_H = "extra_out_h"
        const val RESULT_URI = "result_uri"
        const val COVER_W = 1600
        const val COVER_H = 719
        private const val MAX_SCALE = 4f

        /** 描边色：画面偏亮用半透明黑，偏暗用白，保证任何图下都看得见框 */
        private const val BORDER_ON_LIGHT = 0xE6000000.toInt()
        private const val BORDER_ON_DARK = 0xFFFFFFFF.toInt()

        /** 亮度阈值与采样密度：框内平均亮度高于它就算「亮画面」 */
        private const val LIGHT_THRESHOLD = 0.55f
        private const val SAMPLE_STEPS = 12
    }

    /** 输出尺寸（决定裁剪框比例）；不传就是图文封面 1600x719 */
    private val outW by lazy { intent.getIntExtra(EXTRA_OUT_W, COVER_W) }
    private val outH by lazy { intent.getIntExtra(EXTRA_OUT_H, COVER_H) }

    private var srcBitmap: Bitmap? = null
    private var scale = 1f
    private var transX = 0f
    private var transY = 0f
    private var overlayLeft = 0f
    private var overlayTop = 0f
    private var minScale = 1f

    private val matrix = Matrix()
    private lateinit var scaleDetector: ScaleGestureDetector
    private var lastX = 0f
    private var lastY = 0f

    private val srcUri by lazy { intent.getStringExtra(EXTRA_URI) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, v.paddingTop + top, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.cancel.setOnClickListener { finish() }
        binding.confirm.setOnClickListener { confirmCrop() }

        // 非默认比例（头像 / 背景图是正方形）时改裁剪框比例，必须在测量前设置
        if (outW != COVER_W || outH != COVER_H) {
            (binding.cropOverlay.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.dimensionRatio = "$outW:$outH"
                binding.cropOverlay.layoutParams = lp
            }
        }

        binding.cropImage.post { loadAndInit() }
    }

    private fun loadAndInit() {
        val uri = try {
            Uri.parse(srcUri)
        } catch (e: Exception) {
            null
        }
        val bmp = try {
            uri?.let { contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } }
        } catch (e: Exception) {
            null
        }
        if (bmp == null) {
            makeToast("无法加载图片")
            finish()
            return
        }
        srcBitmap = bmp
        binding.cropImage.setImageBitmap(bmp)

        val overlayLoc = IntArray(2)
        val imgLoc = IntArray(2)
        binding.cropOverlay.getLocationOnScreen(overlayLoc)
        binding.cropImage.getLocationOnScreen(imgLoc)
        overlayLeft = (overlayLoc[0] - imgLoc[0]).toFloat()
        overlayTop = (overlayLoc[1] - imgLoc[1]).toFloat()

        val cw = binding.cropOverlay.width.toFloat()
        val ch = binding.cropOverlay.height.toFloat()
        minScale = max(cw / bmp.width, ch / bmp.height)
        scale = minScale
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        transX = overlayLeft + (cw - sw) / 2
        transY = overlayTop + (ch - sh) / 2
        applyTransform()
        updateOverlayBorder()
        initGesture()
    }

    private fun applyTransform() {
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(transX, transY)
        binding.cropImage.imageMatrix = matrix
    }

    /**
     * 描边色跟着框内画面走：亮画面（白底图、浅色照片）用半透明黑，
     * 暗画面用白。原来固定白色，裁一张白图就完全看不到框在哪。
     *
     * 采样点是把裁剪框逆变换回原图坐标算的，和 [confirmCrop] 一套映射；
     * 只取 13x13 个点算平均亮度，够用且便宜，所以拖拽结束 / 缩放结束时才刷。
     */
    private fun updateOverlayBorder() {
        val bmp = srcBitmap ?: return
        val depth = if (averageLightness() > LIGHT_THRESHOLD) BORDER_ON_LIGHT else BORDER_ON_DARK
        val width = (1.5f * resources.displayMetrics.density).roundToInt()
        binding.cropOverlay.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(width, depth)
        }
    }

    /** 裁剪框内的平均亮度（0 全黑 ~ 1 全白）；算不出来时按亮画面处理（用深色描边） */
    private fun averageLightness(): Float {
        val bmp = srcBitmap ?: return 1f
        val inv = Matrix()
        if (!matrix.invert(inv)) return 1f
        val cw = binding.cropOverlay.width.toFloat()
        val ch = binding.cropOverlay.height.toFloat()
        if (cw <= 0f || ch <= 0f) return 1f
        var sum = 0f
        var count = 0
        val point = FloatArray(2)
        for (i in 0..SAMPLE_STEPS) {
            for (j in 0..SAMPLE_STEPS) {
                point[0] = overlayLeft + cw * i / SAMPLE_STEPS
                point[1] = overlayTop + ch * j / SAMPLE_STEPS
                inv.mapPoints(point)
                val x = point[0].toInt()
                val y = point[1].toInt()
                if (x < 0 || y < 0 || x >= bmp.width || y >= bmp.height) continue
                val pixel = bmp.getPixel(x, y)
                sum += (0.299f * Color.red(pixel) +
                        0.587f * Color.green(pixel) +
                        0.114f * Color.blue(pixel)) / 255f
                count++
            }
        }
        return if (count == 0) 1f else sum / count
    }

    private fun clamp() {
        val bmp = srcBitmap ?: return
        val cw = binding.cropOverlay.width.toFloat()
        val ch = binding.cropOverlay.height.toFloat()
        scale = scale.coerceIn(minScale, minScale * MAX_SCALE)
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        transX = transX.coerceIn(overlayLeft + cw - sw, overlayLeft)
        transY = transY.coerceIn(overlayTop + ch - sh, overlayTop)
    }

    private fun initGesture() {
        scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cw = binding.cropOverlay.width.toFloat()
                    val ch = binding.cropOverlay.height.toFloat()
                    val cx = overlayLeft + cw / 2
                    val cy = overlayTop + ch / 2
                    val factor = detector.scaleFactor
                    scale *= factor
                    transX = cx - (cx - transX) * factor
                    transY = cy - (cy - transY) * factor
                    clamp()
                    applyTransform()
                    return true
                }

                /** 缩放手势（含双指平移）结束时框内画面才算定下来，这时再刷描边色 */
                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    updateOverlayBorder()
                }
            }
        )

        binding.cropImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        transX += event.x - lastX
                        transY += event.y - lastY
                        lastX = event.x
                        lastY = event.y
                        clamp()
                        applyTransform()
                    }
                }
                // 拖动过程中每帧都采样没必要，抬手时统一刷新一次
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> updateOverlayBorder()
            }
            true
        }
    }

    private fun confirmCrop() {
        val bmp = srcBitmap ?: run {
            makeToast("裁剪失败")
            return
        }
        val inv = Matrix()
        if (!matrix.invert(inv)) {
            makeToast("裁剪失败")
            return
        }
        val cw = binding.cropOverlay.width.toFloat()
        val ch = binding.cropOverlay.height.toFloat()
        val pts = floatArrayOf(
            overlayLeft, overlayTop,
            overlayLeft + cw, overlayTop + ch
        )
        inv.mapPoints(pts)
        val left = pts[0].coerceIn(0f, bmp.width.toFloat())
        val top = pts[1].coerceIn(0f, bmp.height.toFloat())
        val right = pts[2].coerceIn(0f, bmp.width.toFloat())
        val bottom = pts[3].coerceIn(0f, bmp.height.toFloat())
        val cropW = (right - left).roundToInt()
        val cropH = (bottom - top).roundToInt()
        if (cropW <= 0 || cropH <= 0) {
            makeToast("裁剪失败")
            return
        }
        val cropped = Bitmap.createBitmap(bmp, left.toInt(), top.toInt(), cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        if (cropped !== scaled) cropped.recycle()

        val file = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        } catch (e: Exception) {
            makeToast("保存图片失败")
            return
        }
        val data = Intent().putExtra(RESULT_URI, Uri.fromFile(file).toString())
        setResult(RESULT_OK, data)
        finish()
    }
}
