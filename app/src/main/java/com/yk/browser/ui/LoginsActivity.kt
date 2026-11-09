package com.yk.browser.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.R
import com.yk.browser.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logins)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val recycler: RecyclerView = findViewById(R.id.recycler)
        val emptyState: LinearLayout = findViewById(R.id.emptyState)
        val db = AppDatabase.get(this)
        val adapter = LoginAdapter(
            this, lifecycleScope,
            onDelete = { login -> db.loginDao().delete(login) },
            onCountChanged = { count ->
                emptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            val logins = withContext(Dispatchers.IO) { db.loginDao().getAll() }
            adapter.submit(logins)
        }
    }
}
