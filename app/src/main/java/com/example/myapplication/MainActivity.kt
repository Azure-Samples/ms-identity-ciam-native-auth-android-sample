package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonSignup: Button = findViewById(R.id.buttonSignup)

        buttonSignup.setOnClickListener {
            val intent = Intent(this, SignupNativeApiActivity::class.java)
            startActivity(intent)
        }
    }
}