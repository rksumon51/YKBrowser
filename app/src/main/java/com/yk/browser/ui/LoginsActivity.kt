package com.yk.browser.ui

import android.os.Bundle
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
        val db = AppDatabase.get(this)
        val adapter = LoginAdapter(this, lifecycleScope) { login ->
            db.loginDao().delete(login)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            val logins = withContext(Dispatchers.IO) { db.loginDao().getAll() }
            adapter.submit(logins)
        }
    }
}
