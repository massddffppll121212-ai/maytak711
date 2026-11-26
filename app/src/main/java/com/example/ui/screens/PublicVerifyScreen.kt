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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic

@Composable
fun PublicVerifyScreen(
    publicId: String,
    certificateData: Map<String, Any>?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(64.dp))
        Text(
            text = "التحقق العام من الشهادة الرقمية",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "معرّف التحقق: $publicId",
            style = MaterialTheme.typography.labelMedium,
            color = GoldMetallic
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("public_verify_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (certificateData != null) {
                    val status = (certificateData["status"] as? String) ?: "ACTIVE"
                    val title = (certificateData["title"] as? String) ?: "اتفاق موثق"
                    val hash = (certificateData["approvedHash"] as? String) ?: "N/A"

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldVerified)
                        Text(text = "الشهادة سارية وموثّقة رسمياً", style = MaterialTheme.typography.titleSmall, color = EmeraldVerified, fontWeight = FontWeight.Bold)
                    }

                    Text(text = "عنوان الاتفاق: $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(text = "حالة الاعتماد: $status", style = MaterialTheme.typography.bodySmall)
                    Text(text = "بصمة الاعتماد (SHA-256): $hash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "عذراً، لم يتم العثور على شهادة مطابقة لهذا المعرّف أو أن الرابط قد انتهى.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
