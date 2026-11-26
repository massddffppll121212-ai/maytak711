package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.crypto.AgreementHasher
import com.example.crypto.BiometricSignatureResult
import com.example.data.OtpChallengeIssued
import com.example.model.Agreement
import com.example.model.AgreementAmendment
import com.example.model.AgreementAttachment
import com.example.model.AgreementDispute
import com.example.model.AgreementPayment
import com.example.model.AgreementSignature
import com.example.model.AgreementStatus
import com.example.model.AuditLog
import com.example.model.DisputeStatus
import com.example.model.Invitation
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.UiState
import com.example.ui.components.AgreementStatusBadge
import com.example.ui.components.formatArabicDate
import com.example.ui.components.formatCurrencyAmount
import com.example.ui.components.formatMillisDate
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.DraftGray
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ExpiredPurple
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue
import com.example.ui.viewmodel.ActionFeedback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgreementDetailScreen(
    agreementState: UiState<Agreement?>,
    paymentsState: UiState<List<AgreementPayment>>,
    disputesState: UiState<List<AgreementDispute>>,
    auditState: UiState<List<AuditLog>>,
    invitationsState: UiState<List<Invitation>>,
    signaturesState: UiState<List<AgreementSignature>>,
    amendmentsState: UiState<List<AgreementAmendment>>,
    currentUserId: String,
    currentUserEmail: String,
    currentUserPhone: String,
    activeOtpChallenge: OtpChallengeIssued?,
    otpVerifiedLocally: Boolean,
    verifiedWitnessOtpChallengeId: String?,
    pendingBiometricSignature: BiometricSignatureResult?,
    actionFeedback: ActionFeedback,
    onBack: () -> Unit,
    onEditDraftOrNeedsEdit: (Agreement) -> Unit,
    onSendInvitations: (Agreement) -> Unit,
    onArchiveAgreement: (Agreement) -> Unit,
    onRequestOtp: (Agreement, String) -> Unit,
    onVerifyOtp: (Agreement, String, String) -> Unit,
    onBiometricSigned: (BiometricSignatureResult) -> Unit,
    onSignAgreement: (
        Agreement,
        phoneVerified: Boolean,
        biometricVerified: Boolean,
        credentialId: String?,
        verificationMethod: String,
        signatureHex: String?
    ) -> Unit,
    onApproveAgreement: (Agreement, String?) -> Unit,
    onRequestAmendment: (
        Agreement,
        reason: String,
        proposedTitle: String,
        proposedDescription: String,
        proposedAmount: Double,
        proposedDueDate: String,
        proposedRights: List<String>,
        proposedObligations: List<String>,
        proposedSpecialTerms: List<String>
    ) -> Unit,
    onRespondToAmendment: (Agreement, AgreementAmendment, Boolean, String?) -> Unit,
    onRequestEdits: (Agreement, String) -> Unit,
    onMarkExpired: (Agreement) -> Unit,
    onRecordPayment: (
        Agreement,
        Double,
        PaymentMethodType,
        String,
        String,
        AgreementAttachment?
    ) -> Unit,
    onConfirmPayment: (
        Agreement,
        AgreementPayment,
        PaymentConfirmationMode,
        Double,
        String?,
        String?
    ) -> Unit,
    onOpenDispute: (Agreement, String, String, List<String>) -> Unit,
    onReplyToDispute: (Agreement, AgreementDispute, String, DisputeStatus) -> Unit,
    onLocalFeedbackError: (String) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("البنود والأطراف", "الدفعات", "النزاعات", "سجل التدقيق")

    var showRequestEditDialog by remember { mutableStateOf(false) }
    var showExpireDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showOpenDisputeDialog by remember { mutableStateOf(false) }
    var disputeToReply by remember { mutableStateOf<AgreementDispute?>(null) }

    var noteInput by remember { mutableStateOf("") }
    var disputeTypeInput by remember { mutableStateOf("") }
    var disputeDetailsInput by remember { mutableStateOf("") }
    var disputeEvidenceInput by remember { mutableStateOf("") }
    var replyMessageInput by remember { mutableStateOf("") }
    var replyStatusInput by remember { mutableStateOf(DisputeStatus.UNDER_REVIEW) }
    var copiedNotice by remember { mutableStateOf(false) }
    var copiedInviteNotice by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة"
                        )
                    }
                    Text(
                        text = "ملف الميثاق الموثّق",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                val loadedAgreement = (agreementState as? UiState.Success)?.data
                if (loadedAgreement != null) {
                    IconButton(
                        onClick = {
                            val summary = buildCertificateShareText(loadedAgreement)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "شهادة ميثاق رقم ${loadedAgreement.agreementCode}")
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة وثيقة / شهادة الميثاق"))
                        },
                        modifier = Modifier.testTag("detail_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "مشاركة الوثيقة",
                            tint = GoldMetallic
                        )
                    }
                }
            }

            when (agreementState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            Text(text = agreementState.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                is UiState.Success -> {
                    val agreement = agreementState.data
                    if (agreement == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لم يتم العثور على هذا الميثاق.")
                        }
                    } else {
                        val isOwner = agreement.ownerId == currentUserId
                        val isParticipant = isOwner ||
                            (currentUserId in agreement.participantUids) ||
                            (currentUserEmail.isNotBlank() && agreement.participantEmails.any { it.equals(currentUserEmail, ignoreCase = true) }) ||
                            (currentUserPhone.isNotBlank() && agreement.participantPhones.any { it.trim() == currentUserPhone.trim() })
                        val integrityOk = remember(agreement) {
                            AgreementHasher.verifyAgreementIntegrity(agreement)
                        }
                        val isBusy = actionFeedback is ActionFeedback.Loading
                        val agreementInvitations = remember(invitationsState, agreement.id) {
                            (invitationsState as? UiState.Success)?.data
                                ?.filter { it.agreementId == agreement.id }
                                .orEmpty()
                        }

                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = GoldMetallic
                        ) {
                            tabTitles.forEachIndexed { idx, label ->
                                Tab(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag("detail_tab_$idx")
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FeedbackBanner(
                                actionFeedback = actionFeedback,
                                onClearFeedback = onClearFeedback
                            )

                            when (selectedTab) {
                                0 -> {
                                    // Header Summary Card
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.5.dp, GoldMetallic.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${agreement.agreementCode} • ${agreement.currentVersionId}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = GoldMetallic,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                AgreementStatusBadge(status = agreement.statusEnum)
                                            }
                                            Text(
                                                text = agreement.title,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${agreement.typeEnum.arabicLabel} • ${agreement.paymentMethodEnum.arabicLabel}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = formatCurrencyAmount(agreement.amount, agreement.currencyEnum),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = GoldMetallic,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (agreement.agreementDate.isNotBlank()) {
                                                Text(
                                                    text = "تاريخ إبرام الاتفاق: ${agreement.agreementDate}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (agreement.startDate.isNotBlank() || agreement.dueDate.isNotBlank()) {
                                                Text(
                                                    text = "المدة: ${agreement.startDate.ifBlank { "—" }} إلى ${agreement.dueDate.ifBlank { "—" }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (agreement.archived) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = DraftGray.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, DraftGray.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.Archive, contentDescription = null, tint = DraftGray)
                                                Text(
                                                    text = "هذه المسودة مؤرشفة (حذف منطقي) ولا تظهر في السجل النشط.",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }

                                    // Immutable Snapshot Lock Banner
                                    if (agreement.lockedAt != null || agreement.approvedSnapshot != null) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = EmeraldVerified.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldVerified)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "النسخة المعتمدة مقفلة وغير قابلة للتعديل",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = EmeraldVerified,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "تاريخ القفل: ${formatArabicDate(agreement.lockedAt)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!agreement.editRequestNote.isNullOrBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = EditOrange.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.45f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "ملاحظة التعديل المطلوب:",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = EditOrange,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = agreement.editRequestNote,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }

                                    // Description & Structured Clauses
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "الوصف ونطاق الاتفاق",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = GoldMetallic,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(text = agreement.description, style = MaterialTheme.typography.bodyLarge)

                                            if (agreement.rights.isNotEmpty()) {
                                                HorizontalDivider()
                                                ClauseGroupDisplay("الحقوق", agreement.rights)
                                            }
                                            if (agreement.obligations.isNotEmpty()) {
                                                HorizontalDivider()
                                                ClauseGroupDisplay("الالتزامات", agreement.obligations)
                                            }
                                            if (agreement.specialTerms.isNotEmpty()) {
                                                HorizontalDivider()
                                                ClauseGroupDisplay("الشروط الخاصة", agreement.specialTerms)
                                            }
                                        }
                                    }

                                    // Parties & Witnesses
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
                                                text = "الأطراف (${agreement.parties.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = GoldMetallic,
                                                fontWeight = FontWeight.Bold
                                            )
                                            agreement.parties.forEachIndexed { idx, p ->
                                                PartyDetailRow(
                                                    title = "${p.name} (${p.role})",
                                                    subtitle = listOfNotNull(
                                                        p.email.takeIf { it.isNotBlank() },
                                                        p.phone.takeIf { it.isNotBlank() }
                                                    ).joinToString(" • "),
                                                    approved = p.approved,
                                                    verifiedPhone = p.verifiedPhone,
                                                    biometricVerified = p.biometricVerified,
                                                    credentialId = p.credentialId
                                                )
                                                if (idx < agreement.parties.lastIndex) HorizontalDivider()
                                            }

                                            if (agreement.witnesses.isNotEmpty()) {
                                                HorizontalDivider()
                                                Text(
                                                    text = "الشهود (${agreement.witnesses.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = GoldMetallic,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                agreement.witnesses.forEach { w ->
                                                    PartyDetailRow(
                                                        title = w.name,
                                                        subtitle = listOfNotNull(
                                                            w.email.takeIf { it.isNotBlank() },
                                                            w.phone.takeIf { it.isNotBlank() }
                                                        ).joinToString(" • "),
                                                        approved = w.approvedAtMillis != null,
                                                        verifiedPhone = w.verifiedPhone,
                                                        biometricVerified = w.biometricVerified,
                                                        credentialId = w.credentialId
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Dual Verification & Transactional Approval Section
                                    if (agreement.statusEnum == AgreementStatus.PENDING ||
                                        (signaturesState as? UiState.Success)?.data?.isNotEmpty() == true
                                    ) {
                                        DualVerificationSection(
                                            agreement = agreement,
                                            signaturesState = signaturesState,
                                            currentUserId = currentUserId,
                                            currentUserEmail = currentUserEmail,
                                            currentUserPhone = currentUserPhone,
                                            activeOtpChallenge = activeOtpChallenge,
                                            otpVerifiedLocallyForAgreement = otpVerifiedLocally,
                                            pendingBiometricSignature = pendingBiometricSignature,
                                            isBusy = isBusy,
                                            onRequestOtp = { target -> onRequestOtp(agreement, target) },
                                            onVerifyOtp = { challengeId, code -> onVerifyOtp(agreement, challengeId, code) },
                                            onBiometricSigned = onBiometricSigned,
                                            onSignAgreement = { phoneOk, bioOk, credId, method, sigHex ->
                                                onSignAgreement(agreement, phoneOk, bioOk, credId, method, sigHex)
                                            },
                                            onApproveAndLockAgreement = { note -> onApproveAgreement(agreement, note) },
                                            onLocalFeedbackError = onLocalFeedbackError
                                        )
                                    }

                                    // Amendments Section for Approved Agreements
                                    AmendmentsSection(
                                        agreement = agreement,
                                        amendmentsState = amendmentsState,
                                        currentUserId = currentUserId,
                                        currentUserEmail = currentUserEmail,
                                        currentUserPhone = currentUserPhone,
                                        isParticipant = isParticipant,
                                        isBusy = isBusy,
                                        onRequestAmendment = { reason, pTitle, pDesc, pAmount, pDue, pRights, pOblig, pTerms ->
                                            onRequestAmendment(
                                                agreement,
                                                reason,
                                                pTitle,
                                                pDesc,
                                                pAmount,
                                                pDue,
                                                pRights,
                                                pOblig,
                                                pTerms
                                            )
                                        },
                                        onRespondToAmendment = { amend, approve, note ->
                                            onRespondToAmendment(agreement, amend, approve, note)
                                        }
                                    )

                                    // Attachments Card
                                    if (agreement.attachments.isNotEmpty()) {
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
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = GoldMetallic)
                                                    Text(
                                                        text = "المرفقات الموثّقة (${agreement.attachments.size})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = GoldMetallic,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                agreement.attachments.forEachIndexed { idx, att ->
                                                    val sizeLabel = if (att.sizeBytes >= 1024L * 1024L) {
                                                        String.format(java.util.Locale.US, "%.2f MB", att.sizeBytes / (1024.0 * 1024.0))
                                                    } else {
                                                        "${(att.sizeBytes / 1024L).coerceAtLeast(1L)} KB"
                                                    }
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(att.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            text = "${att.mimeType} • $sizeLabel • SHA-256: ${att.sha256Hash.take(16)}…",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (idx < agreement.attachments.lastIndex) HorizontalDivider()
                                                }
                                            }
                                        }
                                    }

                                    // Invitations & Multi-Channel Share Card
                                    if (agreement.statusEnum == AgreementStatus.PENDING || agreementInvitations.isNotEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.45f))
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
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Link, contentDescription = null, tint = GoldMetallic)
                                                        Text(
                                                            text = "روابط الدعوة والمشاركة",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = GoldMetallic,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    if (isOwner && agreement.statusEnum == AgreementStatus.PENDING && !agreement.archived) {
                                                        TextButton(
                                                            onClick = { onSendInvitations(agreement) },
                                                            enabled = !isBusy,
                                                            modifier = Modifier.testTag("detail_refresh_invites_button")
                                                        ) {
                                                            Text("إصدار / تجديد")
                                                        }
                                                    }
                                                }

                                                if (copiedInviteNotice != null) {
                                                    Text(
                                                        text = copiedInviteNotice!!,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = EmeraldVerified,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                if (agreementInvitations.isEmpty()) {
                                                    Text(
                                                        text = "لم تُنشأ روابط دعوة بعد. اضغط على «إصدار / تجديد» لإنشاء روابط موثّقة صالحة لمدة 7 أيام.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    agreementInvitations.forEachIndexed { idx, inv ->
                                                        val inviteUrl = "https://meethaq.app/invite/${inv.id}"
                                                        val inviteMessage = buildString {
                                                            appendLine("دعوة لمراجعة ميثاق موثّق عبر منصة ميثاق MEETHAQ:")
                                                            appendLine("رقم الميثاق: ${agreement.agreementCode}")
                                                            appendLine("العنوان: ${agreement.title}")
                                                            appendLine("رابط الدعوة (صالح 7 أيام ولمرة واحدة):")
                                                            append(inviteUrl)
                                                        }
                                                        val isExpired = inv.expiresAt?.toDate()?.time?.let { it < System.currentTimeMillis() } == true
                                                        val statusLabel = when {
                                                            inv.usedAt != null -> "مستخدمة"
                                                            isExpired -> "منتهية"
                                                            else -> "نشطة"
                                                        }
                                                        val statusColor = when {
                                                            inv.usedAt != null -> PendingBlue
                                                            isExpired -> EditOrange
                                                            else -> EmeraldVerified
                                                        }

                                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = inv.inviteePhone.ifBlank { inv.inviteeEmail.ifBlank { "طرف مشارك" } },
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                    Text(
                                                                        text = "${inv.purposeEnum.arabicLabel} • ${inv.id.take(14)}…",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                                Surface(
                                                                    shape = RoundedCornerShape(50),
                                                                    color = statusColor.copy(alpha = 0.14f)
                                                                ) {
                                                                    Text(
                                                                        text = statusLabel,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = statusColor,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                                    )
                                                                }
                                                            }

                                                            FlowRow(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        clipboard.setText(AnnotatedString(inviteUrl))
                                                                        copiedInviteNotice = "تم نسخ رابط الدعوة إلى الحافظة."
                                                                    },
                                                                    modifier = Modifier
                                                                        .height(48.dp)
                                                                        .testTag("invite_copy_button_$idx")
                                                                ) {
                                                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text("نسخ الرابط")
                                                                }

                                                                OutlinedButton(
                                                                    onClick = {
                                                                        val waIntent = Intent(Intent.ACTION_SEND).apply {
                                                                            type = "text/plain"
                                                                            putExtra(Intent.EXTRA_TEXT, inviteMessage)
                                                                            setPackage("com.whatsapp")
                                                                        }
                                                                        try {
                                                                            context.startActivity(waIntent)
                                                                        } catch (_: ActivityNotFoundException) {
                                                                            val fallback = Intent(Intent.ACTION_SEND).apply {
                                                                                type = "text/plain"
                                                                                putExtra(Intent.EXTRA_TEXT, inviteMessage)
                                                                            }
                                                                            context.startActivity(Intent.createChooser(fallback, "مشاركة عبر واتساب أو تطبيق آخر"))
                                                                        }
                                                                    },
                                                                    modifier = Modifier
                                                                        .height(48.dp)
                                                                        .testTag("invite_whatsapp_button_$idx"),
                                                                    border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.6f))
                                                                ) {
                                                                    Icon(Icons.Filled.Chat, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(16.dp))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text("واتساب", color = EmeraldVerified)
                                                                }

                                                                OutlinedButton(
                                                                    onClick = {
                                                                        val smsUri = Uri.parse("smsto:${Uri.encode(inv.inviteePhone)}")
                                                                        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                                                            putExtra("sms_body", inviteMessage)
                                                                        }
                                                                        try {
                                                                            context.startActivity(smsIntent)
                                                                        } catch (_: ActivityNotFoundException) {
                                                                            val fallback = Intent(Intent.ACTION_SEND).apply {
                                                                                type = "text/plain"
                                                                                putExtra(Intent.EXTRA_TEXT, inviteMessage)
                                                                            }
                                                                            context.startActivity(Intent.createChooser(fallback, "إرسال رسالة الدعوة"))
                                                                        }
                                                                    },
                                                                    modifier = Modifier
                                                                        .height(48.dp)
                                                                        .testTag("invite_sms_button_$idx"),
                                                                    border = BorderStroke(1.dp, PendingBlue.copy(alpha = 0.6f))
                                                                ) {
                                                                    Icon(Icons.Filled.Sms, contentDescription = null, tint = PendingBlue, modifier = Modifier.size(16.dp))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text("SMS", color = PendingBlue)
                                                                }

                                                                OutlinedButton(
                                                                    onClick = {
                                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                            type = "text/plain"
                                                                            putExtra(Intent.EXTRA_SUBJECT, "دعوة ميثاق ${agreement.agreementCode}")
                                                                            putExtra(Intent.EXTRA_TEXT, inviteMessage)
                                                                        }
                                                                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة رابط الدعوة"))
                                                                    },
                                                                    modifier = Modifier
                                                                        .height(48.dp)
                                                                        .testTag("invite_share_button_$idx")
                                                                ) {
                                                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text("مشاركة")
                                                                }
                                                            }
                                                        }
                                                        if (idx < agreementInvitations.lastIndex) HorizontalDivider()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // SHA-256 Verification Box
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (integrityOk) EmeraldVerified.copy(alpha = 0.08f) else CrimsonRejected.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, if (integrityOk) EmeraldVerified.copy(alpha = 0.4f) else CrimsonRejected)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (integrityOk) Icons.Filled.Shield else Icons.Filled.Warning,
                                                        contentDescription = null,
                                                        tint = if (integrityOk) EmeraldVerified else CrimsonRejected
                                                    )
                                                    Text(
                                                        text = if (integrityOk) "السلامة الرقمية سليمة 100%" else "تحذير: البصمة لا تطابق المحتوى",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (integrityOk) EmeraldVerified else CrimsonRejected,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString(agreement.contentHash))
                                                        copiedNotice = true
                                                    }
                                                ) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ البصمة", tint = GoldMetallic)
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(text = agreement.contentHash, style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (!agreement.approvedHash.isNullOrBlank()) {
                                                Text(
                                                    text = "approvedHash: ${agreement.approvedHash}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldVerified
                                                )
                                            }
                                            if (copiedNotice) {
                                                Text("تم نسخ بصمة SHA-256.", style = MaterialTheme.typography.labelSmall, color = GoldMetallic)
                                            }
                                        }
                                    }

                                    // Approved Certificate Export Button
                                    if (agreement.statusEnum == AgreementStatus.APPROVED) {
                                        OutlinedButton(
                                            onClick = {
                                                val cert = buildCertificateShareText(agreement)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "شهادة ميثاق معتمدة ${agreement.agreementCode}")
                                                    putExtra(Intent.EXTRA_TEXT, cert)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "تصدير ومشاركة الشهادة الموثّقة"))
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("detail_export_certificate_button"),
                                            border = BorderStroke(1.dp, EmeraldVerified)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("تصدير ومشاركة شهادة التوثيق المعتمدة", color = EmeraldVerified, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Action Controls
                                    if (isOwner && !agreement.archived && (agreement.statusEnum == AgreementStatus.DRAFT || agreement.statusEnum == AgreementStatus.NEEDS_EDIT)) {
                                        Button(
                                            onClick = { onEditDraftOrNeedsEdit(agreement) },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("detail_edit_agreement_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoldMetallic,
                                                contentColor = NavyDeep
                                            )
                                        ) {
                                            Icon(Icons.Filled.EditNote, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("تعديل البنود وإعادة الإرسال", fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onSendInvitations(agreement) },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("detail_send_invitations_button"),
                                                border = BorderStroke(1.dp, PendingBlue)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = PendingBlue, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("إرسال الدعوات", color = PendingBlue, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = { showArchiveDialog = true },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("detail_archive_agreement_button"),
                                                border = BorderStroke(1.dp, CrimsonRejected)
                                            ) {
                                                Icon(Icons.Filled.Archive, contentDescription = null, tint = CrimsonRejected, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("أرشفة المسودة", color = CrimsonRejected, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (agreement.statusEnum == AgreementStatus.PENDING && isParticipant && !isOwner) {
                                        OutlinedButton(
                                            onClick = {
                                                noteInput = ""
                                                showRequestEditDialog = true
                                            },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("detail_request_edit_button"),
                                            border = BorderStroke(1.dp, EditOrange)
                                        ) {
                                            Text("طلب تعديل المسودة وإعادتها للمنشئ", color = EditOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (isParticipant && (agreement.statusEnum == AgreementStatus.PENDING || agreement.statusEnum == AgreementStatus.APPROVED)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showExpireDialog = true },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("detail_expire_button"),
                                                border = BorderStroke(1.dp, ExpiredPurple)
                                            ) {
                                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = ExpiredPurple, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("إنهاء الميثاق", color = ExpiredPurple, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    disputeTypeInput = ""
                                                    disputeDetailsInput = ""
                                                    disputeEvidenceInput = ""
                                                    showOpenDisputeDialog = true
                                                },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("detail_dispute_button"),
                                                border = BorderStroke(1.dp, CrimsonRejected)
                                            ) {
                                                Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = CrimsonRejected, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("فتح نزاع", color = CrimsonRejected, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    PaymentsLedgerTab(
                                        agreement = agreement,
                                        paymentsState = paymentsState,
                                        currentUserId = currentUserId,
                                        currentUserEmail = currentUserEmail,
                                        currentUserPhone = currentUserPhone,
                                        verifiedWitnessOtpChallengeId = verifiedWitnessOtpChallengeId,
                                        isParticipant = isParticipant,
                                        isBusy = isBusy,
                                        onRecordPayment = { amount, method, receiptRef, dateText, attachment ->
                                            onRecordPayment(agreement, amount, method, receiptRef, dateText, attachment)
                                        },
                                        onConfirmPayment = { payment, mode, confirmedAmt, note, witnessChallengeId ->
                                            onConfirmPayment(agreement, payment, mode, confirmedAmt, note, witnessChallengeId)
                                        }
                                    )
                                }

                                2 -> {
                                    // Disputes Tab
                                    if (isParticipant && (agreement.statusEnum == AgreementStatus.PENDING || agreement.statusEnum == AgreementStatus.APPROVED)) {
                                        OutlinedButton(
                                            onClick = {
                                                disputeTypeInput = ""
                                                disputeDetailsInput = ""
                                                disputeEvidenceInput = ""
                                                showOpenDisputeDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            border = BorderStroke(1.dp, CrimsonRejected)
                                        ) {
                                            Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = CrimsonRejected)
                                            Spacer(Modifier.width(6.dp))
                                            Text("فتح ملف نزاع جديد", color = CrimsonRejected, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    when (disputesState) {
                                        is UiState.Loading -> CircularProgressIndicator(color = GoldMetallic)
                                        is UiState.Error -> Text(disputesState.message, color = MaterialTheme.colorScheme.error)
                                        is UiState.Success -> {
                                            if (disputesState.data.isEmpty()) {
                                                Text(
                                                    text = "لا توجد نزاعات مسجلة على هذا الميثاق.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                disputesState.data.forEach { dispute ->
                                                    Surface(
                                                        shape = MaterialTheme.shapes.medium,
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, CrimsonRejected.copy(alpha = 0.4f))
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(dispute.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                                Text(
                                                                    text = dispute.statusEnum.arabicLabel,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = CrimsonRejected,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                            Text("مُقدّم من: ${dispute.openedByName} • ${formatArabicDate(dispute.createdAt)}", style = MaterialTheme.typography.labelSmall)
                                                            Text(dispute.details, style = MaterialTheme.typography.bodyMedium)

                                                            if (dispute.evidenceNotes.isNotEmpty()) {
                                                                Text("الأدلة والمرفقات الموثّقة:", style = MaterialTheme.typography.labelMedium, color = GoldMetallic)
                                                                dispute.evidenceNotes.forEach { ev ->
                                                                    Text("• $ev", style = MaterialTheme.typography.bodySmall)
                                                                }
                                                            }

                                                            if (dispute.replies.isNotEmpty()) {
                                                                HorizontalDivider()
                                                                Text("الردود (${dispute.replies.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                                dispute.replies.forEach { reply ->
                                                                    Surface(
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceVariant
                                                                    ) {
                                                                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                                                            Text(
                                                                                text = "${reply.authorName} • ${formatMillisDate(reply.createdAtMillis)}",
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = GoldMetallic
                                                                            )
                                                                            Text(reply.message, style = MaterialTheme.typography.bodySmall)
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            if (isParticipant) {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        replyMessageInput = ""
                                                                        replyStatusInput = dispute.statusEnum
                                                                        disputeToReply = dispute
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Text("إضافة رد / تحديث حالة النزاع")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                3 -> {
                                    // Audit & Versions Tab
                                    if (agreement.versions.isNotEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "إصدارات الميثاق (${agreement.versions.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = GoldMetallic,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                agreement.versions.forEach { ver ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("${ver.versionId} • ${ver.hash.take(14)}…", style = MaterialTheme.typography.labelMedium)
                                                        Text(formatMillisDate(ver.createdAtMillis), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

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
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.History, contentDescription = null, tint = GoldMetallic)
                                                Text(
                                                    text = "سجل التدقيق غير القابل للتعديل (Append-Only)",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            when (auditState) {
                                                is UiState.Loading -> CircularProgressIndicator(color = GoldMetallic)
                                                is UiState.Error -> Text(auditState.message, color = MaterialTheme.colorScheme.error)
                                                is UiState.Success -> {
                                                    if (auditState.data.isEmpty()) {
                                                        Text("لا توجد سجلات.", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        auditState.data.forEach { entry ->
                                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                                Text(entry.action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                                Text(
                                                                    text = "بواسطة: ${entry.actorName} • ${formatArabicDate(entry.createdAt)}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                                if (!entry.note.isNullOrBlank()) {
                                                                    Text(entry.note, style = MaterialTheme.typography.bodySmall)
                                                                }
                                                                if (entry.metadata.isNotEmpty()) {
                                                                    Text(
                                                                        text = entry.metadata.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = GoldMetallic
                                                                    )
                                                                }
                                                                if (!entry.snapshotHash.isNullOrBlank()) {
                                                                    Text(
                                                                        text = "hash: ${entry.snapshotHash.take(16)}…",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Dialogs

                        if (showRequestEditDialog) {
                            AlertDialog(
                                onDismissRequest = { showRequestEditDialog = false },
                                title = { Text("طلب تعديل الميثاق") },
                                text = {
                                    OutlinedTextField(
                                        value = noteInput,
                                        onValueChange = { noteInput = it.take(500) },
                                        label = { Text("اذكر التعديلات المطلوبة *") },
                                        minLines = 2,
                                        maxLines = 4
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showRequestEditDialog = false
                                            onRequestEdits(agreement, noteInput)
                                        },
                                        enabled = noteInput.trim().length >= 3,
                                        colors = ButtonDefaults.buttonColors(containerColor = EditOrange)
                                    ) { Text("إرسال طلب التعديل") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRequestEditDialog = false }) { Text("تراجع") }
                                }
                            )
                        }

                        if (showExpireDialog) {
                            AlertDialog(
                                onDismissRequest = { showExpireDialog = false },
                                title = { Text("إغلاق الميثاق بحالة منتهٍ") },
                                text = { Text("هل تريد تحويل حالة الميثاق إلى (منتهٍ) لإتمام مدته؟") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showExpireDialog = false
                                            onMarkExpired(agreement)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ExpiredPurple)
                                    ) { Text("تأكيد الإنهاء") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExpireDialog = false }) { Text("تراجع") }
                                }
                            )
                        }

                        if (showArchiveDialog) {
                            AlertDialog(
                                onDismissRequest = { showArchiveDialog = false },
                                title = { Text("أرشفة المسودة (حذف منطقي)") },
                                text = { Text("سيتم إخفاء المسودة من السجل النشط مع الإبقاء على سجلها الموثّق غير المحذوف في قاعدة البيانات. هل تريد المتابعة؟") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showArchiveDialog = false
                                            onArchiveAgreement(agreement)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRejected)
                                    ) { Text("تأكيد الأرشفة") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showArchiveDialog = false }) { Text("تراجع") }
                                }
                            )
                        }


                        if (showOpenDisputeDialog) {
                            AlertDialog(
                                onDismissRequest = { showOpenDisputeDialog = false },
                                title = { Text("فتح نزاع رسمي") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = disputeTypeInput,
                                            onValueChange = { disputeTypeInput = it.take(80) },
                                            label = { Text("نوع النزاع * (مثال: تأخير دفعة)") }
                                        )
                                        OutlinedTextField(
                                            value = disputeDetailsInput,
                                            onValueChange = { disputeDetailsInput = it.take(2000) },
                                            label = { Text("التفاصيل * (10 أحرف على الأقل)") },
                                            minLines = 3
                                        )
                                        OutlinedTextField(
                                            value = disputeEvidenceInput,
                                            onValueChange = { disputeEvidenceInput = it.take(500) },
                                            label = { Text("مرجع الأدلة / الإيصالات (اختياري)") }
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showOpenDisputeDialog = false
                                            val evidence = listOf(disputeEvidenceInput).filter { it.isNotBlank() }
                                            onOpenDispute(agreement, disputeTypeInput, disputeDetailsInput, evidence)
                                        },
                                        enabled = disputeTypeInput.trim().length >= 2 && disputeDetailsInput.trim().length >= 10,
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRejected)
                                    ) { Text("فتح النزاع") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showOpenDisputeDialog = false }) { Text("تراجع") }
                                }
                            )
                        }

                        disputeToReply?.let { dispute ->
                            AlertDialog(
                                onDismissRequest = { disputeToReply = null },
                                title = { Text("الرد على النزاع") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DisputeStatus.entries.forEach { st ->
                                                FilterChip(
                                                    selected = replyStatusInput == st,
                                                    onClick = { replyStatusInput = st },
                                                    label = { Text(st.arabicLabel) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = GoldMetallic,
                                                        selectedLabelColor = NavyDeep
                                                    )
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            value = replyMessageInput,
                                            onValueChange = { replyMessageInput = it.take(800) },
                                            label = { Text("نص الرد *") },
                                            minLines = 2
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val target = dispute
                                            disputeToReply = null
                                            onReplyToDispute(agreement, target, replyMessageInput, replyStatusInput)
                                        },
                                        enabled = replyMessageInput.trim().length >= 2
                                    ) { Text("إرسال الرد") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { disputeToReply = null }) { Text("تراجع") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    actionFeedback: ActionFeedback,
    onClearFeedback: () -> Unit
) {
    when (actionFeedback) {
        is ActionFeedback.Success -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = EmeraldVerified.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldVerified)
                        Text(actionFeedback.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                }
            }
        }
        is ActionFeedback.Error -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = actionFeedback.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ClauseGroupDisplay(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = GoldMetallic,
            fontWeight = FontWeight.Bold
        )
        items.forEachIndexed { idx, item ->
            Text(
                text = "${idx + 1}. $item",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PartyDetailRow(
    title: String,
    subtitle: String,
    approved: Boolean,
    verifiedPhone: Boolean = false,
    biometricVerified: Boolean = false,
    credentialId: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = if (approved) EmeraldVerified else GoldMetallic,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (verifiedPhone || biometricVerified || !credentialId.isNullOrBlank()) {
                    val badges = buildList {
                        if (verifiedPhone) add("OTP ✓")
                        if (biometricVerified) add("بصمة ✓")
                        else if (!credentialId.isNullOrBlank()) add("Passkey ✓")
                    }.joinToString(" • ")
                    Text(
                        text = badges,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldVerified,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Text(
            text = if (approved) "موقّع ومُعتمد" else "بانتظار التوقيع",
            style = MaterialTheme.typography.labelSmall,
            color = if (approved) EmeraldVerified else GoldMetallic,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun buildCertificateShareText(agreement: Agreement): String = buildString {
    appendLine("شهادة توثيق — منصة ميثاق MEETHAQ")
    appendLine("رقم الميثاق: ${agreement.agreementCode} (${agreement.currentVersionId})")
    appendLine("العنوان: ${agreement.title}")
    appendLine("الحالة: ${agreement.statusEnum.arabicLabel}")
    appendLine("النوع: ${agreement.typeEnum.arabicLabel}")
    appendLine("المبلغ: ${formatCurrencyAmount(agreement.amount, agreement.currencyEnum)}")
    if (agreement.paidTotal > 0.0) {
        appendLine("المدفوع المؤكد: ${formatCurrencyAmount(agreement.paidTotal, agreement.currencyEnum)}")
    }
    if (agreement.agreementDate.isNotBlank()) {
        appendLine("تاريخ الاتفاق: ${agreement.agreementDate}")
    }
    if (agreement.lockedAt != null) {
        appendLine("تاريخ القفل النهائي: ${formatArabicDate(agreement.lockedAt)}")
    }
    if (agreement.parties.isNotEmpty()) {
        appendLine("الأطراف: ${agreement.parties.joinToString("، ") { "${it.name} (${it.role})" }}")
    }
    appendLine("الوصف: ${agreement.description}")
    appendLine("بصمة المحتوى SHA-256:")
    appendLine(agreement.contentHash)
    if (!agreement.approvedHash.isNullOrBlank()) {
        appendLine("بصمة الاعتماد القانونية approvedHash:")
        appendLine(agreement.approvedHash)
    }
}

