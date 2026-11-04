package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UnifiedFlowNavigationTest {

    @Test
    fun launchesHomeWithV2Selected() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.home_screen)).check(matches(isDisplayed()))
            onView(withId(R.id.use_v2_checkbox)).check(matches(isChecked()))
            onView(withId(R.id.password_input_layout))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun emptyUsernamesReachSdkValidationForAllEntryPoints() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitUiState(scenario) { !it.busy && !it.flowActive }

            for (actionId in listOf(R.id.sign_in, R.id.sign_up, R.id.reset_password)) {
                scenario.onActivity { activity ->
                    activity.findViewById<android.widget.EditText>(R.id.email_text).text.clear()
                    val action = activity.findViewById<View>(actionId)
                    assertTrue("An empty username must not disable the action", action.isEnabled)
                    action.performClick()
                }
                awaitUiState(scenario) { it.status.startsWith("invalidUsername:") && !it.busy }
                onView(withId(R.id.signed_out_status_text))
                    .check(matches(withText(startsWith("invalidUsername:"))))
            }
        }
    }

    @Test
    fun initialPasswordRemainsAvailableWhenSwitchingBetweenApis() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[NativeAuthViewModel::class.java]
                    .setUseNativeAuthV2(false)
            }
            onView(withId(R.id.password_input_layout))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

            scenario.onActivity { activity ->
                activity.findViewById<android.widget.EditText>(R.id.password_text)
                    .setText("password")
                ViewModelProvider(activity)[NativeAuthViewModel::class.java]
                    .setUseNativeAuthV2(true)
            }
            onView(withId(R.id.password_label))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.password_input_layout))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            scenario.onActivity { activity ->
                assertEquals("password",
                    activity.findViewById<android.widget.EditText>(R.id.password_text).text.toString())
            }
        }
    }

    @Test
    fun codeEntryLayoutsKeepCancelNearVerification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
            val density = context.resources.displayMetrics.density
            for ((layoutId, verifyId) in listOf(
                R.layout.fragment_code to R.id.verify_code,
                R.layout.fragment_mfa_challenge to R.id.verify_challenge
            )) {
                for (heightDp in listOf(480, 800)) {
                    val parent = FrameLayout(context)
                    val content = LayoutInflater.from(context).inflate(layoutId, parent, false)
                    parent.addView(content)
                    parent.measure(
                        View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY)
                    )
                    parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)

                    val verify = content.findViewById<View>(verifyId)
                    val status = content.findViewById<View>(R.id.flow_status)
                    val cancel = content.findViewById<View>(R.id.cancel_flow)
                    val gapDp = (cancel.top - verify.bottom - status.height) / density
                    assertTrue(
                        "${context.resources.getResourceEntryName(layoutId)} at ${heightDp}dp: " +
                            "Verify and Cancel have ${gapDp}dp of spacing around the status",
                        gapDp in 0f..30f
                    )
                }
            }

            private fun awaitUiState(
                scenario: ActivityScenario<MainActivity>,
                predicate: (NativeAuthUiState) -> Boolean
            ) {
                val ready = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val viewModel = ViewModelProvider(activity)[NativeAuthViewModel::class.java]
                    activity.lifecycleScope.launch {
                        viewModel.uiState.first(predicate)
                        ready.countDown()
                    }
                }
                assertTrue("Timed out waiting for the authentication UI state", ready.await(10, TimeUnit.SECONDS))
            }
        }
    }
}
