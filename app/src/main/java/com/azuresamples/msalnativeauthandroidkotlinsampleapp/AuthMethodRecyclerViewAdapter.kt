package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentPickAuthMethodBinding
import com.microsoft.identity.nativeauth.AuthMethod

class AuthMethodRecyclerViewAdapter(
    private val authMethods: List<AuthMethod>
) : RecyclerView.Adapter<AuthMethodRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            FragmentPickAuthMethodBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val authMethod = authMethods[position]
        holder.type.text = authMethod.challengeChannel
        holder.loginHint.text = authMethod.loginHint
    }

    override fun getItemCount(): Int = authMethods.size

    inner class ViewHolder(binding: FragmentPickAuthMethodBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val type: TextView = binding.type
        val loginHint: TextView = binding.loginHint
    }

}