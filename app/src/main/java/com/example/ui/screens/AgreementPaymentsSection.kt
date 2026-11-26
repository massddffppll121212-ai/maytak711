package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Agreement
import com.example.model.AgreementAttachment
import com.example.model.AgreementPayment
import com.example.model.AgreementStatus
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.example.model.UiState
import com.example.ui.components.formatArabicDate
import com.example.ui.components.formatCurrencyAmount
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsLedgerTab(
    agreement: Agreement,
    paymentsState: UiState<List<AgreementPayment>>,
    currentUserId: String,
    currentUserEmail: String,
    currentUserPhone: String,
    verifiedWitnessOtpChallengeId: String?,
    isParticipant: Boolean,
    isBusy: Boolean,
    onRecordPayment: (
        amount: Double,
        method: PaymentMethodType,
        receiptReference: String,
        paymentDateText: String,
        attachment: AgreementAttachment?
    ) -> Unit,
    onConfirmPayment: (
        payment: AgreementPayment,
        mode: PaymentConfirmationMode,
        confirmedAmount: Double,
        note: String?,
        witnessOtpChallengeId: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var paymentToConfirm by remember { mutableStateOf<AgreementPayment?>(null) }

    val todayDefault = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var payAmountInput by remember { mutableStateOf("") }
    var payDateInput by remember { mutableStateOf(todayDefault) }
    var payMethodInput by remember { mutableStateOf(PaymentMethodType.BANK_TRANSFER) }
    var payReceiptInput by remember { mutableStateOf("") }
    var payAttachment by remember { mutableStateOf<AgreementAttachment?>(null) }
    var payAttachmentError by remember { mutableStateOf<String?>(null) }
    var isHashingAttachment by remember { mutableStateOf(false) }

    var confirmModeInput by remember { mutableStateOf(PaymentConfirmationMode.FULL) }
    var confirmPartialAmountInput by remember { mutableStateOf("") }
    var confirmNoteInput by remember { mutableStateOf("") }

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isHashingAttachment = true
        payAttachmentError = null
        scope.launch {
            val res = inspectAndHashAttachment(context, uri)
            isHashingAttachment = false
            res.fold(
                onSuccess = { payAttachment = it },
                onFailure = { payAttachmentError = it.localizedMessage }
            )
        }
    }

    val isCallerWitness = remember(agreement.witnesses, currentUserId, currentUserEmail, currentUserPhone) {
        agreement.witnesses.any { w ->
            w.uid == currentUserId ||
                (currentUserEmail.isNotBlank() && w.email.equals(currentUserEmail, ignoreCase = true)) ||
                (currentUserPhone.isNotBlank() && w.phone.trim() == currentUserPhone.trim())
        }
    }

    val loadedPayments = (paymentsState as? UiState.Success)?.data.orEmpty()
    val confirmedSumFromLedger = loadedPayments.sumOf { p ->
        when (p.statusEnum) {
            PaymentStatus.CONFIRMED -> if (p.confirmedAmount > 0.0) p.confirmedAmount else p.amount
            PaymentStatus.PARTIAL_CONFIRMED -> p.confirmedAmount
            else -> 0.0
        }
    }
    val effectivePaidTotal = maxOf(agreement.paidTotal, confirmedSumFromLedger)
    val remainingAmount = (agreement.amount - effectivePaidTotal).coerceAtLeast(0.0)
    val ratio = if (agreement.amount > 0.0) (effectivePaidTotal / agreement.amount).toFloat().coerceIn(0f, 1f) else 0f

    // Authoritative Financial Ledger Summary Card (non-editable manually)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, GoldMetallic.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "الملخص المالي الموثّق (محسوب تلقائياً من الدفعات المؤكدة)",
                style = MaterialTheme.typography.labelLarge,
                color = GoldMetallic,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FinancialStatColumn(
                    label = "الإجمالي",
                    value = formatCurrencyAmount(agreement.amount, agreement.currencyEnum),
                    accent = MaterialTheme.colorScheme.onSurface
                )
                FinancialStatColumn(
                    label = "المدفوع المؤكد",
                    value = formatCurrencyAmount(effectivePaidTotal, agreement.currencyEnum),
                    accent = EmeraldVerified
                )
                FinancialStatColumn(
                    label = "المتبقي",
                    value = formatCurrencyAmount(remainingAmount, agreement.currencyEnum),
                    accent = if (remainingAmount > 0.0) EditOrange else EmeraldVerified
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = EmeraldVerified,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }

    if (isParticipant && agreement.statusEnum != AgreementStatus.DRAFT) {
        Button(
            onClick = {
                payAmountInput = ""
                payDateInput = todayDefault
                payReceiptInput = ""
                payAttachment = null
                payAttachmentError = null
                showAddPaymentDialog = true
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("detail_add_payment_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldMetallic,
                contentColor = NavyDeep
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("تسجيل دفعة مالية جديدة", fontWeight = FontWeight.Bold)
        }
    }

    when (paymentsState) {
        is UiState.Loading -> CircularProgressIndicator(color = GoldMetallic)
        is UiState.Error -> Text(paymentsState.message, color = MaterialTheme.colorScheme.error)
        is UiState.Success -> {
            if (paymentsState.data.isEmpty()) {
                Text(
                    text = "لا توجد دفعات مالية مسجلة على هذا الميثاق حتى الآن.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                paymentsState.data.forEach { payment ->
                    val statusEnum = payment.statusEnum
                    val isFinalized = statusEnum.isFinalized
                    val statusColor = when (statusEnum) {
                        PaymentStatus.PENDING_CONFIRMATION -> PendingBlue
                        PaymentStatus.CONFIRMED -> EmeraldVerified
                        PaymentStatus.PARTIAL_CONFIRMED -> EditOrange
                        PaymentStatus.NOT_RECEIVED -> CrimsonRejected
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = formatCurrencyAmount(payment.amount, agreement.currencyEnum),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = statusColor.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isFinalized) {
                                            Icon(Icons.Filled.Lock, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            text = statusEnum.arabicLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "الطريقة: ${payment.methodEnum.arabicLabel} • مرجع: ${payment.receiptReference}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "تاريخ السداد: ${payment.paymentDateText.ifBlank { formatArabicDate(payment.paidAt) }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (payment.attachment != null) {
                                Text(
                                    text = "مرفق الإيصال: ${payment.attachment.fileName} • SHA-256: ${payment.attachment.sha256Hash.take(14)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldMetallic
                                )
                            }

                            if (isFinalized) {
                                Text(
                                    text = "المؤكد استلامه: ${formatCurrencyAmount(payment.confirmedAmount, agreement.currencyEnum)} (${payment.confirmationModeEnum?.arabicLabel ?: statusEnum.arabicLabel})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (payment.witnessOtpVerified) {
                                    Text(
                                        text = "تم التأكيد بإثبات شاهد + OTP موثّق",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldVerified
                                    )
                                }
                                if (!payment.confirmationNote.isNullOrBlank()) {
                                    Text("ملاحظة الاستلام: ${payment.confirmationNote}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val receiptSummary = buildString {
                                            appendLine("إيصال دفعة مالية موثّق — منصة ميثاق MEETHAQ")
                                            appendLine("الميثاق: ${agreement.agreementCode} — ${agreement.title}")
                                            appendLine("المبلغ المسجل: ${formatCurrencyAmount(payment.amount, agreement.currencyEnum)}")
                                            appendLine("المؤكد استلامه: ${formatCurrencyAmount(payment.confirmedAmount, agreement.currencyEnum)}")
                                            appendLine("الحالة: ${statusEnum.arabicLabel}")
                                            appendLine("المرجع: ${payment.receiptReference}")
                                            appendLine("التاريخ: ${payment.paymentDateText.ifBlank { formatArabicDate(payment.paidAt) }}")
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "إيصال دفعة ${agreement.agreementCode}")
                                            putExtra(Intent.EXTRA_TEXT, receiptSummary)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "مشاركة ملخص الإيصال"))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("مشاركة ملخص الإيصال")
                                }
                            } else {
                                val canConfirmAsRecipient = isParticipant && payment.recordedBy != currentUserId
                                val canConfirmAsWitnessOtp = isCallerWitness && !verifiedWitnessOtpChallengeId.isNullOrBlank()
                                if (canConfirmAsRecipient || canConfirmAsWitnessOtp) {
                                    Button(
                                        onClick = {
                                            confirmModeInput = PaymentConfirmationMode.FULL
                                            confirmPartialAmountInput = payment.amount.toString()
                                            confirmNoteInput = ""
                                            paymentToConfirm = payment
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("payment_confirm_button_${payment.id}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("تأكيد الاستلام (كامل / جزئي / عدم الاستلام)", fontWeight = FontWeight.Bold)
                                    }
                                } else if (payment.recordedBy == currentUserId) {
                                    Text(
                                        text = "تم تسجيل هذه الدفعة من طرفك؛ بانتظار تأكيد الطرف المستلم الآخر (يُمنع التأكيد الذاتي).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PendingBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            title = { Text("تسجيل دفعة مالية جديدة") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = payAmountInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' }) {
                                payAmountInput = input.take(14)
                            }
                        },
                        label = { Text("مبلغ الدفعة *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("payment_amount_input")
                    )
                    OutlinedTextField(
                        value = payDateInput,
                        onValueChange = { payDateInput = it.take(40) },
                        label = { Text("تاريخ الدفعة (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier.testTag("payment_date_input")
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaymentMethodType.entries.forEach { pm ->
                            FilterChip(
                                selected = payMethodInput == pm,
                                onClick = { payMethodInput = pm },
                                label = { Text(pm.arabicLabel) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = payReceiptInput,
                        onValueChange = { payReceiptInput = it.take(200) },
                        label = { Text("رقم المرجع أو الإيصال *") },
                        modifier = Modifier.testTag("payment_receipt_input")
                    )
                    OutlinedButton(
                        onClick = {
                            attachmentLauncher.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                        },
                        enabled = !isHashingAttachment,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isHashingAttachment) "جارٍ فحص الإيصال…"
                            else payAttachment?.fileName ?: "إرفاق صورة/ملف الإيصال (اختياري)"
                        )
                    }
                    if (payAttachmentError != null) {
                        Text(payAttachmentError!!, style = MaterialTheme.typography.labelSmall, color = CrimsonRejected)
                    }
                }
            },
            confirmButton = {
                val amt = payAmountInput.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        showAddPaymentDialog = false
                        onRecordPayment(amt, payMethodInput, payReceiptInput, payDateInput, payAttachment)
                    },
                    enabled = amt > 0.0 && payReceiptInput.isNotBlank() && payDateInput.isNotBlank() && !isHashingAttachment,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("حفظ الدفعة") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = false }) { Text("إلغاء") }
            }
        )
    }

    paymentToConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { paymentToConfirm = null },
            title = { Text("تأكيد استلام الدفعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "المبلغ المسجل: ${formatCurrencyAmount(target.amount, agreement.currencyEnum)} — مرجع: ${target.receiptReference}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaymentConfirmationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = confirmModeInput == mode,
                                onClick = { confirmModeInput = mode },
                                label = { Text(mode.arabicLabel) }
                            )
                        }
                    }
                    if (confirmModeInput == PaymentConfirmationMode.PARTIAL) {
                        OutlinedTextField(
                            value = confirmPartialAmountInput,
                            onValueChange = { raw ->
                                if (raw.all { it.isDigit() || it == '.' }) {
                                    confirmPartialAmountInput = raw.take(14)
                                }
                            },
                            label = { Text("المبلغ المستلم فعلياً (أقل من ${target.amount}) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    OutlinedTextField(
                        value = confirmNoteInput,
                        onValueChange = { confirmNoteInput = it.take(500) },
                        label = { Text("ملاحظة التأكيد (اختياري)") },
                        maxLines = 3
                    )
                    Text(
                        text = "بعد تأكيد الاستلام يُقفل سجل الدفعة نهائياً ولا يمكن تعديله أو حذفه.",
                        style = MaterialTheme.typography.labelSmall,
                        color = EditOrange
                    )
                }
            },
            confirmButton = {
                val partialVal = confirmPartialAmountInput.toDoubleOrNull() ?: 0.0
                val validPartial = confirmModeInput != PaymentConfirmationMode.PARTIAL ||
                    (partialVal > 0.0 && partialVal < target.amount)
                Button(
                    onClick = {
                        val confirmedAmt = when (confirmModeInput) {
                            PaymentConfirmationMode.FULL -> target.amount
                            PaymentConfirmationMode.PARTIAL -> partialVal
                            PaymentConfirmationMode.NOT_RECEIVED -> 0.0
                        }
                        val witnessProofId = if (isCallerWitness) verifiedWitnessOtpChallengeId else null
                        paymentToConfirm = null
                        onConfirmPayment(target, confirmModeInput, confirmedAmt, confirmNoteInput, witnessProofId)
                    },
                    enabled = validPartial,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified)
                ) { Text("تأكيد وقفل نهائي") }
            },
            dismissButton = {
                TextButton(onClick = { paymentToConfirm = null }) { Text("تراجع") }
            }
        )
    }
}

@Composable
private fun FinancialStatColumn(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
