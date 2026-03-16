package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonSignin: Button = findViewById(R.id.buttonSignin) // Assuming the button ID is buttonSignin

        buttonSignin.setOnClickListener {
            val intent = Intent(this, SigninNativeApiActivity::class.java)
            startActivity(intent)
        }
    }
}