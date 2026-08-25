package com.example.btreceivecall.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.btreceivecall.databinding.ActivityPrivacyPolicyBinding
import com.example.btreceivecall.utils.LocaleHelper

/**
 * In-app activity displaying Privacy Policy & Terms of Service with full TalkBack screen reader accessibility
 * and multilingual localization.
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyPolicyBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackPrivacy.setOnClickListener {
            finish()
        }
    }
}
