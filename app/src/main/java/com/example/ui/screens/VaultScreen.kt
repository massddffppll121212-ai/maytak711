package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.PendingBlue

@Composable
fun VaultScreen(
    vaultItems: List<Map<String, Any>>,
    onOpenAgreement: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var isOfflineSecureMode by remember { mutableStateOf(false) }

    val categories = listOf(
        "ALL" to "الكل",
        "CERTIFICATES" to "الشهادات",
        "RECEIPTS" to "الإيصالات",
        "PDFS" to "ملفات PDF",
        "DISPUTED" to "المتنازع عليها",
        "COMPLETED" to "المكتملة"
    )

    val filtered = vaultItems.filter { item ->
        val title = (item["title"] as? String).orEmpty()
        val cat = (item["category"] as? String).orEmpty()
        val matchesSearch = title.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategory == "ALL" || cat == selectedCategory
        matchesSearch && matchesCat
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "الخزنة الرقمية الآمنة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "أرشفة مشفرة AES-GCM • مزامنة ذكية وتصفح دون اتصال",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = EmeraldVerified.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(12.dp))
                    Text(text = "مشفّر محلياً", style = MaterialTheme.typography.labelSmall, color = EmeraldVerified, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("بحث في الخزنة والمستندات…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_search_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { (key, label) ->
                FilterChip(
                    selected = selectedCategory == key,
                    onClick = { selectedCategory = key },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldMetallic.copy(alpha = 0.2f),
                        selectedLabelColor = GoldMetallic
                    ),
                    modifier = Modifier.testTag("vault_filter_$key")
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = GoldMetallic.copy(alpha = 0.6f))
                    Text("لا توجد مستندات في هذا التصنيف حالياً.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { item ->
                    val title = (item["title"] as? String) ?: "مستند أرشيفي"
                    val cat = (item["category"] as? String) ?: "DOC"
                    val agreementId = (item["agreementId"] as? String)
                    val publicId = (item["publicId"] as? String)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_item_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Description, contentDescription = null, tint = GoldMetallic)
                                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = PendingBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PendingBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { /* Download or view cached */ }) {
                                    Icon(Icons.Filled.Download, contentDescription = "تنزيل مشفر", tint = GoldMetallic)
                                }
                                IconButton(onClick = { /* Share secure link */ }) {
                                    Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = GoldMetallic)
                                }
                                if (!agreementId.isNullOrBlank()) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { onOpenAgreement(agreementId) },
                                        modifier = Modifier.testTag("vault_open_agreement_button")
                                    ) {
                                        Text("فتح الميثاق المرتبط", color = GoldMetallic, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
