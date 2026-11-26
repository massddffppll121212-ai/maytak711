package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Agreement
import com.example.model.AgreementStatus
import com.example.model.UiState
import com.example.ui.components.AgreementCard
import com.example.ui.components.EmptyVaultCard
import com.example.ui.components.OfficialBrandBanner
import com.example.ui.components.SummaryMetricCard
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.DraftGray
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ExpiredPurple
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue

private data class ChartSegment(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
fun DashboardScreen(
    agreementsState: UiState<List<Agreement>>,
    currentUserName: String,
    currentUserEmail: String,
    currentUserPhone: String,
    currentUserId: String,
    onCreateAgreementClick: () -> Unit,
    onViewAllAgreementsClick: () -> Unit,
    onOpenVerifyClick: () -> Unit,
    onOpenAccountClick: () -> Unit,
    onOpenInviteToken: (String) -> Unit,
    onAgreementClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }
    var inviteInputError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OfficialBrandBanner(
                    title = "مرحباً، $currentUserName",
                    subtitle = "اتفاق موثّق.. حق محفوظ.. بلا شهود — إدارة متكاملة للأطراف والشهود والدفعات والنزاعات ببصمة SHA-256.",
                    badgeText = "خزنة ميثاق السحابية"
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateAgreementClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("dashboard_create_agreement_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldMetallic,
                            contentColor = NavyDeep
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صياغة ميثاق جديد",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Quick Action Strip for all app sections + Invite Link opener
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSectionChip(
                            label = "سجل المواثيق",
                            icon = Icons.Filled.Description,
                            onClick = onViewAllAgreementsClick,
                            testTag = "quick_agreements_button"
                        )
                        QuickSectionChip(
                            label = "التحقق والخزنة",
                            icon = Icons.Filled.Fingerprint,
                            onClick = onOpenVerifyClick,
                            testTag = "quick_verify_button"
                        )
                        QuickSectionChip(
                            label = "فتح رابط دعوة",
                            icon = Icons.Filled.Link,
                            onClick = {
                                inviteInput = ""
                                inviteInputError = null
                                showInviteDialog = true
                            },
                            testTag = "quick_open_invite_button"
                        )
                        QuickSectionChip(
                            label = "الحساب والأمان",
                            icon = Icons.Filled.Person,
                            onClick = onOpenAccountClick,
                            testTag = "quick_account_button"
                        )
                    }
                }
            }

            when (agreementsState) {
                is UiState.Loading -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = GoldMetallic,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "جارٍ مزامنة مواثيقك الموثّقة من السحابة…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "خطأ",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = agreementsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val all = agreementsState.data
                    val actionRequired = all.filter { ag ->
                        val isInvitedMember =
                            (currentUserEmail.isNotBlank() && ag.participantEmails.any { it.equals(currentUserEmail, ignoreCase = true) }) ||
                                (currentUserPhone.isNotBlank() && ag.participantPhones.any { it.trim() == currentUserPhone.trim() })
                        (ag.statusEnum == AgreementStatus.PENDING && ag.ownerId != currentUserId && isInvitedMember) ||
                            (ag.statusEnum == AgreementStatus.NEEDS_EDIT && ag.ownerId == currentUserId)
                    }

                    val approvedCount = all.count { it.statusEnum == AgreementStatus.APPROVED }
                    val pendingCount = all.count { it.statusEnum == AgreementStatus.PENDING }
                    val needsEditCount = all.count { it.statusEnum == AgreementStatus.NEEDS_EDIT }
                    val disputedCount = all.count { it.statusEnum == AgreementStatus.DISPUTED }
                    val draftCount = all.count { it.statusEnum == AgreementStatus.DRAFT }
                    val expiredCount = all.count { it.statusEnum == AgreementStatus.EXPIRED }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SummaryMetricCard(
                                    title = "معتمد",
                                    count = approvedCount,
                                    icon = Icons.Filled.Verified,
                                    accentColor = EmeraldVerified,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricCard(
                                    title = "بانتظار الموافقة",
                                    count = pendingCount,
                                    icon = Icons.Filled.HourglassTop,
                                    accentColor = PendingBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricCard(
                                    title = "يحتاج تعديل",
                                    count = needsEditCount,
                                    icon = Icons.Filled.Warning,
                                    accentColor = EditOrange,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SummaryMetricCard(
                                    title = "مسودة",
                                    count = draftCount,
                                    icon = Icons.Filled.EditNote,
                                    accentColor = DraftGray,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricCard(
                                    title = "منتهٍ",
                                    count = expiredCount,
                                    icon = Icons.Filled.Schedule,
                                    accentColor = ExpiredPurple,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricCard(
                                    title = "متنازع",
                                    count = disputedCount,
                                    icon = Icons.Filled.ReportProblem,
                                    accentColor = CrimsonRejected,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (all.isNotEmpty()) {
                        item {
                            StatusDistributionChartCard(
                                segments = listOf(
                                    ChartSegment("معتمد", approvedCount, EmeraldVerified),
                                    ChartSegment("بانتظار الموافقة", pendingCount, PendingBlue),
                                    ChartSegment("مسودة", draftCount, DraftGray),
                                    ChartSegment("يحتاج تعديل", needsEditCount, EditOrange),
                                    ChartSegment("منتهٍ", expiredCount, ExpiredPurple),
                                    ChartSegment("متنازع", disputedCount, CrimsonRejected)
                                ),
                                totalCount = all.size
                            )
                        }
                    }

                    if (actionRequired.isNotEmpty()) {
                        item {
                            Text(
                                text = "مواثيق تتطلب إجراءك الآن",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldMetallic,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(actionRequired.take(3), key = { "action_${it.id}" }) { agreement ->
                            AgreementCard(
                                agreement = agreement,
                                currentUserEmail = currentUserEmail,
                                currentUserId = currentUserId,
                                onClick = { onAgreementClick(agreement.id) }
                            )
                        }
                    }

                    if (all.isEmpty()) {
                        item {
                            EmptyVaultCard(
                                title = "لا توجد مواثيق مسجلة حتى الآن",
                                subtitle = "ابدأ بصياغة أول ميثاق رسمي مع الأطراف والشهود والحقوق والالتزامات، وسيتم حفظه ببصمة SHA-256 في السحابة.",
                                actionButton = {
                                    OutlinedButton(
                                        onClick = onCreateAgreementClick,
                                        modifier = Modifier.testTag("empty_state_create_button"),
                                        border = BorderStroke(1.dp, GoldMetallic)
                                    ) {
                                        Text(
                                            text = "إنشاء أول ميثاق",
                                            color = GoldMetallic,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "أحدث 5 مواثيق",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = onViewAllAgreementsClick,
                                    modifier = Modifier.testTag("view_all_agreements_button"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "عرض الكل (${all.size})",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        items(all.take(5), key = { it.id }) { agreement ->
                            AgreementCard(
                                agreement = agreement,
                                currentUserEmail = currentUserEmail,
                                currentUserId = currentUserId,
                                onClick = { onAgreementClick(agreement.id) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("فتح دعوة ميثاق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الصق رابط الدعوة (https://meethaq.app/invite/...) أو أدخل رمز الدعوة مباشرة:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = {
                            inviteInput = it
                            inviteInputError = null
                        },
                        label = { Text("رابط أو رمز الدعوة") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_invite_input")
                    )
                    if (inviteInputError != null) {
                        Text(
                            text = inviteInputError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = extractInviteToken(inviteInput)
                        if (token == null) {
                            inviteInputError = "الرمز غير صالح، تأكد من لصق الرابط أو الرمز كاملاً."
                        } else {
                            showInviteDialog = false
                            onOpenInviteToken(token)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("فتح الدعوة", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun QuickSectionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.4f)),
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusDistributionChartCard(
    segments: List<ChartSegment>,
    totalCount: Int
) {
    val activeSegments = segments.filter { it.count > 0 }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_status_chart"),
        shape = MaterialTheme.shapes.large,
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
                Icon(Icons.Filled.PieChart, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(20.dp))
                Text(
                    text = "توزيع حالات المواثيق",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldMetallic,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 22.dp.toPx()
                        if (totalCount <= 0 || activeSegments.isEmpty()) {
                            drawArc(
                                color = DraftGray.copy(alpha = 0.25f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                        } else {
                            var startAngle = -90f
                            val gapAngle = if (activeSegments.size > 1) 4f else 0f
                            val usableAngle = 360f - (gapAngle * activeSegments.size)
                            activeSegments.forEach { seg ->
                                val sweep = (seg.count.toFloat() / totalCount.toFloat()) * usableAngle
                                drawArc(
                                    color = seg.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep.coerceAtLeast(1f),
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep + gapAngle
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إجمالي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    segments.forEach { seg ->
                        val pct = if (totalCount > 0) (seg.count * 100) / totalCount else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(seg.color)
                                )
                                Text(
                                    text = seg.label,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = "${seg.count} ($pct%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
