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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
fun DisputeSection(
    agreementId: String,
    disputes: List<Map<String, Any>>,
    onCreateDispute: (disputeType: String, details: String, evidenceList: List<String>) -> Unit,
    onReplyDispute: (disputeId: String, replyText: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var disputeTypeInput by remember { mutableStateOf("إنكار") }
    var detailsInput by remember { mutableStateOf("") }
    var evidenceInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Legal warning banner
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = EditOrange.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = EditOrange)
                Text(
                    text = "تنبيه قانوني: ميثاق ينظم ويحفظ الأدلة ولا يصدر أحكاماً قضائية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "النزاعات والأدلة المرتبطة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GoldMetallic
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                modifier = Modifier.testTag("open_dispute_button")
            ) {
                Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("فتح نزاع جديد", fontWeight = FontWeight.Bold)
            }
        }

        if (disputes.isEmpty()) {
            Text(
                text = "لا توجد نزاعات مسجلة لهذا الاتفاق حتى الآن.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                disputes.forEach { disp ->
                    val type = (disp["disputeType"] as? String) ?: (disp["type"] as? String) ?: "نزاع"
                    val details = (disp["details"] as? String) ?: ""
                    val status = (disp["status"] as? String) ?: "OPEN"
                    val openedByName = (disp["openedByName"] as? String) ?: "مستخدم"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "نوع النزاع: $type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(text = "الحالة: $status", style = MaterialTheme.typography.labelSmall, color = EditOrange)
                            }
                            Text(text = "مفتوح بواسطة: $openedByName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = details, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("فتح نزاع رسمي ورفع الأدلة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = disputeTypeInput,
                        onValueChange = { disputeTypeInput = it },
                        label = { Text("نوع النزاع (إنكار، عدم تسديد، اختلاف مبلغ…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = detailsInput,
                        onValueChange = { detailsInput = it },
                        label = { Text("تفاصيل النزاع وموضوع الاعتراض *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = evidenceInput,
                        onValueChange = { evidenceInput = it },
                        label = { Text("روابط الأدلة والمستندات (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (detailsInput.isNotBlank()) {
                            val evList = if (evidenceInput.isNotBlank()) listOf(evidenceInput) else emptyList()
                            onCreateDispute(disputeTypeInput, detailsInput, evList)
                            showCreateDialog = false
                            detailsInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier.testTag("submit_dispute_button")
                ) {
                    Text("تأكيد وفتح النزاع", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
