package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.ui.theme.GoldMetallic

@Composable
fun NotificationsScreen(
    notifications: List<Map<String, Any>>
) {
    var subAgreementCreated by remember { mutableStateOf(true) }
    var subInvitations by remember { mutableStateOf(true) }
    var subApprovals by remember { mutableStateOf(true) }
    var subPayments by remember { mutableStateOf(true) }
    var subDisputes by remember { mutableStateOf(true) }
    var subNewDevice by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(32.dp))
            Column {
                Text(
                    text = "مركز الإشعارات والتنبيهات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "FCM & In-App Alerts • اشتراكات فورية وحماية ذكية",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldMetallic
                )
            }
        }

        // Subscription Toggles Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "إعدادات اشتراكات الإشعارات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GoldMetallic)

                NotificationToggleRow("إنشاء الاتفاقات والدعوات", subInvitations) { subInvitations = it }
                NotificationToggleRow("طلبات الموافقة والتوقيع", subApprovals) { subApprovals = it }
                NotificationToggleRow("تأكيد الدفعات المالية", subPayments) { subPayments = it }
                NotificationToggleRow("فتح النزاعات والأدلة", subDisputes) { subDisputes = it }
                NotificationToggleRow("تسجيل الدخول من جهاز جديد", subNewDevice) { subNewDevice = it }
            }
        }

        Text(text = "التنبيهات السابقة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (notifications.isEmpty()) {
            Text(
                text = "لا توجد إشعارات جديدة في السجل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notif ->
                    val title = (notif["title"] as? String) ?: "تنبيه ميثاق"
                    val body = (notif["body"] as? String) ?: ""
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_item"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = GoldMetallic)
        )
    }
}
