package com.sivpn.cepat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sivpn.cepat.repository.ConfigRepository
import com.sivpn.cepat.repository.LogRepository
import com.sivpn.cepat.repository.SettingsRepository

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepo = SettingsRepository(context.applicationContext)
            val logRepo = LogRepository()
            val configRepo = ConfigRepository(context.applicationContext)
            return MainViewModel(settingsRepo, logRepo, configRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
