package com.yk.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.yk.browser.R
import com.yk.browser.crypto.CryptoManager
import com.yk.browser.data.SavedLogin
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

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHost: TextView = view.findViewById(R.id.tvHost)
        val tvUser: TextView = view.findViewById(R.id.tvUser)
        val btnCopy: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnCopy)
        val btnDelete: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_login, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val login = items[position]
        holder.tvHost.text = login.host
        holder.tvUser.text = login.username.ifEmpty { "(no username)" }
        holder.btnCopy.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                val password = try { CryptoManager.decrypt(login.passwordEncrypted) } catch (e: Exception) { "" }
                withContext(Dispatchers.Main) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("password", password))
                    Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        holder.btnDelete.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                onDelete(login)
                withContext(Dispatchers.Main) {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        items.removeAt(pos)
                        notifyItemRemoved(pos)
                    }
                }
            }
        }
    }
}
