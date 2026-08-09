package com.example.btreceivecall.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.btreceivecall.databinding.ActivityPrivacyPolicyBinding
import com.example.btreceivecall.utils.LocaleHelper

/**
 * In-app activity displaying Privacy Policy & Terms of Conditions for TalkBack screen reader accessibility.
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

        binding.tvPrivacyContent.text = getPrivacyPolicyText()

        binding.btnBackPrivacy.setOnClickListener {
            finish()
        }
    }

    private fun getPrivacyPolicyText(): String {
        return """
            PRIVACY POLICY & TERMS OF SERVICE
            
            1. Zero Data Collection:
            BT Receive Call collects, stores, records, or transmits NO personal information, call content, or user data.
            
            2. 100% Offline Operation:
            The application operates entirely offline and requests ZERO internet permissions.
            
            3. Accessibility Utility:
            Utilizes Android Accessibility API strictly to assist visually impaired individuals in answering incoming calls (Phone, WhatsApp, Messenger, Telegram) using Bluetooth hardware buttons.
            
            4. Safety & Compliance:
            Designed to enhance user safety and convenience during transit. Fully compliant with Google Play Accessibility and Bluetooth Policies.
        """.trimIndent()
    }
}
