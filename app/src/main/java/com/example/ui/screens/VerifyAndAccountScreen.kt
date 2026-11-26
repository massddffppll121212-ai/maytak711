package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.crypto.AgreementHasher
import com.example.data.AuthManager
import com.example.model.Agreement
import com.example.model.Invitation
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserSession
import com.example.model.VaultDocument
import com.example.ui.components.AgreementStatusBadge
import com.example.ui.components.PhoneCountryPickerField
import com.example.ui.components.PhoneNumberHelper
import com.example.ui.components.formatArabicDate
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue
import com.example.ui.viewmodel.ActionFeedback
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun VerifyScreen(
    agreementsState: UiState<List<Agreement>>,
    vaultState: UiState<List<VaultDocument>>,
    invitationsState: UiState<List<Invitation>>,
    actionFeedback: ActionFeedback,
    onOpenAgreement: (String) -> Unit,
    onOpenInviteToken: ((String) -> Unit)? = null,
    onAddVaultDocument: (title: String, category: String, referenceCode: String, notes: String?) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var codeOrHashInput by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Pair<Agreement, Boolean>?>(null) }
    var searchedNotFound by remember { mutableStateOf(false) }

    var showAddVaultDialog by remember { mutableStateOf(false) }
    var vTitle by remember { mutableStateOf("") }
    var vCategory by remember { mutableStateOf("إيصال مالي") }
    var vRef by remember { mutableStateOf("") }
    var vNotes by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (actionFeedback is ActionFeedback.Success) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = EmeraldVerified.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(actionFeedback.message, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                    }
                }
            } else if (actionFeedback is ActionFeedback.Error) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            actionFeedback.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = GoldMetallic,
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text(
                            text = "التحقق التشفيري من سلامة الميثاق",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أدخل رقم الميثاق (مثل MTH-2026-...) أو بصمة SHA-256 للتحقق من عدم تعرض البنود لأي تعديل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = codeOrHashInput,
                onValueChange = {
                    codeOrHashInput = it
                    searchedNotFound = false
                    verificationResult = null
                },
                label = { Text("رقم الميثاق أو بصمة SHA-256") },
                placeholder = { Text("MTH-2026-XXXXXX أو 64 خانة hex") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_code_input"),
                shape = MaterialTheme.shapes.medium
            )

            Button(
                onClick = {
                    val query = codeOrHashInput.trim().lowercase()
                    val list = (agreementsState as? UiState.Success)?.data.orEmpty()
                    val match = list.firstOrNull {
                        it.agreementCode.equals(query, ignoreCase = true) ||
                            it.contentHash.equals(query, ignoreCase = true) ||
                            it.id.equals(query, ignoreCase = true)
                    }
                    if (match != null) {
                        verificationResult = match to AgreementHasher.verifyAgreementIntegrity(match)
                        searchedNotFound = false
                    } else {
                        verificationResult = null
                        searchedNotFound = true
                    }
                },
                enabled = codeOrHashInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("verify_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    contentColor = NavyDeep
                )
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فحص ومطابقة البصمة الآن", fontWeight = FontWeight.Bold)
            }

            verificationResult?.let { (agreement, isValid) ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isValid) EmeraldVerified.copy(alpha = 0.1f) else CrimsonRejected.copy(alpha = 0.1f),
                    border = BorderStroke(1.5.dp, if (isValid) EmeraldVerified else CrimsonRejected)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isValid) Icons.Filled.Shield else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (isValid) EmeraldVerified else CrimsonRejected
                            )
                            Text(
                                text = if (isValid) {
                                    "تطابق تام — الوثيقة أصلية ولم تتعرض لأي تعديل"
                                } else {
                                    "عدم تطابق في البصمة الرقمية!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isValid) EmeraldVerified else CrimsonRejected,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${agreement.agreementCode} • ${agreement.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        AgreementStatusBadge(status = agreement.statusEnum)
                        Text(
                            text = "البصمة المحسوبة:\n${agreement.contentHash}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { onOpenAgreement(agreement.id) }) {
                            Text("فتح ملف الميثاق الكامل")
                        }
                    }
                }
            }

            if (searchedNotFound) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "لم يُعثر على ميثاق مطابق ضمن المواثيق المرتبطة بحسابك.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Personal Vault Section (users/{uid}/vault)
            Surface(
                shape = MaterialTheme.shapes.large,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.FolderSpecial, contentDescription = null, tint = GoldMetallic)
                            Text("خزنتي الخاصة (Vault)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                vTitle = ""
                                vCategory = "إيصال مالي"
                                vRef = ""
                                vNotes = ""
                                showAddVaultDialog = true
                            },
                            modifier = Modifier.testTag("add_vault_doc_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ وثيقة")
                        }
                    }

                    when (vaultState) {
                        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldMetallic)
                        is UiState.Error -> Text(vaultState.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            if (vaultState.data.isEmpty()) {
                                Text(
                                    text = "احفظ إثباتاتك الخاصة وأرقام مراجع العقود وإيصالاتك هنا ببصمة SHA-256.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                vaultState.data.forEach { doc ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${doc.title} (${doc.category})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text("مرجع: ${doc.referenceCode} • بصمة: ${doc.sha256Hash.take(18)}…", style = MaterialTheme.typography.labelSmall, color = GoldMetallic)
                                        if (!doc.notes.isNullOrBlank()) {
                                            Text(doc.notes, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Invitations Overview
            Surface(
                shape = MaterialTheme.shapes.large,
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
                        Icon(Icons.Filled.MailOutline, contentDescription = null, tint = GoldMetallic)
                        Text("سجل الدعوات (Invitations)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    when (invitationsState) {
                        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldMetallic)
                        is UiState.Error -> Text(invitationsState.message, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            if (invitationsState.data.isEmpty()) {
                                Text(
                                    text = "لا توجد دعوات مسجلة حالياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                invitationsState.data.take(6).forEach { inv ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(inv.agreementTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${inv.purposeEnum.arabicLabel} • ${inv.inviteePhone.ifBlank { inv.inviteeEmail }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (onOpenInviteToken != null) {
                                                TextButton(onClick = { onOpenInviteToken(inv.id) }) {
                                                    Text("بوابة الدعوة")
                                                }
                                            }
                                            TextButton(onClick = { onOpenAgreement(inv.agreementId) }) {
                                                Text("فتح")
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddVaultDialog) {
        AlertDialog(
            onDismissRequest = { showAddVaultDialog = false },
            title = { Text("حفظ وثيقة في الخزنة الخاصة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = vTitle, onValueChange = { vTitle = it.take(160) }, label = { Text("عنوان الوثيقة *") })
                    OutlinedTextField(value = vCategory, onValueChange = { vCategory = it.take(60) }, label = { Text("التصنيف *") })
                    OutlinedTextField(value = vRef, onValueChange = { vRef = it.take(64) }, label = { Text("الرمز المرجعي *") })
                    OutlinedTextField(value = vNotes, onValueChange = { vNotes = it.take(1000) }, label = { Text("ملاحظات (اختياري)") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddVaultDialog = false
                        onAddVaultDocument(vTitle, vCategory, vRef, vNotes)
                    },
                    enabled = vTitle.trim().length >= 2 && vCategory.trim().length >= 2 && vRef.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("حفظ وتشفير") }
            },
            dismissButton = {
                TextButton(onClick = { showAddVaultDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    displayName: String,
    email: String,
    photoUrl: String?,
    currentSessionId: String,
    userProfile: UserProfile?,
    sessionsState: UiState<List<UserSession>>,
    themePreference: ThemePreference,
    actionFeedback: ActionFeedback,
    onThemeSelected: (ThemePreference) -> Unit,
    onUpdateProfile: (fullName: String, phone: String) -> Unit,
    onPhoneVerified: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onRevokeSession: (String) -> Unit,
    onSignOutClick: () -> Unit,
    onSignOutAllDevicesClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الملف الشخصي", "الأمان", "الجلسات النشطة")

    var localAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) localAvatarUri = uri
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var nameInput by remember(userProfile?.fullName, displayName) {
        mutableStateOf(userProfile?.fullName?.ifBlank { displayName } ?: displayName)
    }
    val initialDetected = remember(userProfile?.phone) {
        PhoneNumberHelper.detectCountryAndLocal(userProfile?.phone.orEmpty())
    }
    var editCountry by remember(userProfile?.phone) { mutableStateOf(initialDetected.first) }
    var phoneInput by remember(userProfile?.phone) { mutableStateOf(initialDetected.second) }

    // Phone update dialog state inside Account
    var showVerifyPhoneDialog by remember { mutableStateOf(false) }
    var otpCountry by remember(userProfile?.phone) { mutableStateOf(initialDetected.first) }
    var otpPhoneInput by remember(userProfile?.phone) { mutableStateOf(initialDetected.second) }

    // Change password state (/settings/security)
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    val hasPasswordProvider = remember { AuthManager.hasPasswordProvider(Firebase.auth.currentUser) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GoldMetallic
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("account_tab_$idx")
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (actionFeedback is ActionFeedback.Success) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = EmeraldVerified.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(actionFeedback.message, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                        }
                    }
                } else if (actionFeedback is ActionFeedback.Error) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                actionFeedback.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearFeedback) { Text("إغلاق") }
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // Profile Tab
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.size(62.dp),
                                    shape = CircleShape,
                                    color = GoldMetallic.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, GoldMetallic)
                                ) {
                                    val avatarModel: Any? = localAvatarUri ?: photoUrl
                                    if (avatarModel != null) {
                                        AsyncImage(
                                            model = avatarModel,
                                            contentDescription = "صورة الحساب",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.AddAPhoto, contentDescription = "اختيار صورة", tint = GoldMetallic)
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userProfile?.fullName?.ifBlank { displayName } ?: displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (email.isNotBlank()) {
                                        Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldVerified, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = if (userProfile?.phoneVerified == true) "الهوية والهاتف موثّقان" else "حساب موثّق",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldVerified,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { showEditProfileDialog = true },
                                    modifier = Modifier.testTag("edit_profile_button")
                                ) {
                                    Text("تعديل")
                                }
                            }
                        }

                        // Phone Verification Card
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = GoldMetallic)
                                        Column {
                                            Text("رقم الهاتف وتوثيقه (OTP)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = userProfile?.phone?.takeIf { it.isNotBlank() } ?: "غير مضاف بعد",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (userProfile?.phoneVerified == true) EmeraldVerified.copy(alpha = 0.14f) else EditOrange.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (userProfile?.phoneVerified == true) "موثّق" else "غير موثّق",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (userProfile?.phoneVerified == true) EmeraldVerified else EditOrange,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        showVerifyPhoneDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_verify_phone_button"),
                                    border = BorderStroke(1.dp, GoldMetallic)
                                ) {
                                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (userProfile?.phone?.isNotBlank() == true) "تحديث رقم الهاتف في الملف الشخصي" else "إضافة وربط رقم الهاتف",
                                        color = GoldMetallic,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Theme Preference Card
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "مظهر التطبيق (الوضع الليلي والنهاري)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldMetallic
                                )
                                ThemePreference.entries.forEach { option ->
                                    val icon = when (option) {
                                        ThemePreference.SYSTEM -> Icons.Filled.SettingsBrightness
                                        ThemePreference.LIGHT -> Icons.Filled.LightMode
                                        ThemePreference.DARK -> Icons.Filled.DarkMode
                                    }
                                    Surface(
                                        onClick = { onThemeSelected(option) },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (themePreference == option) GoldMetallic.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.testTag("theme_option_${option.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(imageVector = icon, contentDescription = option.arabicLabel, tint = GoldMetallic)
                                                Text(text = option.arabicLabel, style = MaterialTheme.typography.bodyLarge)
                                            }
                                            RadioButton(
                                                selected = themePreference == option,
                                                onClick = { onThemeSelected(option) },
                                                colors = RadioButtonDefaults.colors(selectedColor = GoldMetallic)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // /settings/security
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Security, contentDescription = null, tint = GoldMetallic)
                                    Text("إعدادات الأمان وحماية الحساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "• حماية الدخول المفعّلة: قفل تلقائي بعد 5 محاولات فاشلة لمدة 10 دقائق.\n• قواعد Firestore: تمنع رؤية أو تعديل بياناتك أو جلساتك من أي مستخدم آخر، وتحمي uid وتاريخ الإنشاء createdAt ضد التعديل.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
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
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldMetallic)
                                    Text("تغيير كلمة المرور", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }

                                if (hasPasswordProvider) {
                                    OutlinedTextField(
                                        value = newPasswordInput,
                                        onValueChange = { newPasswordInput = it.take(64) },
                                        label = { Text("كلمة المرور الجديدة (8 أحرف على الأقل)") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("security_new_password_input")
                                    )
                                    OutlinedTextField(
                                        value = confirmPasswordInput,
                                        onValueChange = { confirmPasswordInput = it.take(64) },
                                        label = { Text("تأكيد كلمة المرور الجديدة") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("security_confirm_password_input")
                                    )
                                    Button(
                                        onClick = { onChangePassword(newPasswordInput) },
                                        enabled = newPasswordInput.length >= 8 && newPasswordInput == confirmPasswordInput,
                                        modifier = Modifier.fillMaxWidth().testTag("security_change_password_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                                    ) {
                                        Text("تحديث كلمة المرور", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = "حسابك الحالي يعتمد على المصادقة الموثّقة عبر حساب Google الآمن، لذا تُدار حماية الدخول مباشرة من حسابك الموثّق وإدارة الجلسات النشطة.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // /settings/sessions
                        Surface(
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
                                    Icon(Icons.Filled.Devices, contentDescription = null, tint = GoldMetallic)
                                    Text("الجلسات والأجهزة المسجلة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }

                                when (sessionsState) {
                                    is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldMetallic)
                                    is UiState.Error -> Text(sessionsState.message, color = MaterialTheme.colorScheme.error)
                                    is UiState.Success -> {
                                        if (sessionsState.data.isEmpty()) {
                                            Text("لا توجد جلسات مسجلة.", style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            sessionsState.data.forEach { session ->
                                                val isThisDevice = session.id == currentSessionId
                                                Surface(
                                                    shape = MaterialTheme.shapes.medium,
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isThisDevice && !session.revoked) GoldMetallic else MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = session.device.ifBlank { "Android Device" },
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = when {
                                                                    session.revoked -> "مُبطلة"
                                                                    isThisDevice -> "هذا الجهاز (نشط)"
                                                                    else -> "نشطة"
                                                                },
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (session.revoked) CrimsonRejected else EmeraldVerified,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Text(
                                                            text = session.userAgent,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = "بصمة الشبكة: ${session.ipHash.take(16)}… • آخر نشاط: ${formatArabicDate(session.lastSeenAt)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (!session.revoked && !isThisDevice) {
                                                            TextButton(onClick = { onRevokeSession(session.id) }) {
                                                                Text("إنهاء هذه الجلسة", color = CrimsonRejected)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = onSignOutClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("sign_out_this_device_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = PendingBlue)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تسجيل خروج من هذا الجهاز", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onSignOutAllDevicesClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("sign_out_all_devices_button"),
                                    border = BorderStroke(1.dp, CrimsonRejected)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonRejected, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تسجيل خروج من جميع الأجهزة", color = CrimsonRejected, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    OutlinedButton(
                        onClick = onSignOutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("sign_out_button"),
                        border = BorderStroke(1.dp, CrimsonRejected.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonRejected, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out), color = CrimsonRejected, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("تحديث الملف الشخصي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(120) },
                        label = { Text("الاسم الكامل *") },
                        singleLine = true
                    )
                    PhoneCountryPickerField(
                        selectedCountry = editCountry,
                        onCountrySelected = { editCountry = it },
                        localPhone = phoneInput,
                        onLocalPhoneChange = { phoneInput = it },
                        label = "رقم الجوال",
                        testTagPrefix = "account_edit_phone"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditProfileDialog = false
                        val normalized = if (phoneInput.isNotBlank()) {
                            PhoneNumberHelper.buildE164(editCountry, phoneInput)
                        } else {
                            ""
                        }
                        onUpdateProfile(nameInput, normalized)
                    },
                    enabled = nameInput.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showVerifyPhoneDialog) {
        val activeOtpE164 = PhoneNumberHelper.buildE164(otpCountry, otpPhoneInput)
        AlertDialog(
            onDismissRequest = { showVerifyPhoneDialog = false },
            title = { Text("ربط وتحديث رقم الهاتف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "أدخل رقم هاتفك بالصيغة الدولية لربطه بملفك الشخصي في ميثاق وتسهيل استلام دعوات المواثيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PhoneCountryPickerField(
                        selectedCountry = otpCountry,
                        onCountrySelected = { otpCountry = it },
                        localPhone = otpPhoneInput,
                        onLocalPhoneChange = { otpPhoneInput = it },
                        label = "رقم الهاتف",
                        testTagPrefix = "account_otp_phone"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVerifyPhoneDialog = false
                        onPhoneVerified(activeOtpE164)
                    },
                    enabled = PhoneNumberHelper.isValidE164(activeOtpE164),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier.testTag("account_save_phone_button")
                ) { Text("حفظ الرقم") }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyPhoneDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
