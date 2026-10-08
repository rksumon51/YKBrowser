package com.yk.browser.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yk.browser.data.AppDatabase
import com.yk.browser.databinding.ActivityLoginsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.get(this)
        val adapter = LoginAdapter(this, lifecycleScope) { login ->
            db.loginDao().delete(login)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        lifecycleScope.launch {
            val logins = withContext(Dispatchers.IO) { db.loginDao().getAll() }
            adapter.submit(logins)
        }
    }
}
