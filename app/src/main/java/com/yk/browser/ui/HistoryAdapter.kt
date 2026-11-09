package com.yk.browser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.R
import com.yk.browser.data.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val items = mutableListOf<HistoryEntry>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    fun submit(list: List<HistoryEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetter: TextView = view.findViewById(R.id.tvLetter)
        val ivFavicon: ImageView = view.findViewById(R.id.ivFavicon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.tvLetter.text = (entry.title.takeIf { it.isNotBlank() } ?: entry.url)
            .firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        FaviconLoader.load(FaviconLoader.hostOf(entry.url), holder.ivFavicon, holder.tvLetter, scope)
        holder.tvTitle.text = entry.title.ifEmpty { entry.url }
        holder.tvUrl.text = entry.url
        holder.tvTime.text = dateFormat.format(Date(entry.visitedAt))
        holder.itemView.setOnClickListener { onOpen(entry) }
    }
}
