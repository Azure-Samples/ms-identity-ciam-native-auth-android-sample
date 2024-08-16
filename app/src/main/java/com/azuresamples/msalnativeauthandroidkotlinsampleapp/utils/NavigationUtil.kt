package com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.PasswordResetCodeFragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.PasswordResetNewPasswordFragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.R
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.SignInCodeFragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.SignInMFAFragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.SignUpCodeFragment
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState

class NavigationUtil(private val activity: Activity) {
    companion object {
        const val STATE = "state"
    }

    fun navigateToSignUpCode(nextState: SignUpCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(STATE, nextState)
        val fragment = SignUpCodeFragment()
        fragment.arguments = bundle

        execute(fragment)
    }

    fun navigateToSignInCode(signInstate: SignInCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(STATE, signInstate)
        val fragment = SignInCodeFragment()
        fragment.arguments = bundle

        execute(fragment)
    }

    fun navigateToSignInMFA(nextState: MFARequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(STATE, nextState)
        val fragment = SignInMFAFragment()
        fragment.arguments = bundle

        execute(fragment)
    }

    fun navigateToResetPasswordPasswordFragment(nextState: ResetPasswordPasswordRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(STATE, nextState)
        val fragment = PasswordResetNewPasswordFragment()
        fragment.arguments = bundle

        execute(fragment)
    }

    fun navigateToResetPasswordCodeFragment(nextState: ResetPasswordCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(STATE, nextState)
        val fragment = PasswordResetCodeFragment()
        fragment.arguments = bundle

        execute(fragment)
    }

    private fun execute(fragment: Fragment) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(fragment::class.java.name)
                .replace(R.id.scenario_fragment, fragment)
                .commit()
        } else {
            // Handle the case where the activity is not a FragmentActivity
            throw IllegalStateException("Activity must be a FragmentActivity to perform this navigation")
        }
    }

    fun finish(index: Int = 0) {
        if (activity is FragmentActivity) {
            val fragmentManager = activity.supportFragmentManager
            val name: String? = fragmentManager.getBackStackEntryAt(index).name
            fragmentManager.popBackStackImmediate(name, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } else {
            // Handle the case where the activity is not a FragmentActivity
            throw IllegalStateException("Activity must be a FragmentActivity to perform this navigation")
        }
    }
}