package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.AuthMethodItemBinding
import com.microsoft.identity.nativeauth.AuthMethod

class AuthMethodAdapter(
    private val methods: List<AuthMethod>,
    private val onItemClick: (AuthMethod) -> Unit
) :
    RecyclerView.Adapter<AuthMethodAdapter.ViewHolder>() {

    class ViewHolder(private val binding: AuthMethodItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(method: AuthMethod, onItemClick: (AuthMethod) -> Unit) {
            binding.methodNameTextView.text = method.challengeChannel

            itemView.setOnClickListener { onItemClick(method) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AuthMethodItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(methods[position], onItemClick)
    }

    override fun getItemCount() = methods.size
}