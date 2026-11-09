package com.yk.browser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.R
import com.yk.browser.data.Bookmark

class BookmarksAdapter(
    private val onOpen: (Bookmark) -> Unit,
    private val onDelete: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.Holder>() {

    private val items = mutableListOf<Bookmark>()

    fun submit(list: List<Bookmark>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetter: TextView = view.findViewById(R.id.tvLetter)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val bookmark = items[position]
        holder.tvLetter.text = (bookmark.title.takeIf { it.isNotBlank() } ?: bookmark.url)
            .firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvTitle.text = bookmark.title.ifEmpty { bookmark.url }
        holder.tvUrl.text = bookmark.url
        holder.itemView.setOnClickListener { onOpen(bookmark) }
        holder.btnDelete.setOnClickListener { onDelete(bookmark) }
    }
}
