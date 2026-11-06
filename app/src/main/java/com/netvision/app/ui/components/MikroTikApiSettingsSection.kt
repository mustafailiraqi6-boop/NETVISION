package com.netvision.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun MikroTikApiSettingsSection(
    isArabic: Boolean, enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    port: String, onPortChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit
) {
    var customCredentials by remember { mutableStateOf(username.isNotBlank() || password.isNotBlank()) }
    LaunchedEffect(username, password) {
        if (username.isNotBlank() || password.isNotBlank()) customCredentials = true
    }
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
                value = port, onValueChange = onPortChange, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isArabic) "منفذ API" else "API Port") }, singleLine = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = {
                    customCredentials = !customCredentials
                    if (!customCredentials) { onUsernameChange(""); onPasswordChange("") }
                }) {
                    Text(if (customCredentials) { if (isArabic) "استخدام الدخول الافتراضي" else "Use default credentials" } else { if (isArabic) "تخصيص الدخول" else "Customize credentials" })
                }
            }
            if (customCredentials) {
                OutlinedTextField(
                    value = username, onValueChange = onUsernameChange, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "اسم مستخدم الجهاز" else "Device username") }, singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "كلمة مرور الجهاز" else "Device password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true
                )
            } else {
                Text(
                    if (isArabic) "سيستخدم التطبيق بيانات الدخول الافتراضية المحفوظة في الإعدادات العامة."
                    else "The app will use the default credentials saved in general settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}