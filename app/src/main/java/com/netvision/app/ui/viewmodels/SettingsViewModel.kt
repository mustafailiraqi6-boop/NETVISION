package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.settings.AppSettings
import com.netvision.app.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val repository: SettingsRepository = try {
        NetVisionApplication.instance.settingsRepository
    } catch (e: Exception) {
        SettingsRepository(NetVisionApplication.instance)
    }
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.getSettings()
        )

    fun updateSettings(newSettings: AppSettings) {
        repository.updateSettings(newSettings)
    }

    fun updateSnmpCommunities(communities: List<String>) {
        repository.updateSnmpCommunities(communities)
    }

    fun updateSnmpVersion(version: String) {
        val current = repository.getSettings()
        repository.updateSettings(current.copy(snmpVersion = version))
    }

    fun updateSnmpPort(portStr: String) {
        val portInt = portStr.toIntOrNull() ?: 161
        val current = repository.getSettings()
        repository.updateSettings(current.copy(snmpPort = portInt))
    }

    fun updateDefaultMikroTikApiPort(portStr: String) {
        val portInt = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val current = repository.getSettings()
        repository.updateSettings(current.copy(defaultMikroTikApiPort = portInt))
    }
}
