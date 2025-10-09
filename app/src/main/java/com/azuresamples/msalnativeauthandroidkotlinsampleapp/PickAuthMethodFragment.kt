package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState

class PickAuthMethodFragment : Fragment() {

    private var authMethods: List<AuthMethod> = emptyList()
    private lateinit var currentState: RegisterStrongAuthState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            currentState = (it.getParcelable(Constants.STATE) as? RegisterStrongAuthState)!!
            authMethods = it.getSerializable(Constants.AUTH_METHOD_LIST) as List<AuthMethod>

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pick_auth_method_list, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.authMethodList)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = AuthMethodRecyclerViewAdapter(authMethods)

        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        return view
    }
}