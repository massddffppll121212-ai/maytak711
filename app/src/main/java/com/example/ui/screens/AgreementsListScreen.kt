package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Agreement
import com.example.model.AgreementStatus
import com.example.model.UiState
import com.example.ui.components.AgreementCard
import com.example.ui.components.EmptyVaultCard
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.viewmodel.AgreementFilter

@Composable
fun AgreementsListScreen(
    agreementsState: UiState<List<Agreement>>,
    selectedFilter: AgreementFilter,
    searchQuery: String,
    currentUserEmail: String,
    currentUserId: String,
    onFilterSelected: (AgreementFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAgreementClick: (String) -> Unit,
    onCreateAgreementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agreements_search_input"),
                    placeholder = { Text("ابحث بالعنوان أو رقم الميثاق أو اسم طرف أو شاهد…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "بحث"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "مسح البحث"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgreementFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(
                                    text = filter.arabicLabel,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldMetallic,
                                selectedLabelColor = NavyDeep
                            )
                        )
                    }
                }
            }

            when (agreementsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GoldMetallic)
                    }
                }

                is UiState.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "خطأ",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = agreementsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(agreementsState.data, selectedFilter, searchQuery) {
                        val query = searchQuery.trim().lowercase()
                        agreementsState.data.filter { item ->
                            val matchesFilter = when (selectedFilter) {
                                AgreementFilter.ALL -> true
                                AgreementFilter.DRAFT -> item.statusEnum == AgreementStatus.DRAFT
                                AgreementFilter.PENDING -> item.statusEnum == AgreementStatus.PENDING
                                AgreementFilter.APPROVED -> item.statusEnum == AgreementStatus.APPROVED
                                AgreementFilter.NEEDS_EDIT -> item.statusEnum == AgreementStatus.NEEDS_EDIT
                                AgreementFilter.EXPIRED -> item.statusEnum == AgreementStatus.EXPIRED
                                AgreementFilter.DISPUTED -> item.statusEnum == AgreementStatus.DISPUTED
                            }
                            val matchesQuery = query.isEmpty() ||
                                item.title.lowercase().contains(query) ||
                                item.agreementCode.lowercase().contains(query) ||
                                item.ownerName.lowercase().contains(query) ||
                                item.parties.any {
                                    it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
                                } ||
                                item.witnesses.any {
                                    it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
                                }
                            matchesFilter && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            EmptyVaultCard(
                                title = if (agreementsState.data.isEmpty()) {
                                    "سجل المواثيق فارغ"
                                } else {
                                    "لا توجد مواثيق مطابقة للتصفية"
                                },
                                subtitle = if (agreementsState.data.isEmpty()) {
                                    "أنشئ ميثاقك الأول الآن لتوثيق الاتفاقات وحفظ الحقوق رقمياً."
                                } else {
                                    "جرّب تغيير حالة التصفية أو كلمات البحث."
                                },
                                actionButton = {
                                    Button(
                                        onClick = onCreateAgreementClick,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldMetallic,
                                            contentColor = NavyDeep
                                        ),
                                        border = BorderStroke(1.dp, GoldMetallic)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("صياغة ميثاق جديد", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { agreement ->
                                AgreementCard(
                                    agreement = agreement,
                                    currentUserEmail = currentUserEmail,
                                    currentUserId = currentUserId,
                                    onClick = { onAgreementClick(agreement.id) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
