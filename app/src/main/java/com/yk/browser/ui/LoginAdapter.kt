package com.yk.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.crypto.CryptoManager
import com.yk.browser.data.SavedLogin
import com.yk.browser.databinding.ItemLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDelete: suspend (SavedLogin) -> Unit
) : RecyclerView.Adapter<LoginAdapter.Holder>() {

    private val items = mutableListOf<SavedLogin>()

    fun submit(list: List<SavedLogin>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemLoginBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLoginBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val login = items[position]
        holder.binding.tvHost.text = login.host
        holder.binding.tvUser.text = login.username.ifEmpty { "(no username)" }
        holder.binding.btnCopy.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                val password = try { CryptoManager.decrypt(login.passwordEncrypted) } catch (e: Exception) { "" }
                withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("password", password))
                    Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        holder.binding.btnDelete.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                onDelete(login)
                withContext(Dispatchers.Main) {
                    items.removeAt(holder.bindingAdapterPosition)
                    notifyItemRemoved(holder.bindingAdapterPosition)
                }
            }
        }
    }
}
