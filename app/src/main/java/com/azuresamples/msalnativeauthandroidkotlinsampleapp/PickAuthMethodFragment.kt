package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PickAuthMethodFragment : NativeAuthFlowDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_pick_auth_method_list, container, false)
        val state = viewModel.uiState.value

        view.findViewById<TextView>(R.id.header_text).setText(viewModel.authMethodPrompt())

        val recyclerView = view.findViewById<RecyclerView>(R.id.authMethodList)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = AuthMethodRecyclerViewAdapter(
            state.authMethods,
            object : OnItemClickListener {
                override fun onItemClick(position: Int) {
                    state.authMethods.getOrNull(position)?.let(viewModel::selectAuthMethod)
                }
            }
        )
        recyclerView.addItemDecoration(
            DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
        )
        view.findViewById<View>(R.id.cancel_flow).setOnClickListener {
            viewModel.cancelFlow()
        }
        return view
    }
}
