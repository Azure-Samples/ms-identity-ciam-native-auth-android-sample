package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.content.DialogInterface
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels

abstract class NativeAuthFlowDialogFragment : DialogFragment() {
    protected val viewModel: NativeAuthViewModel by activityViewModels()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cancelFlow()
    }
}
