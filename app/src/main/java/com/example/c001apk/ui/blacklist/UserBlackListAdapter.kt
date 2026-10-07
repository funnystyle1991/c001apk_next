package com.example.c001apk.ui.blacklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.BlackListUser
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.VerifyBadge

/** 云端用户黑名单列表（头像 + 昵称 + UID + 移除） */
class UserBlackListAdapter :
    ListAdapter<BlackListUser, UserBlackListAdapter.ViewHolder>(UserBlackListDiffCallback()) {

    private var onItemClick: ((BlackListUser) -> Unit)? = null
    private var onRemoveClick: ((BlackListUser) -> Unit)? = null

    fun setOnItemClickListener(
        onItemClick: (BlackListUser) -> Unit,
        onRemoveClick: (BlackListUser) -> Unit,
    ) {
        this.onItemClick = onItemClick
        this.onRemoveClick = onRemoveClick
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val verifyBadge: ImageView = view.findViewById(R.id.verifyBadge)
        val username: TextView = view.findViewById(R.id.username)
        val uid: TextView = view.findViewById(R.id.uid)
        val remove: ImageView = view.findViewById(R.id.remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_black_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = currentList[position]
        ImageUtil.showIMG(holder.avatar, user.userAvatar)
        VerifyBadge.applyTo(holder.verifyBadge, user.uid, user.verifyIcon, user.verifyStatus)
        holder.username.text = user.name
        holder.uid.text = "UID: ${user.uid.orEmpty()}"
        holder.itemView.setOnClickListener { onItemClick?.invoke(user) }
        holder.remove.setOnClickListener { onRemoveClick?.invoke(user) }
    }
}

class UserBlackListDiffCallback : DiffUtil.ItemCallback<BlackListUser>() {
    override fun areItemsTheSame(oldItem: BlackListUser, newItem: BlackListUser): Boolean =
        oldItem.uid == newItem.uid

    override fun areContentsTheSame(oldItem: BlackListUser, newItem: BlackListUser): Boolean =
        oldItem == newItem
}
