package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
            authMethods = (it.getSerializable(Constants.AUTH_METHOD_LIST) as? List<*>)?.filterIsInstance<AuthMethod>()!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pick_auth_method_list, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.authMethodList)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val adapter = AuthMethodRecyclerViewAdapter(authMethods, object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val selectedItem = authMethods.getOrNull(position)

                if (selectedItem == null) {
                    Toast.makeText(requireContext(), getString(R.string.unknown_error_message), Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return
                }

                navigateToVerificationContact(currentState, selectedItem)
            }
        })
        recyclerView.adapter = adapter

        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        return view
    }

    private fun navigateToVerificationContact(nextState: RegisterStrongAuthState, authMethod: AuthMethod) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        bundle.putParcelable(Constants.AUTH_METHOD, authMethod)

        val fragment = StrongAuthVerificationContactFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}