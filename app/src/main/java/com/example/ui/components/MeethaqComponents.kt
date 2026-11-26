package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.crypto.AgreementHasher
import com.example.model.Agreement
import com.example.model.AgreementStatus
import com.example.model.CurrencyCode
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.CrimsonRejectedContainerDark
import com.example.ui.theme.CrimsonRejectedContainerLight
import com.example.ui.theme.DraftGray
import com.example.ui.theme.DraftGrayContainerDark
import com.example.ui.theme.DraftGrayContainerLight
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EditOrangeContainerDark
import com.example.ui.theme.EditOrangeContainerLight
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.EmeraldVerifiedContainerDark
import com.example.ui.theme.EmeraldVerifiedContainerLight
import com.example.ui.theme.ExpiredPurple
import com.example.ui.theme.ExpiredPurpleContainerDark
import com.example.ui.theme.ExpiredPurpleContainerLight
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldDeep
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PendingBlue
import com.example.ui.theme.PendingBlueContainerDark
import com.example.ui.theme.PendingBlueContainerLight
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun formatArabicDate(timestamp: Timestamp?): String {
    val safeTs = timestamp ?: return "قيد التوثيق…"
    val formatter = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("ar"))
    return formatter.format(safeTs.toDate())
}

fun formatMillisDate(millis: Long): String {
    if (millis <= 0L) return "غير محدد"
    val formatter = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("ar"))
    return formatter.format(java.util.Date(millis))
}

fun formatCurrencyAmount(amount: Double, currency: CurrencyCode): String {
    if (currency == CurrencyCode.NONE || amount <= 0.0) return "اتفاق غير مالي"
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }
    return "${formatter.format(amount)} ${currency.symbol}"
}

/**
 * شعار ميثاق الرسمي: شخصان متقابلان متحدان في الرأس داخل درع يرمز للعهد.
 */
@Composable
fun MeethaqShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "شعار ميثاق: شخصان متحدان في الرأس داخل درع العهد" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Outer Heraldic Shield Path
        val shieldPath = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.88f, h * 0.17f)
            cubicTo(
                w * 0.89f, h * 0.55f,
                w * 0.74f, h * 0.82f,
                w * 0.50f, h * 0.95f
            )
            cubicTo(
                w * 0.26f, h * 0.82f,
                w * 0.11f, h * 0.55f,
                w * 0.12f, h * 0.17f
            )
            close()
        }

        // Deep Royal Navy Shield Background
        drawPath(
            path = shieldPath,
            brush = Brush.verticalGradient(
                colors = listOf(NavyPrimary, NavyDeep)
            )
        )

        // Metallic Gold Shield Outline
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(GoldBright, GoldMetallic, GoldDeep)
            ),
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Inner Subtle Covenant Rim
        val innerShield = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.81f, h * 0.22f)
            cubicTo(
                w * 0.81f, h * 0.53f,
                w * 0.69f, h * 0.76f,
                w * 0.50f, h * 0.88f
            )
            cubicTo(
                w * 0.31f, h * 0.76f,
                w * 0.19f, h * 0.53f,
                w * 0.19f, h * 0.22f
            )
            close()
        }
        drawPath(
            path = innerShield,
            color = GoldMetallic.copy(alpha = 0.28f),
            style = Stroke(width = w * 0.018f)
        )

        // Shared United Head (Union of Mind & Covenant at Center Top)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldBright, GoldMetallic),
                center = Offset(w * 0.50f, h * 0.33f),
                radius = w * 0.12f
            ),
            radius = w * 0.095f,
            center = Offset(w * 0.50f, h * 0.34f)
        )

        // Left Figure Body leaning inward to meet at the united head & clasped covenant arch
        val leftPerson = Path().apply {
            moveTo(w * 0.30f, h * 0.68f)
            cubicTo(
                w * 0.28f, h * 0.49f,
                w * 0.38f, h * 0.41f,
                w * 0.48f, h * 0.43f
            )
            lineTo(w * 0.49f, h * 0.54f)
            cubicTo(
                w * 0.42f, h * 0.54f,
                w * 0.38f, h * 0.60f,
                w * 0.39f, h * 0.72f
            )
            close()
        }
        drawPath(
            path = leftPerson,
            brush = Brush.verticalGradient(listOf(GoldBright, GoldMetallic))
        )

        // Right Figure Body facing left, united symmetrically at the head
        val rightPerson = Path().apply {
            moveTo(w * 0.70f, h * 0.68f)
            cubicTo(
                w * 0.72f, h * 0.49f,
                w * 0.62f, h * 0.41f,
                w * 0.52f, h * 0.43f
            )
            lineTo(w * 0.51f, h * 0.54f)
            cubicTo(
                w * 0.58f, h * 0.54f,
                w * 0.62f, h * 0.60f,
                w * 0.61f, h * 0.72f
            )
            close()
        }
        drawPath(
            path = rightPerson,
            brush = Brush.verticalGradient(listOf(GoldBright, GoldMetallic))
        )

        // Clasped Covenant Arch linking the two facing figures
        val covenantArch = Path().apply {
            moveTo(w * 0.35f, h * 0.58f)
            quadraticTo(w * 0.50f, h * 0.68f, w * 0.65f, h * 0.58f)
        }
        drawPath(
            path = covenantArch,
            color = Color.White.copy(alpha = 0.9f),
            style = Stroke(width = w * 0.036f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AgreementStatusBadge(
    status: AgreementStatus,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == NavyDeep ||
        (isSystemInDarkTheme() && MaterialTheme.colorScheme.surface != Color.White)

    val (bg, fg, icon) = when (status) {
        AgreementStatus.DRAFT -> Triple(
            if (isDark) DraftGrayContainerDark else DraftGrayContainerLight,
            if (isDark) Color(0xFFB7C0D4) else DraftGray,
            Icons.Filled.EditNote
        )
        AgreementStatus.PENDING -> Triple(
            if (isDark) PendingBlueContainerDark else PendingBlueContainerLight,
            if (isDark) Color(0xFF7FB2FF) else PendingBlue,
            Icons.Filled.HourglassTop
        )
        AgreementStatus.APPROVED -> Triple(
            if (isDark) EmeraldVerifiedContainerDark else EmeraldVerifiedContainerLight,
            if (isDark) Color(0xFF5EE8B0) else EmeraldVerified,
            Icons.Filled.Verified
        )
        AgreementStatus.NEEDS_EDIT -> Triple(
            if (isDark) EditOrangeContainerDark else EditOrangeContainerLight,
            if (isDark) Color(0xFFFFB26B) else EditOrange,
            Icons.Filled.Warning
        )
        AgreementStatus.EXPIRED -> Triple(
            if (isDark) ExpiredPurpleContainerDark else ExpiredPurpleContainerLight,
            if (isDark) Color(0xFFD1A3FF) else ExpiredPurple,
            Icons.Filled.Schedule
        )
        AgreementStatus.DISPUTED -> Triple(
            if (isDark) CrimsonRejectedContainerDark else CrimsonRejectedContainerLight,
            if (isDark) Color(0xFFFF8A96) else CrimsonRejected,
            Icons.Filled.ReportProblem
        )
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.arabicLabel,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.arabicLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AgreementCard(
    agreement: Agreement,
    currentUserEmail: String,
    currentUserId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOwner = agreement.ownerId == currentUserId
    val pendingForMe = agreement.statusEnum == AgreementStatus.PENDING &&
        !isOwner &&
        ((currentUserEmail.isNotBlank() && agreement.participantEmails.any { it.equals(currentUserEmail, ignoreCase = true) }) ||
            (currentUserId in agreement.participantUids) ||
            agreement.participantPhones.isNotEmpty())
    val needsMyEdit = agreement.statusEnum == AgreementStatus.NEEDS_EDIT && isOwner
    val highlighted = pendingForMe || needsMyEdit
    val hashValid = AgreementHasher.verifyAgreementIntegrity(agreement)
    val otherPartyNames = agreement.parties
        .filterNot {
            it.uid == agreement.ownerId ||
                (agreement.ownerEmail.isNotBlank() && it.email.equals(agreement.ownerEmail, ignoreCase = true)) ||
                (agreement.ownerPhone.isNotBlank() && it.phone == agreement.ownerPhone)
        }
        .joinToString("، ") { it.name }
        .ifBlank { "—" }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("agreement_card_${agreement.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = when {
                pendingForMe -> PendingBlue
                needsMyEdit -> EditOrange
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = GoldMetallic,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = agreement.agreementCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldMetallic,
                        fontWeight = FontWeight.Bold
                    )
                    if (agreement.lockedAt != null) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "ميثاق مقفل غير قابل للتعديل",
                            tint = EmeraldVerified,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                AgreementStatusBadge(status = agreement.statusEnum)
            }

            Text(
                text = agreement.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOwner) {
                            "الأطراف: $otherPartyNames"
                        } else {
                            "المُنشئ: ${agreement.ownerName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${agreement.typeEnum.arabicLabel} • ${agreement.currentVersionId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (agreement.witnesses.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = GoldMetallic,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${agreement.witnesses.size} شهود",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldMetallic
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = formatCurrencyAmount(agreement.amount, agreement.currencyEnum),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (hashValid) Icons.Filled.Shield else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (hashValid) EmeraldVerified else CrimsonRejected,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            pendingForMe -> "يتطلب مراجعتك واعتمادك الآن"
                            needsMyEdit -> "مطلوب تعديل البنود وإعادة الإرسال"
                            else -> formatArabicDate(agreement.updatedAt ?: agreement.createdAt)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            pendingForMe -> PendingBlue
                            needsMyEdit -> EditOrange
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "عرض التفاصيل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyVaultCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_vault),
                contentDescription = "خزنة المواثيق",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, GoldMetallic.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionButton != null) {
                Spacer(modifier = Modifier.height(4.dp))
                actionButton()
            }
        }
    }
}

@Composable
fun OfficialBrandBanner(
    title: String,
    subtitle: String,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, GoldMetallic.copy(alpha = 0.45f), MaterialTheme.shapes.large)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_meethaq_hero),
            contentDescription = "شعار ميثاق الرسمي",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NavyPrimary.copy(alpha = 0.88f),
                            NavyPrimary.copy(alpha = 0.72f),
                            NavyDeep.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = GoldMetallic.copy(alpha = 0.22f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, GoldBright.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
