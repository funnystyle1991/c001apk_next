package net.mikaelzero.mojito.loader.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
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
        // http(s) 一律换成 GlideUrl：Glide 处理裸 Uri 时走的是它自己内置的
        // HttpURLConnection 栈（不经过 GlideProgressSupport 替换过的 OkHttp 加载器，
        // 也不带任何请求头），图片 CDN 的 WAF 会因此回 567，点开大图就黑屏。
        // 换成 GlideUrl 才能落到 OkHttp 上，从而带上 UA 和下载进度回调；
        // 本地 file:// 仍按 Uri 处理（GlideUrl 只认 http(s)）。
        val model: Any =
            if (uri.scheme == "http" || uri.scheme == "https") GlideUrl(uri.toString()) else uri
        requestManager
            .downloadOnly()
            .load(model)
            .onlyRetrieveFromCache(onlyRetrieveFromCache)
            .into(target)
    }

    companion object {
        @JvmOverloads
        fun with(context: Context, okHttpClient: OkHttpClient? = null): GlideImageLoader {
            return GlideImageLoader(context, okHttpClient)
        }
    }
}
