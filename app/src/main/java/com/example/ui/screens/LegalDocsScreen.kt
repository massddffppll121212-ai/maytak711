package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GoldMetallic

@Composable
fun LegalDocsScreen(docType: String) {
    val title = when (docType) {
        "privacy" -> "سياسة الخصوصية"
        "terms" -> "شروط الاستخدام والتوثيق"
        else -> "إخلاء المسؤولية القانونية"
    }

    val content = when (docType) {
        "privacy" -> "تلتزم منصة ميثاق بحماية بياناتك الشخصية وتشفير كافة المعاملات والاتفاقات باستخدام معايير التشفير القياسية SHA-256 و AES-GCM. لا يتم مشاركة بياناتك مع أي طرف ثالث دون سند قانوني أو موافقة صريحة."
        "terms" -> "باستخدامك لمنصة ميثاق، فإنك توافق على الشروط والأحكام الخاصة بالتوثيق الرقمي، بما في ذلك التحقق المزدوج برمز OTP والتوقيع البيومتري. يعتبر كل اتفاق معتمد وثيقة رقمية محمية بسجل تدقيق لا يقبل التعديل المباشر."
        else -> "منصة ميثاق هي أداة رقمية متقدمة لتنظيم، توثيق، وحفظ الاتفاقات والأدلة والسجلات المالية، ولا تعتبر منصة ميثاق جهة قضائية أو محكمة رسمية، والتحقق البيومتري وحده ليس إثباتاً قانونياً قاطعاً للهوية أمام الجهات القضائية."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GoldMetallic
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
