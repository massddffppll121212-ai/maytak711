package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AgreementRepository
import com.example.data.AuthManager
import com.example.data.AuthRateLimiter
import com.example.data.PublicInvitationLookup
import com.example.model.Invitation
import com.example.ui.components.CountryDialCode
import com.example.ui.components.MeethaqShieldLogo
import com.example.ui.components.PhoneCountryPickerField
import com.example.ui.components.PhoneNumberHelper
import com.example.ui.components.formatArabicDate
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

fun extractInviteToken(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val marker = "/invite/"
    val extracted = when {
        trimmed.contains(marker, ignoreCase = true) ->
            trimmed.substringAfter(marker).substringBefore("?").substringBefore("#").trimEnd('/')
        trimmed.startsWith("meethaq://invite/", ignoreCase = true) ->
            trimmed.substringAfter("meethaq://invite/").substringBefore("?").trimEnd('/')
        else -> trimmed
    }
    return extracted.takeIf { it.length >= 16 && it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
}

@Composable
fun InvitePublicScreen(
    token: String,
    isSignedIn: Boolean,
    currentUserEmail: String,
    currentUserPhone: String,
    onBack: () -> Unit,
    onRequestSignIn: () -> Unit,
    onVerifiedAndReady: (agreementId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val repository = remember(context) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        AgreementRepository(FirebaseFirestore.getInstance(dbId))
    }

    var lookupState by remember(token) { mutableStateOf<PublicInvitationLookup?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    var selectedCountry by remember { mutableStateOf<CountryDialCode>(PhoneNumberHelper.defaultCountry) }
    var localPhoneInput by remember { mutableStateOf("") }

    LaunchedEffect(token) {
        lookupState = null
        errorMsg = null
        infoMsg = null
        lookupState = repository.getPublicInvitation(token)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("invite_public_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة"
                        )
                    }
                    MeethaqShieldLogo(size = 38.dp)
                    Column {
                        Text(
                            text = "بوابة الدعوة الموثّقة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اتفاق موثّق.. حق محفوظ.. بلا شهود",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldMetallic
                        )
                    }
                }
            }

            when (val state = lookupState) {
                null -> {
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
                            CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(26.dp))
                            Text("جارٍ فحص صلاحية رابط الدعوة والتأكد منه…")
                        }
                    }
                }

                PublicInvitationLookup.InvalidToken, PublicInvitationLookup.NotFound -> {
                    InviteErrorStatusCard(
                        icon = Icons.Filled.ErrorOutline,
                        title = "رابط الدعوة غير صالح",
                        message = "تعذّر العثور على دعوة مطابقة لهذا الرمز. تأكد من نسخ الرابط كاملاً من مُرسِل الميثاق.",
                        accent = CrimsonRejected,
                        onBack = onBack
                    )
                }

                is PublicInvitationLookup.Error -> {
                    InviteErrorStatusCard(
                        icon = Icons.Filled.ErrorOutline,
                        title = "تعذّر تحميل الدعوة",
                        message = state.message,
                        accent = CrimsonRejected,
                        onBack = onBack
                    )
                }

                is PublicInvitationLookup.Expired -> {
                    InviteErrorStatusCard(
                        icon = Icons.Filled.LockClock,
                        title = "انتهت صلاحية رابط الدعوة",
                        message = "صلاحية رابط الدعوة (7 أيام) الخاصة بميثاق «${state.invitation.agreementTitle}» قد انتهت. يرجى الطلب من «${state.invitation.inviterName}» إعادة إرسال دعوة جديدة.",
                        accent = EditOrange,
                        onBack = onBack
                    )
                }

                is PublicInvitationLookup.AlreadyUsed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = PendingBlue.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, PendingBlue.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PendingBlue)
                                Text(
                                    text = "تم استخدام رابط الدعوة مسبقاً",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PendingBlue
                                )
                            }
                            Text(
                                text = "هذا الرابط استُخدم سابقاً لفتح الميثاق «${state.invitation.agreementTitle}» ولا يمكن إعادة تفعيله لمرة ثانية.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isSignedIn) {
                                Button(
                                    onClick = { onVerifiedAndReady(state.invitation.agreementId) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("invite_open_used_agreement_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                                ) {
                                    Text("فتح الميثاق مباشرة", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onRequestSignIn,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                                ) {
                                    Text("تسجيل الدخول لعرض مواثيقي", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                is PublicInvitationLookup.Valid -> {
                    val inv: Invitation = state.invitation
                    val phoneLast3 = inv.inviteePhoneLast3.ifBlank {
                        inv.inviteePhone.filter { it.isDigit() }.takeLast(3)
                    }

                    // Invitation Summary Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, GoldMetallic.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = GoldMetallic.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Shield, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "دعوة لمراجعة ميثاق • ${inv.purposeEnum.arabicLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoldMetallic,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (inv.agreementCode.isNotBlank()) {
                                Text(
                                    text = inv.agreementCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = inv.agreementTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            if (inv.agreementSummary.isNotBlank()) {
                                Text(
                                    text = inv.agreementSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider()

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "المُرسل: ${inv.inviterName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (phoneLast3.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Sms, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "رقم الهاتف المخصّص للدعوة ينتهي بـ:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "•••$phoneLast3",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = GoldMetallic,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (inv.inviteeEmail.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Gavel, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "البريد المدعو: ${inv.inviteeEmail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (inv.expiresAt != null) {
                                Text(
                                    text = "تنتهي صلاحية الدعوة في: ${formatArabicDate(inv.expiresAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Explicit Disclaimer
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = PendingBlue.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, PendingBlue.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = PendingBlue)
                            Text(
                                text = "مهم: فتح الدعوة أو التحقق من الهوية لا يُعد موافقةً على الميثاق. ستنتقل بعد التحقق لصفحة التفاصيل لمراجعة كافة البنود ثم اتخاذ قرار الاعتماد أو طلب التعديل.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }

                    if (infoMsg != null) {
                        Surface(
                            color = EmeraldVerified.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = infoMsg!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }

                    if (isSignedIn) {
                        Button(
                            onClick = {
                                isBusy = true
                                errorMsg = null
                                scope.launch {
                                    val claim = repository.claimInvitation(token)
                                    isBusy = false
                                    claim.fold(
                                        onSuccess = { agId -> onVerifiedAndReady(agId) },
                                        onFailure = { err -> errorMsg = err.localizedMessage }
                                    )
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("invite_proceed_authenticated_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تأكيد الهوية وفتح الميثاق للمراجعة", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "تأكيد الهوية لفتح الميثاق",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.Bold
                                )

                                PhoneCountryPickerField(
                                    selectedCountry = selectedCountry,
                                    onCountrySelected = { selectedCountry = it },
                                    localPhone = localPhoneInput,
                                    onLocalPhoneChange = { localPhoneInput = it },
                                    label = "رقم هاتفك المدعو (اختياري للربط)",
                                    enabled = !isBusy,
                                    testTagPrefix = "invite_phone"
                                )

                                Button(
                                    onClick = {
                                        val e164 = PhoneNumberHelper.buildE164(selectedCountry, localPhoneInput)
                                        if (PhoneNumberHelper.isValidE164(e164)) {
                                            AuthRateLimiter.savePendingSignupInfo(context, "", e164)
                                        }
                                        isBusy = true
                                        errorMsg = null
                                        infoMsg = null
                                        AuthManager.onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onAuthSuccess = {
                                                scope.launch {
                                                    val claim = repository.claimInvitation(token)
                                                    isBusy = false
                                                    claim.fold(
                                                        onSuccess = { agId -> onVerifiedAndReady(agId) },
                                                        onFailure = { err -> errorMsg = err.localizedMessage }
                                                    )
                                                }
                                            },
                                            onAuthError = { msg ->
                                                isBusy = false
                                                errorMsg = msg
                                            },
                                            onAuthCancelled = {
                                                isBusy = false
                                                infoMsg = "تم إلغاء نافذة تسجيل الدخول. يمكنك المحاولة مجدداً لفتح الميثاق."
                                            }
                                        )
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("invite_google_verify_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("تأكيد الهوية عبر Google وفتح الميثاق", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InviteErrorStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    accent: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("العودة")
            }
        }
    }
}
