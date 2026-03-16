package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.helpers.ApiEndpoints
import com.example.myapplication.ui.signin.SigninUiState
import com.example.myapplication.ui.signin.SigninViewModel
import com.lexisnexisrisk.threatmetrix.*
import java.io.IOException
import java.util.UUID

class SigninNativeApiActivity : AppCompatActivity() {

    private val viewModel: SigninViewModel by viewModels()

    private lateinit var editUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonSubmitSignin: Button

    // MFA UI Elements
    private lateinit var textViewOTPLabel: TextView
    private lateinit var editTextMFAOTP: EditText
    private lateinit var buttonSubmitOTP: Button

    private lateinit var m_profileHandle: TMXProfilingHandle

    companion object {
        // TODO: Replace with appropriate org id from LNR profile
        private const val LNR_ORG_ID = "<LNR_ORG_ID>"
        private const val TAG = "SAMPLE_CODE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_native_api)

        viewModel.sessionId = UUID.randomUUID().toString()

        initUi()
        observeViewModel()

        if (savedInstanceState == null) {
            setupThreatMetrix()
        }
    }

    private fun initUi() {
        editUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonSubmitSignin = findViewById(R.id.buttonSubmitSignin)

        textViewOTPLabel = findViewById(R.id.textViewOTPLabel)
        editTextMFAOTP = findViewById(R.id.editTextMFAOTP)
        buttonSubmitOTP = findViewById(R.id.buttonSubmitOTP)

        buttonSubmitSignin.setOnClickListener {
            val username = editUsername.text.toString()
            val password = editTextPassword.text.toString()
            if (username.isNotBlank() && password.isNotBlank()) {
                if (::m_profileHandle.isInitialized) m_profileHandle.sendBehavioSecData()
                viewModel.initiateSignin(username)
            } else {
                showToast("Please enter both username and password")
            }
        }

        buttonSubmitOTP.setOnClickListener {
            val mfaOtp = editTextMFAOTP.text.toString()
            if (mfaOtp.isNotBlank()) {
                viewModel.verifyMFAOTP(editUsername.text.toString(), mfaOtp)
            } else {
                showToast("Please enter the MFA code")
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is SigninUiState.Loading -> {
                    buttonSubmitSignin.isEnabled = false
                    showToast(state.message)
                }
                SigninUiState.ReadyForPassword -> {
                    buttonSubmitSignin.isEnabled = true
                    editTextPassword.isEnabled = true
                    // Automatically trigger password submission after challenge is ready
                    viewModel.submitPassword(editUsername.text.toString(), editTextPassword.text.toString())
                }
                SigninUiState.MfaOtpSent -> {
                    buttonSubmitSignin.isEnabled = true
                    showMFAUI()
                    showToast("MFA Code sent!")
                }
                is SigninUiState.Success -> {
                    buttonSubmitSignin.isEnabled = true
                    showToast("Sign In Successful!")
                    Log.d(TAG, "Access Token: ${state.accessToken}")
                }
                is SigninUiState.Error -> {
                    buttonSubmitSignin.isEnabled = true
                    showToast("Error: ${state.message}")
                }
                SigninUiState.Idle -> { /* Do nothing */ }
            }
        }
    }

    private fun showMFAUI() {
        textViewOTPLabel.visibility = View.VISIBLE
        editTextMFAOTP.visibility = View.VISIBLE
        buttonSubmitOTP.visibility = View.VISIBLE
    }

    private fun setupThreatMetrix() {
        try {
            initThreatMetrixSDK()
            doProfile()
        } catch (error: IOException) {
            Log.e(TAG, "ThreatMetrix SDK initialization failed.", error)
        }
    }

    @Throws(IOException::class)
    private fun initThreatMetrixSDK() {
        val config = TMXConfig()
            .setOrgId(LNR_ORG_ID)
            .setFPServer(ApiEndpoints.TMX_FP_SERVER)
            .setContext(applicationContext)
            .setProfileTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .setRegisterForLocationServices(true)
        TMXProfiling.getInstance().init(config)
    }

    private fun doProfile() {
        val options = TMXProfilingOptions().setTMXBehavioSecRunningActivity(this)
        m_profileHandle = TMXProfiling.getInstance().profile(options, CompletionNotifier())
    }

    private inner class CompletionNotifier : TMXEndNotifier {
        override fun complete(result: TMXProfilingHandle.Result) {
            TMXProfiling.getInstance().scanPackages { Log.i(TAG, "Scan completed") }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onRestart() {
        doProfile()
        super.onRestart()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::m_profileHandle.isInitialized) TMXProfiling.getInstance().pauseLocationServices(!hasFocus)
    }
}
