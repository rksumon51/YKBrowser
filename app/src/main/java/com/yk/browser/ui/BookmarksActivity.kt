package com.yk.browser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.MainActivity
import com.yk.browser.R
import com.yk.browser.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksActivity : AppCompatActivity() {

    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val recycler: RecyclerView = findViewById(R.id.recycler)
        val emptyState: LinearLayout = findViewById(R.id.emptyState)
        val db = AppDatabase.get(this)
        adapter = BookmarksAdapter(
            onOpen = { bookmark ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.url), this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            },
            onDelete = { bookmark ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.bookmarkDao().deleteByUrl(bookmark.url)
                    val items = db.bookmarkDao().getAll()
                    withContext(Dispatchers.Main) {
                        adapter.submit(items)
                        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { db.bookmarkDao().getAll() }
            adapter.submit(items)
            emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
