package net.mikaelzero.mojito.loader.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import net.mikaelzero.mojito.loader.ImageLoader
import okhttp3.OkHttpClient
import java.io.File
import kotlin.concurrent.thread

open class GlideImageLoader private constructor(val context: Context, okHttpClient: OkHttpClient?) : ImageLoader {
    private val requestManager = Glide.with(context)
    private val flyingRequestTargets: MutableMap<Int, ImageDownloadTarget> = hashMapOf()

    init {
        GlideProgressSupport.init(Glide.get(context), okHttpClient)
    }

    override fun loadImage(requestId: Int, uri: Uri, onlyRetrieveFromCache: Boolean, callback: ImageLoader.Callback) {
        val target = object : ImageDownloadTarget(uri.toString()) {
            override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                super.onResourceReady(resource, transition)
                callback.onSuccess(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                callback.onFail(GlideLoaderException(errorDrawable))
            }

            override fun onDownloadStart() {
                callback.onStart()
            }

            override fun onProgress(progress: Int) {
                callback.onProgress(progress)
            }

            override fun onDownloadFinish() {
                callback.onFinish()
            }
        }
        synchronized(this) {
            flyingRequestTargets[requestId] = target
        }
        downloadImageInto(uri, target, onlyRetrieveFromCache)
    }

    @Synchronized
    override fun cancel(requestId: Int) {
        flyingRequestTargets.remove(requestId)?.let(requestManager::clear)
    }

    @Synchronized
    override fun cancelAll() {
        flyingRequestTargets.values.forEach(requestManager::clear)
        flyingRequestTargets.clear()
    }

    override fun prefetch(uri: Uri) {
        val target = PrefetchTarget()
        downloadImageInto(uri, target, false)
    }

    override fun cleanCache() {
        Glide.get(context).apply {
            clearMemory()
            thread { clearDiskCache() }
        }
    }

    private fun downloadImageInto(uri: Uri, target: Target<File>, onlyRetrieveFromCache: Boolean) {
        requestManager
            .downloadOnly()
            .load(wrapWithHeaders(uri))
            .onlyRetrieveFromCache(onlyRetrieveFromCache)
            .into(target)
    }

    // image.coolapk.com 在腾讯云 EdgeOne 上开了 UA 防盗链（非 CoolMarket UA 一律回 567 拦截页），
    // 裸 Uri 请求没有这个头，全屏看图会下载失败（黑屏）；带上后缓存 key 也和 showIMG 一致，能直接复用缩略图缓存
    private fun wrapWithHeaders(uri: Uri): Any {
        if (uri.scheme != "http" && uri.scheme != "https") return uri
        val headers = headerProvider?.invoke() ?: return uri
        val builder = LazyHeaders.Builder()
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return GlideUrl(uri.toString(), builder.build())
    }

    companion object {
        // 请求头在每次下载时求值：设备参数（MODEL/BRAND 等）由 PrefManager 运行期写入，
        // 早绑定会把 UA 冻结在 Application 启动时的空值上
        var headerProvider: (() -> Map<String, String>)? = null

        @JvmOverloads
        fun with(context: Context, okHttpClient: OkHttpClient? = null): GlideImageLoader {
            return GlideImageLoader(context, okHttpClient)
        }
    }
}
