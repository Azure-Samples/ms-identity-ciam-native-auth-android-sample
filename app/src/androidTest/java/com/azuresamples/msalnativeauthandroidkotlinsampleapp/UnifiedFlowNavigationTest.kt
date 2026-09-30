package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnifiedFlowNavigationTest {

    @Test
    fun launchesHomeWithV2Selected() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.home_screen)).check(matches(isDisplayed()))
            onView(withId(R.id.use_v2_checkbox)).check(matches(isChecked()))
        }
    }
}
