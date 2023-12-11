package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.ActivityMainBinding
import com.microsoft.identity.client.Logger
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AuthClient.initialize(this@MainActivity)

        val emailSignInSignUpFragment = EmailSignInSignUpFragment()
        val emailPasswordSignInSignUpFragment = EmailPasswordSignInSignUpFragment()
        val emailAttributeSignUpFragment = EmailAttributeSignUpFragment()
        val passwordResetFragment = PasswordResetFragment()
        val more = MoreFragment()

        setCurrentFragment(emailSignInSignUpFragment, R.string.title_email_oob_sisu)

        bottom_navigation_view.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.email_oob_sisu -> setCurrentFragment(emailSignInSignUpFragment, R.string.title_email_oob_sisu)
                R.id.email_password_sisu -> setCurrentFragment(emailPasswordSignInSignUpFragment, R.string.title_email_password_sisu)
                R.id.email_attribute_oob_sisu -> setCurrentFragment(emailAttributeSignUpFragment, R.string.title_email_attribute_oob_sisu)
                R.id.email_oob_sspr -> setCurrentFragment(passwordResetFragment, R.string.title_email_oob_sspr)
                R.id.more -> setCurrentFragment(more, R.string.title_more)
            }
            true
        }
    }

    private fun setCurrentFragment(fragment: Fragment, title: Int) {
        supportActionBar?.title = getString(title)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.scenario_fragment, fragment)
            commit()
        }
    }
}
