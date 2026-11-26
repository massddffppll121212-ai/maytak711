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
import com.example.ui.theme.EmeraldVerified
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.ui.theme.EditOrange
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep

@Composable
fun InheritanceScreen(
    onSubmitRequest: (deceasedEmailOrPhone: String, legalReason: String, documents: List<String>) -> Unit
) {
    var deceasedInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var docUrlInput by remember { mutableStateOf("") }
    var submittedSuccess by remember { mutableStateOf(false) }

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
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(32.dp))
            Column {
                Text(
                    text = "الورثة والممثل القانوني",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "تقديم طلب تمثيل قانوني في حالة الوفاة أو فقدان الأهلية",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldMetallic
                )
            }
        }

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
                    text = "تنبيه هام: لا يتم نقل الحقوق أو صلاحيات الحساب تلقائياً. تخضع كافة الطلبات لمراجعة قانونية صارمة، ويُمنع منعاً باتاً تعديل أي اتفاقيات أصلية أو موافقات سابقة.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "نموذج تقديم طلب الممثل القانوني", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GoldMetallic)

                OutlinedTextField(
                    value = deceasedInput,
                    onValueChange = { deceasedInput = it },
                    label = { Text("بريد أو هاتف الطرف المعني/المتوفى *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inheritance_deceased_input")
                )

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("الصفة القانونية وسبب الطلب (شهادة وفاة، صك ولاية، وكالة شرعية) *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inheritance_reason_input")
                )

                OutlinedTextField(
                    value = docUrlInput,
                    onValueChange = { docUrlInput = it },
                    label = { Text("رابط المستندات الثبوتية (PDF / صورة الصك)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inheritance_doc_input")
                )

                if (submittedSuccess) {
                    Text(
                        text = "تم تقديم الطلب بنجاح وهو قيد المراجعة القانونية من فريق ميثاق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldVerified,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (deceasedInput.isNotBlank() && reasonInput.isNotBlank()) {
                            val docs = if (docUrlInput.isNotBlank()) listOf(docUrlInput) else emptyList()
                            onSubmitRequest(deceasedInput, reasonInput, docs)
                            submittedSuccess = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("inheritance_submit_button")
                ) {
                    Icon(Icons.Filled.Gavel, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إرسال طلب المراجعة القانونية", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
