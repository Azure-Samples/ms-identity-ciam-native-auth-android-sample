package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.ui.signup.SignupUiState
import com.example.myapplication.ui.signup.SignupViewModel
import java.util.UUID

class SignupNativeApiActivity : AppCompatActivity() {

    private val viewModel: SignupViewModel by viewModels()

    private lateinit var editUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonGetCode: Button
    private lateinit var editTextOtp: EditText
    private lateinit var buttonVerifyOTP: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up_native_api)

        viewModel.sessionId = UUID.randomUUID().toString()

        initUi()
        observeViewModel()
    }

    private fun initUi() {
        editUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonGetCode = findViewById(R.id.buttonGetCode)
        editTextOtp = findViewById(R.id.editTextOTP)
        buttonVerifyOTP = findViewById(R.id.buttonVerifyOTP)

        editTextOtp.isEnabled = false
        buttonVerifyOTP.isEnabled = false

        buttonGetCode.setOnClickListener {
            val username = editUsername.text.toString()
            val password = editTextPassword.text.toString()

            if (username.isBlank() || password.isBlank()) {
                showToast("Please enter both username and password")
                return@setOnClickListener
            }
            viewModel.startSignup(username, password)
        }

        buttonVerifyOTP.setOnClickListener {
            val otp = editTextOtp.text.toString()
            if (otp.isBlank()) {
                showToast("Please enter the code")
                return@setOnClickListener
            }
            viewModel.verifyOtp(editUsername.text.toString(), otp)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is SignupUiState.Loading -> {
                    buttonGetCode.isEnabled = false
                    buttonVerifyOTP.isEnabled = false
                    showToast(state.message)
                }
                is SignupUiState.OtpSent -> {
                    buttonGetCode.isEnabled = true
                    editTextOtp.isEnabled = true
                    buttonVerifyOTP.isEnabled = true
                    showToast("OTP Sent! Ready for next step.")
                }
                is SignupUiState.Success -> {
                    buttonVerifyOTP.isEnabled = true
                    showToast("Sign Up Successful!")
                    Log.d("SANJIB_NATIVE", "Access Token: ${state.accessToken}")
                }
                is SignupUiState.Error -> {
                    buttonGetCode.isEnabled = true
                    buttonVerifyOTP.isEnabled = true
                    showToast("Error: ${state.message}")
                }
                SignupUiState.Idle -> { 
                    buttonGetCode.isEnabled = true
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
