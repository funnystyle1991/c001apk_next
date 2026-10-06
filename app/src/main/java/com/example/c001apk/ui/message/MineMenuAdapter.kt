package com.example.c001apk.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemMineMenuBinding
import com.example.c001apk.ui.collection.CollectionActivity
import com.example.c001apk.ui.follow.FFFListActivity
import com.example.c001apk.ui.history.HistoryActivity
import com.example.c001apk.ui.others.WebViewActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager

/**
 * 「我的」页功能宫格（8 格）：我的动态 / 我的关注 / 我的收藏 / 我的赞 /
 * 我的回复 / 我的常去 / 浏览历史 / 我的装备。
 *
 * 跳转参数沿用原来的 item_message_mine 卡片（FFFListActivity 的 type 取值等都没动）。
 */
class MineMenuAdapter : RecyclerView.Adapter<MineMenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(val binding: ItemMineMenuBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            // 装备 / 浏览历史不需要登录；其余入口登录后才可点
            binding.equipmentLayout.setOnClickListener(this)
            binding.historyLayout.setOnClickListener(this)
            if (PrefManager.isLogin) {
                binding.feedLayout.setOnClickListener(this)
                binding.followLayout.setOnClickListener(this)
                binding.favLayout.setOnClickListener(this)
                binding.likeLayout.setOnClickListener(this)
                binding.replyLayout.setOnClickListener(this)
                binding.freqLayout.setOnClickListener(this)
            }
        }

        override fun onClick(view: View?) {
            when (view?.id) {
                R.id.feedLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "feed")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.followLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "follow")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.favLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<CollectionActivity>(itemView.context) {
                        }

                R.id.likeLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "like")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.replyLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "reply")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.freqLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "recentHistory")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.historyLayout ->
                    IntentUtil.startActivity<HistoryActivity>(itemView.context) {
                        putExtra("type", "browse")
                    }

                // 我的装备：直接进入装备页；「修改/编辑装备」按钮放在该页右上角
                R.id.equipmentLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<WebViewActivity>(itemView.context) {
                            putExtra("url", "https://m.coolapk.com/myDevice/${PrefManager.uid}")
                            putExtra(
                                "editUrl",
                                "https://m.coolapk.com/mp/do?c=product&m=editProductOwner&from=home"
                            )
                        }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemMineMenuBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val lp = binding.root.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            lp.isFullSpan = true
        }
        return MenuViewHolder(binding)
    }

    override fun getItemCount() = 1

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.binding.executePendingBindings()
    }

}
