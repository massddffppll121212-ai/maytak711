package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.AgreementHasher
import com.example.data.DraftAutoSaveStore
import com.example.data.LocalDraftSnapshot
import com.example.model.Agreement
import com.example.model.AgreementAttachment
import com.example.model.AgreementType
import com.example.model.CurrencyCode
import com.example.model.Party
import com.example.model.PaymentMethodType
import com.example.model.Witness
import com.example.ui.components.CountryDialCode
import com.example.ui.components.PhoneCountryPickerField
import com.example.ui.components.PhoneNumberHelper
import com.example.ui.components.formatCurrencyAmount
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.viewmodel.ActionFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L // 10 MB
private val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")

private val wizardStepTitles = listOf(
    "النوع والعنوان",
    "المبالغ والتواريخ",
    "الأطراف المشاركة",
    "الحقوق والالتزامات",
    "الشهود والمرفقات",
    "المراجعة والإرسال"
)

private data class PartyFormItem(
    val name: String = "",
    val role: String = "الطرف الثاني",
    val email: String = "",
    val country: CountryDialCode = PhoneNumberHelper.defaultCountry,
    val localPhone: String = ""
) {
    fun toParty(): Party {
        val e164 = if (localPhone.isNotBlank()) PhoneNumberHelper.buildE164(country, localPhone) else ""
        return Party(
            name = name.trim(),
            email = email.trim().lowercase(Locale.US),
            phone = e164,
            role = role.trim().ifBlank { "طرف مشارك" }
        )
    }

    companion object {
        fun fromParty(party: Party): PartyFormItem {
            val (c, local) = if (party.phone.isNotBlank()) {
                PhoneNumberHelper.detectCountryAndLocal(party.phone)
            } else {
                PhoneNumberHelper.defaultCountry to ""
            }
            return PartyFormItem(
                name = party.name,
                role = party.role.ifBlank { "طرف مشارك" },
                email = party.email,
                country = c,
                localPhone = local
            )
        }
    }
}

private data class WitnessFormItem(
    val name: String = "",
    val email: String = "",
    val country: CountryDialCode = PhoneNumberHelper.defaultCountry,
    val localPhone: String = "",
    val nationalId: String = ""
) {
    fun toWitness(): Witness {
        val e164 = if (localPhone.isNotBlank()) PhoneNumberHelper.buildE164(country, localPhone) else ""
        return Witness(
            name = name.trim(),
            email = email.trim().lowercase(Locale.US),
            phone = e164,
            nationalId = nationalId.trim().takeIf { it.isNotBlank() }
        )
    }

    companion object {
        fun fromWitness(w: Witness): WitnessFormItem {
            val (c, local) = if (w.phone.isNotBlank()) {
                PhoneNumberHelper.detectCountryAndLocal(w.phone)
            } else {
                PhoneNumberHelper.defaultCountry to ""
            }
            return WitnessFormItem(
                name = w.name,
                email = w.email,
                country = c,
                localPhone = local,
                nationalId = w.nationalId.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAgreementScreen(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String,
    currentUserPhone: String,
    editingAgreement: Agreement?,
    actionFeedback: ActionFeedback,
    onSaveSubmit: (
        existingAgreement: Agreement?,
        title: String,
        type: AgreementType,
        description: String,
        amount: Double,
        currency: CurrencyCode,
        paymentMethod: PaymentMethodType,
        agreementDate: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        invitedParties: List<Party>,
        witnesses: List<Witness>,
        attachments: List<AgreementAttachment>,
        submitAsPending: Boolean
    ) -> Unit,
    onAutoSaveCloudDraft: suspend (
        existingAgreement: Agreement?,
        title: String,
        type: AgreementType,
        description: String,
        amount: Double,
        currency: CurrencyCode,
        paymentMethod: PaymentMethodType,
        agreementDate: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        invitedParties: List<Party>,
        witnesses: List<Witness>,
        attachments: List<AgreementAttachment>
    ) -> Result<String>,
    onCancelEditing: () -> Unit,
    onAgreementSaved: (String) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var currentStep by remember(editingAgreement?.id) { mutableIntStateOf(0) }
    var activeDraftAgreement by remember(editingAgreement?.id) { mutableStateOf(editingAgreement) }
    var autoSavedCloudId by remember(editingAgreement?.id) { mutableStateOf(editingAgreement?.id) }

    // Step 1
    var selectedType by remember(editingAgreement?.id) {
        mutableStateOf(editingAgreement?.typeEnum ?: AgreementType.PARTNERSHIP)
    }
    var title by remember(editingAgreement?.id) { mutableStateOf(editingAgreement?.title.orEmpty()) }
    var description by remember(editingAgreement?.id) { mutableStateOf(editingAgreement?.description.orEmpty()) }

    // Step 2
    var selectedCurrency by remember(editingAgreement?.id) {
        mutableStateOf(editingAgreement?.currencyEnum ?: CurrencyCode.SAR)
    }
    var amountText by remember(editingAgreement?.id) {
        mutableStateOf(
            if (editingAgreement != null && editingAgreement.amount > 0.0) {
                editingAgreement.amount.toString().removeSuffix(".0")
            } else ""
        )
    }
    var selectedPaymentMethod by remember(editingAgreement?.id) {
        mutableStateOf(editingAgreement?.paymentMethodEnum ?: PaymentMethodType.BANK_TRANSFER)
    }
    var agreementDateText by remember(editingAgreement?.id) {
        mutableStateOf(editingAgreement?.agreementDate?.ifBlank { todayStr } ?: todayStr)
    }
    var startDateText by remember(editingAgreement?.id) {
        mutableStateOf(editingAgreement?.startDate?.ifBlank { todayStr } ?: todayStr)
    }
    var dueDateText by remember(editingAgreement?.id) { mutableStateOf(editingAgreement?.dueDate.orEmpty()) }

    // Step 3: Parties
    val partiesList = remember(editingAgreement?.id) {
        mutableStateListOf<PartyFormItem>().apply {
            val initial = editingAgreement?.parties
                ?.filterNot {
                    it.uid == currentUserId ||
                        (currentUserEmail.isNotBlank() && it.email.equals(currentUserEmail, ignoreCase = true)) ||
                        (currentUserPhone.isNotBlank() && it.phone == currentUserPhone)
                }
                .orEmpty()
            if (initial.isNotEmpty()) {
                addAll(initial.map { PartyFormItem.fromParty(it) })
            } else {
                add(PartyFormItem(role = "الطرف الثاني"))
            }
        }
    }

    // Step 4: Rights, Obligations, Special Terms
    val rightsList = remember(editingAgreement?.id) {
        mutableStateListOf<String>().apply {
            editingAgreement?.rights?.let { addAll(it) }
        }
    }
    var newRightInput by remember { mutableStateOf("") }

    val obligationsList = remember(editingAgreement?.id) {
        mutableStateListOf<String>().apply {
            editingAgreement?.obligations?.let { addAll(it) }
        }
    }
    var newObligationInput by remember { mutableStateOf("") }

    val specialTermsList = remember(editingAgreement?.id) {
        mutableStateListOf<String>().apply {
            editingAgreement?.specialTerms?.let { addAll(it) }
        }
    }
    var newSpecialTermInput by remember { mutableStateOf("") }

    // Step 5: Witnesses & Attachments
    val witnessesList = remember(editingAgreement?.id) {
        mutableStateListOf<WitnessFormItem>().apply {
            editingAgreement?.witnesses?.forEach { add(WitnessFormItem.fromWitness(it)) }
        }
    }
    val attachmentsList = remember(editingAgreement?.id) {
        mutableStateListOf<AgreementAttachment>().apply {
            editingAgreement?.attachments?.let { addAll(it) }
        }
    }
    var isProcessingAttachment by remember { mutableStateOf(false) }

    // Step 6
    var pledgeConfirmed by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }
    var autoSaveStatus by remember { mutableStateOf<String?>(null) }

    // Local auto-saved draft restore prompt (only when creating fresh)
    var savedLocalDraft by remember(editingAgreement?.id) {
        mutableStateOf(
            if (editingAgreement == null) {
                DraftAutoSaveStore.load(context, currentUserId)
                    ?.takeIf { it.title.isNotBlank() || it.description.isNotBlank() }
            } else null
        )
    }

    if (currentStep > 0) {
        BackHandler {
            localValidationError = null
            currentStep -= 1
        }
    } else if (editingAgreement != null) {
        BackHandler { onCancelEditing() }
    }

    LaunchedEffect(actionFeedback) {
        if (actionFeedback is ActionFeedback.Success && actionFeedback.createdAgreementId != null) {
            val createdId = actionFeedback.createdAgreementId
            onClearFeedback()
            onAgreementSaved(createdId)
        }
    }

    val isSubmitting = actionFeedback is ActionFeedback.Loading
    val parsedAmount = if (selectedCurrency == CurrencyCode.NONE) 0.0 else (amountText.trim().toDoubleOrNull() ?: 0.0)

    val partyModels = partiesList.map { it.toParty() }
    val witnessModels = witnessesList.map { it.toWitness() }

    val allParticipantEmails = remember(currentUserEmail, partyModels, witnessModels) {
        (listOf(currentUserEmail) + partyModels.map { it.email } + witnessModels.map { it.email })
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val previewHash = remember(
        title,
        selectedType,
        description,
        parsedAmount,
        selectedCurrency,
        selectedPaymentMethod,
        startDateText,
        dueDateText,
        rightsList.toList(),
        obligationsList.toList(),
        specialTermsList.toList(),
        allParticipantEmails
    ) {
        AgreementHasher.computeContentHash(
            agreementCode = activeDraftAgreement?.agreementCode ?: "PREVIEW",
            title = title,
            type = selectedType.name,
            description = description,
            amount = parsedAmount,
            currency = selectedCurrency.name,
            paymentMethod = selectedPaymentMethod.name,
            startDate = startDateText,
            dueDate = dueDateText,
            rights = rightsList.toList(),
            obligations = obligationsList.toList(),
            specialTerms = specialTermsList.toList(),
            ownerId = currentUserId,
            participantEmails = allParticipantEmails
        )
    }

    // Auto-save every 30 seconds (Local snapshot always + Cloud DRAFT when valid)
    LaunchedEffect(currentUserId) {
        while (true) {
            delay(30_000L)
            val hasAnyInput = title.isNotBlank() || description.isNotBlank() || rightsList.isNotEmpty()
            if (!hasAnyInput || isSubmitting) continue

            val currentParties = partiesList.map { it.toParty() }
            val currentWitnesses = witnessesList.map { it.toWitness() }
            val nowMillis = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(nowMillis))

            DraftAutoSaveStore.save(
                context = context,
                userId = currentUserId,
                snapshot = LocalDraftSnapshot(
                    existingAgreementId = activeDraftAgreement?.id ?: autoSavedCloudId,
                    stepIndex = currentStep,
                    title = title,
                    type = selectedType,
                    description = description,
                    currency = selectedCurrency,
                    amountText = amountText,
                    paymentMethod = selectedPaymentMethod,
                    agreementDate = agreementDateText,
                    startDate = startDateText,
                    dueDate = dueDateText,
                    parties = currentParties,
                    rights = rightsList.toList(),
                    obligations = obligationsList.toList(),
                    specialTerms = specialTermsList.toList(),
                    witnesses = currentWitnesses,
                    attachments = attachmentsList.toList(),
                    savedAtMillis = nowMillis
                )
            )
            autoSaveStatus = "تم الحفظ التلقائي محلياً ($timeStr)"

            val validPartiesForCloud = currentParties.filter {
                it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank())
            }
            if (title.trim().length >= 3 && description.trim().length >= 10 && validPartiesForCloud.isNotEmpty()) {
                val res = onAutoSaveCloudDraft(
                    activeDraftAgreement,
                    title.trim(),
                    selectedType,
                    description.trim(),
                    parsedAmount,
                    selectedCurrency,
                    selectedPaymentMethod,
                    agreementDateText.trim().ifBlank { todayStr },
                    startDateText.trim(),
                    dueDateText.trim(),
                    rightsList.toList(),
                    obligationsList.toList(),
                    specialTermsList.toList(),
                    validPartiesForCloud,
                    currentWitnesses.filter { it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank()) },
                    attachmentsList.toList()
                )
                res.onSuccess { savedId ->
                    autoSavedCloudId = savedId
                    autoSaveStatus = "تم الحفظ التلقائي في السحابة كمسودة ($timeStr)"
                }
            }
        }
    }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (attachmentsList.size >= 10) {
            localValidationError = "الحد الأقصى للمرفقات هو 10 ملفات."
            return@rememberLauncherForActivityResult
        }
        isProcessingAttachment = true
        localValidationError = null
        scope.launch {
            val result = inspectAndHashAttachment(context, uri)
            isProcessingAttachment = false
            result.fold(
                onSuccess = { att ->
                    if (attachmentsList.any { it.sha256Hash == att.sha256Hash }) {
                        localValidationError = "هذا الملف مرفق بالفعل (${att.fileName})."
                    } else {
                        attachmentsList.add(att)
                    }
                },
                onFailure = { err ->
                    localValidationError = err.localizedMessage ?: "تعذّر إرفاق الملف."
                }
            )
        }
    }

    fun validateStep(step: Int): Boolean {
        val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        return when (step) {
            0 -> {
                when {
                    title.trim().length < 3 -> {
                        localValidationError = "يرجى إدخال عنوان واضح للميثاق (3 أحرف على الأقل)."
                        false
                    }
                    description.trim().length < 10 -> {
                        localValidationError = "يرجى كتابة وصف وبنود الاتفاق الأساسية (10 أحرف على الأقل)."
                        false
                    }
                    else -> true
                }
            }
            1 -> {
                when {
                    selectedCurrency != CurrencyCode.NONE &&
                        amountText.isNotBlank() &&
                        (amountText.toDoubleOrNull() == null || amountText.toDouble() < 0.0) -> {
                        localValidationError = "يرجى إدخال مبلغ مالي صحيح."
                        false
                    }
                    agreementDateText.trim().isBlank() -> {
                        localValidationError = "يرجى تحديد تاريخ الاتفاق."
                        false
                    }
                    else -> true
                }
            }
            2 -> {
                val activeParties = partiesList.filter {
                    it.name.isNotBlank() || it.email.isNotBlank() || it.localPhone.isNotBlank()
                }
                when {
                    activeParties.isEmpty() -> {
                        localValidationError = "يرجى إضافة طرف آخر واحد على الأقل."
                        false
                    }
                    activeParties.any { it.name.trim().length < 2 } -> {
                        localValidationError = "يرجى إدخال الاسم الكامل لكل طرف (حرفين على الأقل)."
                        false
                    }
                    activeParties.any { p ->
                        val hasEmail = p.email.isNotBlank()
                        val hasPhone = p.localPhone.isNotBlank()
                        (!hasEmail && !hasPhone) ||
                            (hasEmail && !p.email.trim().matches(emailRegex)) ||
                            (hasPhone && !PhoneNumberHelper.isValidE164(PhoneNumberHelper.buildE164(p.country, p.localPhone)))
                    } -> {
                        localValidationError = "تأكد من إدخال رقم هاتف صحيح (مع مفتاح الدولة) أو بريد إلكتروني صحيح لكل طرف."
                        false
                    }
                    else -> true
                }
            }
            3 -> true
            4 -> {
                val activeWitnesses = witnessesList.filter {
                    it.name.isNotBlank() || it.email.isNotBlank() || it.localPhone.isNotBlank()
                }
                when {
                    activeWitnesses.any { w ->
                        w.name.trim().length < 2 ||
                            (w.email.isBlank() && w.localPhone.isBlank()) ||
                            (w.email.isNotBlank() && !w.email.trim().matches(emailRegex)) ||
                            (w.localPhone.isNotBlank() && !PhoneNumberHelper.isValidE164(PhoneNumberHelper.buildE164(w.country, w.localPhone)))
                    } -> {
                        localValidationError = "تأكد من صحة اسم وبيانات التواصل (هاتف أو بريد) لكل شاهد مضاف."
                        false
                    }
                    else -> true
                }
            }
            else -> true
        }
    }

    fun submitFinal(submitAsPending: Boolean) {
        if (!validateStep(0) || !validateStep(1) || !validateStep(2) || !validateStep(4)) return
        if (submitAsPending && !pledgeConfirmed) {
            localValidationError = "يرجى تأكيد الإقرار قبل إرسال الميثاق للموافقة وإصدار الدعوات."
            return
        }
        localValidationError = null
        val targetExisting = activeDraftAgreement ?: editingAgreement?.let { it } ?: autoSavedCloudId?.let {
            Agreement(id = it, ownerId = currentUserId, agreementCode = previewHash)
        }
        val cleanParties = partiesList
            .map { it.toParty() }
            .filter { it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank()) }
        val cleanWitnesses = witnessesList
            .map { it.toWitness() }
            .filter { it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank()) }

        onSaveSubmit(
            targetExisting?.takeIf { it.ownerId == currentUserId && it.title.isNotBlank() },
            title.trim(),
            selectedType,
            description.trim(),
            parsedAmount,
            selectedCurrency,
            selectedPaymentMethod,
            agreementDateText.trim().ifBlank { todayStr },
            startDateText.trim(),
            dueDateText.trim(),
            rightsList.toList(),
            obligationsList.toList(),
            specialTermsList.toList(),
            cleanParties,
            cleanWitnesses,
            attachmentsList.toList(),
            submitAsPending
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wizard Header + Progress Bar
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (editingAgreement != null) Icons.Filled.EditNote else Icons.Filled.Gavel,
                                contentDescription = null,
                                tint = GoldMetallic,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = if (editingAgreement != null) {
                                        "تعديل الميثاق (${editingAgreement.agreementCode})"
                                    } else {
                                        "معالج صياغة الميثاق (6 خطوات)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الخطوة ${currentStep + 1} من 6: ${wizardStepTitles[currentStep]}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (editingAgreement != null) {
                            TextButton(onClick = onCancelEditing) { Text("إلغاء") }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (currentStep + 1) / 6f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .testTag("wizard_progress_bar"),
                        color = GoldMetallic,
                        trackColor = GoldMetallic.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wizardStepTitles.forEachIndexed { idx, label ->
                            val isActive = idx == currentStep
                            val isDone = idx < currentStep
                            Surface(
                                onClick = {
                                    if (idx <= currentStep || validateStep(currentStep)) {
                                        localValidationError = null
                                        currentStep = idx
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = when {
                                    isActive -> GoldMetallic
                                    isDone -> EmeraldVerified.copy(alpha = 0.16f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isActive -> GoldMetallic
                                        isDone -> EmeraldVerified.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier.testTag("wizard_step_pill_$idx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(14.dp))
                                    } else {
                                        Text(
                                            text = "${idx + 1}.",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) NavyDeep else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isActive -> NavyDeep
                                            isDone -> EmeraldVerified
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (autoSaveStatus != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(14.dp))
                            Text(
                                text = autoSaveStatus!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldVerified
                            )
                        }
                    }
                }
            }

            // Local Auto-Saved Draft Restore Banner
            savedLocalDraft?.let { draft ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = GoldMetallic.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GoldMetallic)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, tint = GoldMetallic)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "توجد مسودة محفوظة تلقائياً",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = draft.title.ifBlank { "مسودة بدون عنوان" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                title = draft.title
                                selectedType = draft.type
                                description = draft.description
                                selectedCurrency = draft.currency
                                amountText = draft.amountText
                                selectedPaymentMethod = draft.paymentMethod
                                agreementDateText = draft.agreementDate.ifBlank { todayStr }
                                startDateText = draft.startDate
                                dueDateText = draft.dueDate
                                rightsList.clear(); rightsList.addAll(draft.rights)
                                obligationsList.clear(); obligationsList.addAll(draft.obligations)
                                specialTermsList.clear(); specialTermsList.addAll(draft.specialTerms)
                                if (draft.parties.isNotEmpty()) {
                                    partiesList.clear()
                                    partiesList.addAll(draft.parties.map { PartyFormItem.fromParty(it) })
                                }
                                witnessesList.clear()
                                witnessesList.addAll(draft.witnesses.map { WitnessFormItem.fromWitness(it) })
                                attachmentsList.clear()
                                attachmentsList.addAll(draft.attachments)
                                currentStep = draft.stepIndex.coerceIn(0, 5)
                                savedLocalDraft = null
                            }
                        ) { Text("استعادة") }
                        TextButton(
                            onClick = {
                                DraftAutoSaveStore.clear(context, currentUserId)
                                savedLocalDraft = null
                            }
                        ) { Text("تجاهل") }
                    }
                }
            }

            if (!editingAgreement?.editRequestNote.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = EditOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ملاحظة طلب التعديل:",
                            style = MaterialTheme.typography.labelLarge,
                            color = EditOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = editingAgreement?.editRequestNote.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // STEP CONTENT
            when (currentStep) {
                0 -> {
                    // Step 1: 10 Types, Title, Description
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "1. نوع الميثاق والعنوان (${AgreementType.entries.size} أنواع متاحة)",
                                style = MaterialTheme.typography.titleSmall,
                                color = GoldMetallic,
                                fontWeight = FontWeight.Bold
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AgreementType.entries.forEach { t ->
                                    val selected = selectedType == t
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedType = t },
                                        label = { Text(t.arabicLabel) },
                                        modifier = Modifier.testTag("type_chip_${t.name.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldMetallic,
                                            selectedLabelColor = NavyDeep
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    title = it.take(160)
                                    localValidationError = null
                                },
                                label = { Text("عنوان الميثاق *") },
                                placeholder = { Text("مثال: عقد شراكة تجارية أو اتفاقية بيع وتوريد") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_title_input"),
                                shape = MaterialTheme.shapes.medium
                            )

                            OutlinedTextField(
                                value = description,
                                onValueChange = {
                                    description = it.take(5000)
                                    localValidationError = null
                                },
                                label = { Text("الوصف والملخص التنفيذي للاتفاق *") },
                                placeholder = { Text("اشرح موضوع الميثاق ونطاقه بوضوح (10 أحرف على الأقل)…") },
                                minLines = 4,
                                maxLines = 8,
                                supportingText = { Text("${description.length} / 5000 حرف") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_description_input"),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }

                1 -> {
                    // Step 2: Currency (inc. YER), Amount, Dates, Payment Method
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "2. المبالغ المالية، طريقة السداد، والتواريخ",
                                style = MaterialTheme.typography.titleSmall,
                                color = GoldMetallic,
                                fontWeight = FontWeight.Bold
                            )

                            Text("العملة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CurrencyCode.entries.forEach { curr ->
                                    FilterChip(
                                        selected = selectedCurrency == curr,
                                        onClick = { selectedCurrency = curr },
                                        label = { Text("${curr.arabicLabel} (${curr.symbol})") },
                                        modifier = Modifier.testTag("currency_chip_${curr.name.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldMetallic,
                                            selectedLabelColor = NavyDeep
                                        )
                                    )
                                }
                            }

                            if (selectedCurrency != CurrencyCode.NONE) {
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { input ->
                                        val ascii = PhoneNumberHelper.toAsciiDigits(input)
                                        if (ascii.all { it.isDigit() || it == '.' }) {
                                            amountText = ascii.take(15)
                                            localValidationError = null
                                        }
                                    },
                                    label = { Text("المبلغ الإجمالي (${selectedCurrency.symbol})") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_amount_input"),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }

                            OutlinedTextField(
                                value = agreementDateText,
                                onValueChange = { agreementDateText = it.take(40) },
                                label = { Text("تاريخ تحرير الاتفاق *") },
                                placeholder = { Text("2026-11-21") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_agreement_date_input"),
                                shape = MaterialTheme.shapes.medium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = startDateText,
                                    onValueChange = { startDateText = it.take(40) },
                                    label = { Text("تاريخ بداية الالتزام") },
                                    placeholder = { Text("2026-11-22") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_start_date_input"),
                                    shape = MaterialTheme.shapes.medium
                                )
                                OutlinedTextField(
                                    value = dueDateText,
                                    onValueChange = { dueDateText = it.take(40) },
                                    label = { Text("تاريخ الاستحقاق") },
                                    placeholder = { Text("2026-12-31") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_due_date_input"),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }

                            Text("طريقة السداد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PaymentMethodType.entries.forEach { pm ->
                                    FilterChip(
                                        selected = selectedPaymentMethod == pm,
                                        onClick = { selectedPaymentMethod = pm },
                                        label = { Text(pm.arabicLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldMetallic,
                                            selectedLabelColor = NavyDeep
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Step 3: Parties (Creator defaulted as Party 1 + additional parties)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "3. الأطراف المشاركة",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.Bold
                                )
                                if (partiesList.size < 5) {
                                    TextButton(
                                        onClick = {
                                            partiesList.add(PartyFormItem(role = "طرف مشارك"))
                                        },
                                        modifier = Modifier.testTag("add_party_button")
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إضافة طرف")
                                    }
                                }
                            }

                            // Creator locked as Party #1
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = EmeraldVerified.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = EmeraldVerified)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "الطرف #1: $currentUserName (المنشئ — تلقائياً)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val contactInfo = listOfNotNull(
                                            currentUserPhone.takeIf { it.isNotBlank() },
                                            currentUserEmail.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        if (contactInfo.isNotBlank()) {
                                            Text(
                                                text = contactInfo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            partiesList.forEachIndexed { index, party ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "الطرف #${index + 2}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (partiesList.size > 1) {
                                            IconButton(onClick = { partiesList.removeAt(index) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الطرف")
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = party.name,
                                            onValueChange = { partiesList[index] = party.copy(name = it.take(120)) },
                                            label = { Text("الاسم الكامل *") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1.4f)
                                                .testTag("create_party_name_$index")
                                        )
                                        OutlinedTextField(
                                            value = party.role,
                                            onValueChange = { partiesList[index] = party.copy(role = it.take(60)) },
                                            label = { Text("الدور") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    PhoneCountryPickerField(
                                        selectedCountry = party.country,
                                        onCountrySelected = { c -> partiesList[index] = party.copy(country = c) },
                                        localPhone = party.localPhone,
                                        onLocalPhoneChange = { ph -> partiesList[index] = party.copy(localPhone = ph) },
                                        label = "رقم هاتف الطرف (يُرسل إليه رابط الدعوة)",
                                        testTagPrefix = "create_party_phone_$index"
                                    )

                                    OutlinedTextField(
                                        value = party.email,
                                        onValueChange = { partiesList[index] = party.copy(email = it.take(150)) },
                                        label = { Text("البريد الإلكتروني (اختياري إذا أُدخل الهاتف)") },
                                        placeholder = { Text("partner@gmail.com") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("create_party_email_$index")
                                    )

                                    if (index < partiesList.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Step 4: Rights, Obligations, Special Terms
                    ClauseListEditorCard(
                        title = "الحقوق (Rights)",
                        placeholder = "أضف حقاً مكفولاً في الميثاق…",
                        items = rightsList,
                        inputValue = newRightInput,
                        onInputChange = { newRightInput = it },
                        onAddItem = {
                            val trimmed = newRightInput.trim()
                            if (trimmed.isNotEmpty() && rightsList.size < 20) {
                                rightsList.add(trimmed)
                                newRightInput = ""
                            }
                        },
                        onRemoveAt = { rightsList.removeAt(it) },
                        testTagPrefix = "rights"
                    )

                    ClauseListEditorCard(
                        title = "الالتزامات (Obligations)",
                        placeholder = "أضف التزاماً متفقاً عليه…",
                        items = obligationsList,
                        inputValue = newObligationInput,
                        onInputChange = { newObligationInput = it },
                        onAddItem = {
                            val trimmed = newObligationInput.trim()
                            if (trimmed.isNotEmpty() && obligationsList.size < 20) {
                                obligationsList.add(trimmed)
                                newObligationInput = ""
                            }
                        },
                        onRemoveAt = { obligationsList.removeAt(it) },
                        testTagPrefix = "obligations"
                    )

                    ClauseListEditorCard(
                        title = "الشروط الخاصة (Special Terms)",
                        placeholder = "أضف شرطاً خاصاً أو جزائياً…",
                        items = specialTermsList,
                        inputValue = newSpecialTermInput,
                        onInputChange = { newSpecialTermInput = it },
                        onAddItem = {
                            val trimmed = newSpecialTermInput.trim()
                            if (trimmed.isNotEmpty() && specialTermsList.size < 20) {
                                specialTermsList.add(trimmed)
                                newSpecialTermInput = ""
                            }
                        },
                        onRemoveAt = { specialTermsList.removeAt(it) },
                        testTagPrefix = "special_terms"
                    )
                }

                4 -> {
                    // Step 5: Optional Witnesses & Document Attachments (PDF/JPG/PNG <= 10MB)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "الشهود (اختياري — بحد أقصى 4)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = GoldMetallic,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (witnessesList.size < 4) {
                                    TextButton(
                                        onClick = { witnessesList.add(WitnessFormItem()) },
                                        modifier = Modifier.testTag("add_witness_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إضافة شاهد")
                                    }
                                }
                            }

                            if (witnessesList.isEmpty()) {
                                Text(
                                    text = "اتفاق موثّق.. حق محفوظ.. بلا شهود — ويمكنك إضافة شهود اختياريين عند الحاجة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                witnessesList.forEachIndexed { index, witness ->
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "الشاهد #${index + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(onClick = { witnessesList.removeAt(index) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الشاهد")
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = witness.name,
                                                onValueChange = { witnessesList[index] = witness.copy(name = it.take(120)) },
                                                label = { Text("اسم الشاهد *") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1.3f)
                                            )
                                            OutlinedTextField(
                                                value = witness.nationalId,
                                                onValueChange = { witnessesList[index] = witness.copy(nationalId = it.take(30)) },
                                                label = { Text("الهوية (اختياري)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        PhoneCountryPickerField(
                                            selectedCountry = witness.country,
                                            onCountrySelected = { c -> witnessesList[index] = witness.copy(country = c) },
                                            localPhone = witness.localPhone,
                                            onLocalPhoneChange = { ph -> witnessesList[index] = witness.copy(localPhone = ph) },
                                            label = "رقم هاتف الشاهد",
                                            testTagPrefix = "create_witness_phone_$index"
                                        )
                                        OutlinedTextField(
                                            value = witness.email,
                                            onValueChange = { witnessesList[index] = witness.copy(email = it.take(150)) },
                                            label = { Text("البريد الإلكتروني (اختياري)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (index < witnessesList.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    // Attachments Card (PDF, JPG, PNG max 10MB + SHA-256 fingerprint)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "المرفقات المؤمّنة (PDF / JPG / PNG)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = GoldMetallic,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "الحد الأقصى لحجم الملف 10MB — تُوثَّق ببصمة SHA-256 لكل مرفق.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    attachmentPickerLauncher.launch(
                                        arrayOf("application/pdf", "image/jpeg", "image/png")
                                    )
                                },
                                enabled = !isProcessingAttachment && attachmentsList.size < 10,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("add_attachment_button"),
                                border = BorderStroke(1.dp, GoldMetallic)
                            ) {
                                if (isProcessingAttachment) {
                                    CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جارٍ فحص وتوثيق بصمة الملف…", color = GoldMetallic)
                                } else {
                                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = GoldMetallic)
                                    Spacer(Modifier.width(6.dp))
                                    Text("إرفاق مستند أو صورة (حتى 10MB)", color = GoldMetallic, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (attachmentsList.isEmpty()) {
                                Text(
                                    text = "لم تُضَف مرفقات بعد (اختياري).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                attachmentsList.forEachIndexed { idx, att ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (att.mimeType == "application/pdf") {
                                                    Icons.Filled.PictureAsPdf
                                                } else {
                                                    Icons.Filled.Image
                                                },
                                                contentDescription = null,
                                                tint = GoldMetallic
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = att.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatFileSize(att.sizeBytes)} • SHA-256: ${att.sha256Hash.take(14)}…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { attachmentsList.removeAt(idx) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف المرفق")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                5 -> {
                    // Step 6: Final Review & Save Draft / Send for Approval
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
                                text = "6. المراجعة النهائية قبل التوثيق",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldMetallic,
                                fontWeight = FontWeight.Bold
                            )
                            ReviewLine("العنوان", title)
                            ReviewLine("نوع الميثاق", selectedType.arabicLabel)
                            ReviewLine("المبلغ", formatCurrencyAmount(parsedAmount, selectedCurrency))
                            ReviewLine("طريقة السداد", selectedPaymentMethod.arabicLabel)
                            ReviewLine("تاريخ الاتفاق", agreementDateText.ifBlank { todayStr })
                            if (startDateText.isNotBlank() || dueDateText.isNotBlank()) {
                                ReviewLine("المدة", "${startDateText.ifBlank { "—" }} ← ${dueDateText.ifBlank { "—" }}")
                            }
                            ReviewLine("الأطراف المدعوون", "${partiesList.count { it.name.isNotBlank() }} + أنت (المنشئ)")
                            ReviewLine(
                                "البنود المفصلة",
                                "${rightsList.size} حقوق • ${obligationsList.size} التزامات • ${specialTermsList.size} شروط خاصة"
                            )
                            ReviewLine("الشهود والمرفقات", "${witnessesList.count { it.name.isNotBlank() }} شهود • ${attachmentsList.size} مرفقات")
                        }
                    }

                    // SHA-256 Preview Box
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بصمة التشفير المتوقعة (SHA-256)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = previewHash.take(36) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Commitment Checkbox
                    Surface(
                        onClick = { pledgeConfirmed = !pledgeConfirmed },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (pledgeConfirmed) GoldMetallic else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = pledgeConfirmed,
                                onCheckedChange = { pledgeConfirmed = it },
                                modifier = Modifier.testTag("create_pledge_checkbox"),
                                colors = CheckboxDefaults.colors(checkedColor = GoldMetallic)
                            )
                            Text(
                                text = "أقرّ بصحة جميع البنود والأطراف أعلاه، وأعلم أن إرسال الميثاق للموافقة يُنشئ روابط دعوة صالحة لمدة 7 أيام ويقفل النسخة بعد الاعتماد.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Validation / Error Banner
            val errorToShow = localValidationError ?: (actionFeedback as? ActionFeedback.Error)?.message
            if (errorToShow != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "تنبيه",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorToShow,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Navigation / Action Buttons Bar
            if (currentStep < 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = {
                                localValidationError = null
                                currentStep -= 1
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("wizard_prev_button"),
                            border = BorderStroke(1.dp, GoldMetallic)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("السابق", color = GoldMetallic, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (validateStep(currentStep)) {
                                localValidationError = null
                                currentStep += 1
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("wizard_next_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldMetallic,
                            contentColor = NavyDeep
                        )
                    ) {
                        Text("التالي: ${wizardStepTitles[currentStep + 1]}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { submitFinal(submitAsPending = false) },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("create_draft_button"),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, GoldMetallic)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ كمسودة", color = GoldMetallic, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { submitFinal(submitAsPending = true) },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(54.dp)
                                .testTag("create_submit_button"),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldMetallic,
                                contentColor = NavyDeep
                            )
                        ) {
                            if (actionFeedback is ActionFeedback.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NavyDeep,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("إرسال للموافقة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            localValidationError = null
                            currentStep -= 1
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("العودة للخطوة السابقة")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

internal suspend fun inspectAndHashAttachment(
    context: Context,
    uri: Uri
): Result<AgreementAttachment> = withContext(Dispatchers.IO) {
    try {
        val resolver = context.applicationContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.US).orEmpty()
        var displayName = "attachment"
        var declaredSize = -1L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
            }
        }

        val resolvedMime = when {
            mime in ALLOWED_MIME_TYPES -> mime
            displayName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            displayName.endsWith(".jpg", ignoreCase = true) ||
                displayName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            displayName.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> ""
        }

        if (resolvedMime !in ALLOWED_MIME_TYPES) {
            return@withContext Result.failure(
                IllegalArgumentException("نوع الملف غير مدعوم. يُسمح فقط بملفات PDF و JPG و PNG.")
            )
        }
        if (declaredSize > MAX_ATTACHMENT_BYTES) {
            return@withContext Result.failure(
                IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح (10 ميجابايت).")
            )
        }

        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val buffer = stream.readBytes()
            if (buffer.size.toLong() > MAX_ATTACHMENT_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح (10 ميجابايت).")
                )
            }
            buffer
        } ?: return@withContext Result.failure(
            IllegalStateException("تعذّر قراءة محتوى الملف المختار.")
        )

        val sha256 = AgreementHasher.computeBytesSha256(bytes)
        Result.success(
            AgreementAttachment(
                fileName = displayName.take(120),
                mimeType = resolvedMime,
                sizeBytes = bytes.size.toLong(),
                sha256Hash = sha256
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ClauseListEditorCard(
    title: String,
    placeholder: String,
    items: List<String>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    testTagPrefix: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = GoldMetallic,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { onInputChange(it.take(300)) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_input")
                )
                Button(
                    onClick = onAddItem,
                    enabled = inputValue.isNotBlank() && items.size < 20,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("${testTagPrefix}_add_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldMetallic,
                        contentColor = NavyDeep
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة بند")
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "لم تُضَف بنود بعد (اختياري).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. $item",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemoveAt(idx) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "حذف البند"
                            )
                        }
                    }
                    if (idx < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
