package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.microsoft.identity.nativeauth.AuthMethod

interface OnItemClickListener {
    fun onItemClick(position: Int)
}

class AuthMethodRecyclerViewAdapter(
    private val authMethods: List<AuthMethod>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<AuthMethodRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_pick_auth_method, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val authMethod = authMethods[position]
        holder.type.text = authMethod.challengeChannel.uppercase()
        holder.loginHint.text = authMethod.loginHint ?: "No default value"
    }

    override fun getItemCount(): Int = authMethods.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val type: TextView = itemView.findViewById(R.id.type)
        val loginHint: TextView = itemView.findViewById(R.id.login_hint)

        init {
            itemView.setOnClickListener {
                listener.onItemClick(absoluteAdapterPosition)
            }
        }
    }

}