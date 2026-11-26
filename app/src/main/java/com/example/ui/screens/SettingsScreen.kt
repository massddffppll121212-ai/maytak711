package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EditOrange
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep

@Composable
fun SettingsScreen(
    userEmail: String,
    userPhone: String,
    onDeactivateAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    var showDeactivateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(32.dp))
            Column {
                Text(
                    text = "إعدادات الحساب والأمان",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "إدارة الأجهزة، الخصوصية، وحالة الحساب",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldMetallic
                )
            }
        }

        // Account Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "معلومات الحساب", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GoldMetallic)
                Text(text = "البريد الإلكتروني: $userEmail", style = MaterialTheme.typography.bodySmall)
                Text(text = "رقم الهاتف المرتبط: ${userPhone.ifBlank { "غير مضاف" }}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Security & Sessions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "الأمان وجلسات الأجهزة النشطة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GoldMetallic)
                Text(text = "حالة App Check: مفعّل وآمن", style = MaterialTheme.typography.bodySmall)
                Text(text = "التوثيق المزدوج (OTP + Biometric): نشط وحماية قصوى", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Account Deactivation (Soft Deactivation per Data Retention Policy)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "تعطيل الحساب", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EditOrange)
                Text(
                    text = "وفقاً لسياسة الاحتفاظ بالاتفاقات والأدلة لحماية حقوق الأطراف الآخرين، يؤدي تعطيل الحساب إلى إيقاف الوصول الفردي مع الاحتفاظ بالاتفاقات وسجلات التدقيق المعتمدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showDeactivateDialog = true },
                    border = BorderStroke(1.dp, EditOrange),
                    modifier = Modifier.testTag("settings_deactivate_button")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = EditOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("تعطيل الحساب", color = EditOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("تأكيد تعطيل الحساب") },
            text = { Text("هل أنت متأكد من رغبتك في تعطيل الحساب؟ سيتم إنهاء الجلسة الحالية والاحتفاظ بالسجلات المشتركة للأطراف.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        onDeactivateAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EditOrange),
                    modifier = Modifier.testTag("confirm_deactivate_button")
                ) {
                    Text("تأكيد وتعطيل", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
