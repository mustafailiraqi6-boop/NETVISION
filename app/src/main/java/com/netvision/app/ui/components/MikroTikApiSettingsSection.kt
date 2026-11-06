package com.netvision.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MikroTikApiSettingsSection(
    isArabic: Boolean, enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    port: String, onPortChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (isArabic) "إعدادات MikroTik API (اختياري)" else "MikroTik API Settings (Optional)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (isArabic) "تفعيل اتصال API لهذا الجهاز" else "Enable API for this device",
                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface
            )
            Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isArabic) "منفذ API" else "API Port") },
                singleLine = true
            )
            Text(
                if (isArabic) "بيانات الدخول تُؤخذ تلقائياً من إعدادات MikroTik API العامة. المنفذ الافتراضي 8728."
                else "Credentials are taken automatically from the global MikroTik API settings. Default port is 8728.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
