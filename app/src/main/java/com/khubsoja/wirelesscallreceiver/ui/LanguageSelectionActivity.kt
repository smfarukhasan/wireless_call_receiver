package com.khubsoja.wirelesscallreceiver.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.khubsoja.wirelesscallreceiver.R
import com.khubsoja.wirelesscallreceiver.databinding.ActivityLanguageSelectionBinding
import com.khubsoja.wirelesscallreceiver.utils.LocaleHelper

/**
 * Initial Language Selection Activity for first launch & settings language switcher.
 * Designed with TalkBack screen reader compatibility in mind.
 */
class LanguageSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_SETTINGS = "extra_from_settings"
    }

    private lateinit var binding: ActivityLanguageSelectionBinding
    private var selectedLanguage: String = LocaleHelper.LANGUAGE_ENGLISH
    private var isFromSettings: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isFromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        selectedLanguage = LocaleHelper.getSavedLanguage(this)

        setupRadioSelection()
        setupListeners()
    }

    private fun setupRadioSelection() {
        when (selectedLanguage) {
            LocaleHelper.LANGUAGE_ENGLISH -> binding.rbEnglish.isChecked = true
            LocaleHelper.LANGUAGE_BENGALI -> binding.rbBengali.isChecked = true
            LocaleHelper.LANGUAGE_SPANISH -> binding.rbSpanish.isChecked = true
            LocaleHelper.LANGUAGE_FRENCH -> binding.rbFrench.isChecked = true
            LocaleHelper.LANGUAGE_HINDI -> binding.rbHindi.isChecked = true
            else -> binding.rbEnglish.isChecked = true
        }

        binding.rgLanguages.setOnCheckedChangeListener { _, checkedId ->
            selectedLanguage = when (checkedId) {
                R.id.rbEnglish -> LocaleHelper.LANGUAGE_ENGLISH
                R.id.rbBengali -> LocaleHelper.LANGUAGE_BENGALI
                R.id.rbSpanish -> LocaleHelper.LANGUAGE_SPANISH
                R.id.rbFrench -> LocaleHelper.LANGUAGE_FRENCH
                R.id.rbHindi -> LocaleHelper.LANGUAGE_HINDI
                else -> LocaleHelper.LANGUAGE_ENGLISH
            }
        }
    }

    private fun setupListeners() {
        binding.btnContinueLanguage.setOnClickListener {
            // Save selected language and update locale context
            LocaleHelper.setLocale(this, selectedLanguage)

            if (isFromSettings) {
                // Restart MainActivity so resources re-inflate in new language
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } else {
                // Proceed to Onboarding Setup Wizard
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}
