package com.example.c001apk.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.c001apk.MyApplication.Companion.context
import com.example.c001apk.ui.app.AppActivity
import com.example.c001apk.ui.carousel.CarouselActivity
import com.example.c001apk.ui.coolpic.CoolPicActivity
import com.example.c001apk.ui.dyh.DyhActivity
import com.example.c001apk.ui.event.EventDetailActivity
import com.example.c001apk.ui.feed.FeedActivity
import com.example.c001apk.ui.others.WebViewActivity
import com.example.c001apk.ui.topic.TopicActivity
import com.example.c001apk.ui.user.UserActivity


object NetWorkUtil {

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network? = cm.activeNetwork
        if (network != null) {
            val nc = cm.getNetworkCapabilities(network)
            nc?.let {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return false
                }
            }
        }
        return false
    }

    fun openLink(context: Context, url: String, title: String?) {
        val replace = url
            .replace("coolmarket://", "/")
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .replace("coolapk1s", "coolapk")
            .replace("coolapk.com", "")
            // coolmarket://<host>/... 这类要单独收尾，两个坑：
            // 1. 换 scheme 时留下了一个前导 "/"，再削掉 www. / coolapk.com 之后就成了
            //    `//feed/74194931`；多这一个斜杠会让下面所有 startsWith 全部落空，
            //    直接掉进 else 弹「unsupported url」——浏览器里「用 App 打开」给的正是
            //    这个形态（coolmarket://www.coolapk.com/feed/74194931?s=...）。
            //    注意只能压「两个及以上」的前导斜杠：`#/feed/xxx`（coolpic）那支不能被碰。
            // 2. com.coolapk.market 也是我们自己在 manifest 里声明支持的 host，
            //    不削掉同样会落到 else。
            .replace("com.coolapk.market", "")
            .replaceFirst(Regex("^//+"), "/")

        if (replace.startsWith("/feed/")) {
            with(replace.indexOfFirst { it == '?' }) {
                IntentUtil.startActivity<FeedActivity>(context) {
                    putExtra(
                        "id",
                        if (this@with != -1) replace.substring(6, this@with)
                        else replace.substring(6)
                    )
                    if (this@with != -1) {
                        replace.indexOf("rid=").let {
                            if (it != -1) {
                                putExtra("rid", replace.substring(it + 4))
                                putExtra("viewReply", true)
                            }
                        }
                    }
                }
            }
        } else if (replace.startsWith("/picture/")) {
            with(replace.indexOfFirst { it == '?' }) {
                IntentUtil.startActivity<FeedActivity>(context) {
                    putExtra(
                        "id",
                        if (this@with != -1) replace.substring(9, this@with)
                        else replace.substring(9)
                    )
                }
            }
        } else if (replace.startsWith("#/feed/")) { // iconLinkGridCard-coolpic
            IntentUtil.startActivity<CarouselActivity>(context) {
                putExtra("title", title)
                putExtra("url", url)
            }
        } else if (replace.startsWith("/apk/") || replace.startsWith("/game/")) {
            IntentUtil.startActivity<AppActivity>(context) {
                putExtra("id", replace.replace("/apk/", "").replace("/game/", ""))
            }
        } else if (replace.startsWith("/u/")) {
            IntentUtil.startActivity<UserActivity>(context) {
                putExtra("id", replace.substring(3))
            }
        } else if (replace.startsWith("/t/")) {
            if (replace.contains("?type=8")) {
                IntentUtil.startActivity<CoolPicActivity>(context) {
                    putExtra("title", replace.substring(3, replace.indexOfFirst { it == '?' }))
                }
            } else {
                with(replace.indexOfFirst { it == '?' }) {
                    val param = if (this@with != -1) replace.substring(3, this@with)
                    else replace.substring(3)
                    IntentUtil.startActivity<TopicActivity>(context) {
                        putExtra("type", "topic")
                        putExtra("url", param)
                        putExtra("title", param)
                    }
                }
            }
        } else if (replace.startsWith("/product/productList")) {
            // 机型对比列表页（同价位 / 同SoC / 同系列）：产品页「参数」tab 里 listCard 卡片
            // 的 url 是服务端下发的**列表 url**，形如
            //   /product/productList?type=series&id=1546&categoryId=1000&title=数字系列&entityTemplate=productSelect
            // 它必须整条交给 /v6/page/dataList（CarouselActivity 走的就是这条），
            // 单页时 CarouselPagerFragment 会自动隐藏 tab 栏、用 title 当标题。
            // 若落到下面的 /product/<id> 分支，substring(9) 会把 "productList?type=..." 整个
            // 当成机型 id 去请求 /v6/product/detail，服务端返回「手机吧ID不能为空」+ data=null，
            // 列表就永远是空的。
            IntentUtil.startActivity<CarouselActivity>(context) {
                putExtra("title", title)
                putExtra("url", replace)
            }
        } else if (replace.startsWith("/product/")) {
            IntentUtil.startActivity<TopicActivity>(context) {
                putExtra("type", "product")
                putExtra("title", title)
                putExtra("id", replace.substring(9))
            }
        } else if (replace.startsWith("/event/") || replace.contains("./event/")) {
            // 众测/活动详情没有 H5，www.coolapk.com/event/<id> 只是下载引导落地页。
            // 改为原生接口 /v6/event/detail（EventDetailActivity）。
            val id = replace.substring(replace.indexOf("/event/") + 7)
                .substringBefore('?')
                .substringBefore('/')
            IntentUtil.startActivity<EventDetailActivity>(context) {
                putExtra("id", id)
            }
        } else if (replace.startsWith("/activity/") || replace.contains("./activity/")) {
            // 活动 H5 落地页（m.coolapk.com/activity/<name>）
            IntentUtil.startActivity<WebViewActivity>(context) {
                putExtra(
                    "url",
                    "https://m.coolapk.com/activity/" + replace.substring(replace.indexOf("/activity/") + 10)
                )
            }
        } else if (replace.startsWith("#/page?url=") || replace.startsWith("/page?url=")) {
            IntentUtil.startActivity<CarouselActivity>(context) {
                putExtra("url", replace.replace("#/page?url=", "").replace("/page?url=", ""))
                putExtra("title", title)
            }
        } else if (replace.startsWith("#/topic/") || replace.startsWith("/topic/")) {
            // 服务端下发的「栏目」url 必须整条交给 /v6/page/dataList，sort / keywords /
            // ratingUI 这些参数才带得过去（和 #/topic/userFollowTagList 同一条链路）。
            // 游戏频道卡片右上角的「榜单」给的就是这种：
            //   #/topic/tagList?keywords=2025游戏%2c…&sort=hot_num&ratingUI=1&title=🎮 热门新游
            // 之前落到末尾的 else，只会弹「unsupported url」。
            // withConfigCard 那对参数是让服务端多下发一张空的 configCard（「默认配置」），
            // 通用列表渲染不了它、会多出一条空白卡片，所以剥掉（实测剥掉后正好 20 条榜单项）。
            IntentUtil.startActivity<CarouselActivity>(context) {
                putExtra(
                    "url",
                    replace
                        .replace(Regex("[&?]withConfigCard=[^&]*"), "")
                        .replace(Regex("[&?]configCardExtraData=[^&]*"), "")
                )
                putExtra("title", title)
            }
        } else if (replace.startsWith("image.coolapk.com")) {
            ImageUtil.startBigImgViewSimple(context, url.http2https)
        } else if (url.startsWith("https://") || url.startsWith("http://")) {
            if (PrefManager.isOpenLinkOutside) {
                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.data = Uri.parse(url)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show()
                    Log.w("error", "Activity was not found for intent, $intent")
                }
            } else {
                IntentUtil.startActivity<WebViewActivity>(context) {
                    putExtra("url", url)
                }
            }
        } else {
            Toast.makeText(context, "unsupported url: $url", Toast.LENGTH_SHORT).show()
            ClipboardUtil.copyText(context, url, false)
        }
    }

    fun openLinkDyh(type: String, mContext: Context, url: String, id: String, title: String?) {
        when (type) {
            "feedRelation" -> {
                IntentUtil.startActivity<DyhActivity>(mContext) {
                    putExtra("id", id)
                    putExtra("title", title)
                }
            }

            "topic", "product" -> {
                IntentUtil.startActivity<TopicActivity>(mContext) {
                    putExtra("type", type)
                    putExtra("title", title)
                    putExtra("url", url)
                    putExtra("id", id)
                }
            }

            else -> openLink(mContext, url, title)
        }
    }

}