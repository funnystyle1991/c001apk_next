package com.example.c001apk.ui.others

import android.annotation.SuppressLint
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.drakeet.about.AbsAboutActivity
import com.drakeet.about.Card
import com.drakeet.about.Category
import com.drakeet.about.Contributor
import com.drakeet.about.License
import com.drakeet.about.Line
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.TransitionAnim
import com.example.c001apk.util.UpdateChecker
import kotlinx.coroutines.launch

class AboutActivity : AbsAboutActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreateHeader(icon: ImageView, slogan: TextView, version: TextView) {
        icon.setImageResource(R.mipmap.ic_launcher)
        slogan.text = applicationInfo.loadLabel(packageManager)
        version.text = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    }

    override fun onItemsCreated(items: MutableList<Any>) {
        // 自定义条目（开关 / 立即检查按钮）注册进 about-page 的 MultiTypeAdapter
        adapter.register(UpdateSwitchItem::class.java, UpdateSwitchBinder())
        adapter.register(UpdateActionItem::class.java, UpdateActionBinder())

        items.add(Category(getString(R.string.about)))
        items.add(Card("fake coolapk"))

        items.add(Category(getString(R.string.about_developer)))
        items.add(
            Contributor(
                R.drawable.cont_author,
                "bggRGjQaUbCoE",
                "Developer & Designer",
                "https://github.com/bggRGjQaUbCoE"
            )
        )
        items.add(Line())
        items.add(
            Contributor(
                R.drawable.cont_klxiaoniu,
                "klxiaoniu",
                "Developer & Collaborator",
                "https://github.com/klxiaoniu"
            )
        )
        items.add(Line())
        items.add(
            Contributor(
                R.drawable.cont_kongwufang,
                "kongwufang",
                "Developer & Maintainer",
                "https://github.com/kongwufang"
            )
        )

        // 更新相关：两个开关 + 两个「立即检查」按钮，插在「开发者」和「反馈」中间
        items.add(
            UpdateSwitchItem(
                getString(R.string.check_stable_update), R.drawable.outline_system_update_24,
                get = { PrefManager.isCheckUpdateStable },
                set = { PrefManager.isCheckUpdateStable = it }
            )
        )
        items.add(
            UpdateSwitchItem(
                getString(R.string.check_beta_update), R.drawable.outline_file_download_24,
                get = { PrefManager.isCheckUpdateBeta },
                set = { PrefManager.isCheckUpdateBeta = it }
            )
        )
        items.add(
            UpdateActionItem(
                getString(R.string.check_update_now_stable), R.drawable.outline_system_update_24
            ) { checkUpdateNow(UpdateChecker.CHANNEL_STABLE) }
        )
        items.add(
            UpdateActionItem(
                getString(R.string.check_update_now_beta), R.drawable.outline_file_download_24
            ) { checkUpdateNow(UpdateChecker.CHANNEL_BETA) }
        )

        items.add(Category(getString(R.string.feedback)))

        // 反馈群组：由 update 分支的 org.json 云端下发，可配多个（改 json 即可，不用发版），
        // 拉到之后插在「反馈」下面、GitHub 卡片上面；拉不到就不显示，不影响页面
        val groupInsertIndex = items.size
        lifecycleScope.launch {
            val links = UpdateChecker.fetchOrgLinks()
            if (links.isEmpty()) return@launch
            items.addAll(
                groupInsertIndex,
                links.map { link ->
                    UpdateActionItem(link.name, R.drawable.ic_chat) {
                        UpdateChecker.openExternal(
                            this@AboutActivity, link.url, "无法打开群组链接"
                        )
                    }
                }
            )
            adapter.notifyDataSetChanged()
        }

        items.add(Card("GitHub\nhttps://github.com/kongwufang/c001apk_next"))

        items.add(Category(getString(R.string.about_open_source)))
        items.add(
            License(
                "kotlin",
                "JetBrains",
                License.APACHE_2,
                "https://github.com/JetBrains/kotlin"
            )
        )
        items.add(License("AndroidX", "Google", License.APACHE_2, "https://source.google.com"))
        items.add(
            License(
                "material-components-android",
                "Google",
                License.APACHE_2,
                "https://github.com/material-components/material-components-android"
            )
        )
        items.add(
            License(
                "RikkaX",
                "RikkaApps",
                License.MIT,
                "https://github.com/RikkaApps/RikkaX"
            )
        )
        items.add(
            License(
                "about-page",
                "drakeet",
                License.APACHE_2,
                "https://github.com/drakeet/about-page"
            )
        )
        items.add(
            License(
                "LSPosed",
                "LSPosed",
                License.GPL_V3,
                "https://github.com/LSPosed/LSPosed"
            )
        )
        items.add(
            License(
                "LibChecker",
                "LibChecker",
                License.APACHE_2,
                "https://github.com/LibChecker/LibChecker"
            )
        )
        items.add(
            License(
                "Hide-My-Applist",
                "Dr-TSNG",
                License.GPL_V3,
                "https://github.com/Dr-TSNG/Hide-My-Applist"
            )
        )
        items.add(License("okhttp", "square", License.APACHE_2, "https://github.com/square/okhttp"))
        items.add(
            License(
                "retrofit",
                "square",
                License.APACHE_2,
                "https://github.com/square/retrofit"
            )
        )
        items.add(
            License(
                "glide",
                "bumptech",
                License.APACHE_2,
                "https://github.com/bumptech/glide"
            )
        )
        items.add(
            License(
                "jBCrypt",
                "jeremyh",
                License.APACHE_2,
                "https://github.com/jeremyh/jBCrypt"
            )
        )
        items.add(
            License(
                "flexbox-layout",
                "google",
                License.APACHE_2,
                "https://github.com/google/flexbox-layout"
            )
        )
        items.add(
            License(
                "glide-transformations",
                "wasabeef",
                License.APACHE_2,
                "https://github.com/wasabeef/glide-transformations"
            )
        )
        items.add(License("jsoup", "jhy", License.MIT, "https://github.com/jhy/jsoup"))
        items.add(
            License(
                "NineGridImageView",
                "plain-dev",
                License.MIT,
                "https://github.com/plain-dev/NineGridImageView"
            )
        )
        items.add(
            License(
                "mojito",
                "mikaelzero",
                License.APACHE_2,
                "https://github.com/mikaelzero/mojito"
            )
        )
        items.add(
            License(
                "CircleIndicator",
                "ongakuer",
                License.APACHE_2,
                "https://github.com/ongakuer/CircleIndicator"
            )
        )
        items.add(
            License(
                "libraries",
                "zhaobozhen",
                License.MIT,
                "https://github.com/zhaobozhen/libraries"
            )
        )
        items.add(
            License(
                "dagger",
                "google",
                License.APACHE_2,
                "https://github.com/google/dagger"
            )
        )
        items.add(
            License(
                "SmoothInputLayout",
                "AlexMofer",
                License.APACHE_2,
                "https://github.com/AlexMofer/SmoothInputLayout"
            )
        )

    }

    /** 立即检查一次更新（关于页的两个按钮） */
    private fun checkUpdateNow(channel: String) {
        Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val info = UpdateChecker.fetchUpdate(channel)
            when {
                info == null ->
                    Toast.makeText(this@AboutActivity, "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show()

                info.isNewer ->
                    // 「不再提示」后刷新列表，让开关条目重新读一遍 PrefManager
                    UpdateChecker.showUpdateDialog(this@AboutActivity, info, channel) {
                        adapter.notifyDataSetChanged()
                    }

                else ->
                    Toast.makeText(this@AboutActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun finish() {
        super.finish()
        TransitionAnim.applyReturn(this)
    }

}
