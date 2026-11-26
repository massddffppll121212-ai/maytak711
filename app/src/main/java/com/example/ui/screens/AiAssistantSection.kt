package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Agreement
import com.example.ui.theme.EditOrange
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "AiAssistant"

@Composable
fun AiAssistantSection(agreement: Agreement) {
    val scope = rememberCoroutineScope()
    var aiOutput by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun runAiPrompt(prompt: String) {
        isLoading = true
        aiOutput = null
        scope.launch {
            try {
                val response = withTimeout(45_000L) {
                    generativeModel.generateContent(
                        "أنت مساعد قانوني ذكي لمنصة ميثاق. حلل الميثاق التالي بدقة واقترح صياغات أو تلخيصاً:\n" +
                            "عنوان الاتفاق: ${agreement.title}\n" +
                            "الوصف والبنود: ${agreement.description}\n" +
                            "المبلغ: ${agreement.amount} ${agreement.currency}\n\n" +
                            "الطلب: $prompt"
                    )
                }
                aiOutput = response.text ?: "لم يتم إرجاع نتيجة."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                aiOutput = "عذراً، تعذّر إتمام التحليل الذكي حالياً: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_assistant_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GoldMetallic)
                Text(
                    text = "المساعد الذكي لبنود الاتفاق",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GoldMetallic
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = EditOrange.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = EditOrange, modifier = Modifier.size(16.dp))
                    Text(
                        text = "تنبيه: المساعد الذكي لا يقدم استشارة قانونية نهائية ولا يضمن الصحة القانونية.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { runAiPrompt("تلخيص الحقوق والالتزامات الأساسية للطرفين") },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_summarize_button")
                ) {
                    Text("تلخيص الحقوق", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { runAiPrompt("اكتشاف البنود الغامضة أو المتعارضة أو التي تحتاج تحسيناً") },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_check_clauses_button")
                ) {
                    Text("فحص البنود", fontWeight = FontWeight.Bold)
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = GoldMetallic)
                }
            }

            val currentOutput = aiOutput
            if (!currentOutput.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = currentOutput,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
