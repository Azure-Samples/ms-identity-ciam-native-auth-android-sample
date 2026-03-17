package com.example.myapplication

import android.app.Application
import android.util.Log
import com.example.myapplication.human.HumanManager

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Log.d("SAMPLE_CODE", "Application onCreate called")
        HumanManager.start(this)
    }
}