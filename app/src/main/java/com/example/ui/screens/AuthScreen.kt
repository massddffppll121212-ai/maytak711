package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AuthManager
import com.example.data.AuthRateLimiter
import com.example.model.ThemePreference
import com.example.ui.components.MeethaqShieldLogo
import com.example.ui.components.PhoneCountryPickerField
import com.example.ui.components.PhoneNumberHelper
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary

private enum class AuthRoute {
    LOGIN,
    SIGN_UP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    themePreference: ThemePreference,
    onToggleTheme: (ThemePreference) -> Unit,
    onAuthSuccess: () -> Unit,
    onOpenInviteToken: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val rateLimiter = remember(context) { AuthRateLimiter(context) }

    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteRawInput by remember { mutableStateOf("") }
    var inviteInputError by remember { mutableStateOf<String?>(null) }

    var currentRoute by remember { mutableStateOf(AuthRoute.LOGIN) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Login / Sign-up inputs
    var fullNameInput by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(PhoneNumberHelper.defaultCountry) }
    var localPhoneInput by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(true) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var rateLimitStatus by remember {
        rateLimiter.clearLegacyFailedAttemptsOnce()
        mutableStateOf(rateLimiter.checkAllowed())
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        pickedPhotoUri = uri
    }

    if (currentRoute != AuthRoute.LOGIN) {
        BackHandler {
            errorMessage = null
            infoMessage = null
            currentRoute = AuthRoute.LOGIN
        }
    }

    LaunchedEffect(Unit) {
        rateLimiter.clearLegacyFailedAttemptsOnce()
        rateLimitStatus = rateLimiter.checkAllowed()
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = { onAuthSuccess() },
            onUnauthenticated = { }
        )
    }

    fun refreshRateLimit() {
        rateLimitStatus = rateLimiter.checkAllowed()
    }

    fun triggerGoogleSignIn() {
        errorMessage = null
        infoMessage = null
        isLoading = true
        AuthManager.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {
                isLoading = false
                refreshRateLimit()
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                refreshRateLimit()
                errorMessage = msg
            },
            onAuthCancelled = {
                isLoading = false
                infoMessage = "تم إلغاء نافذة اختيار حساب Google. يمكنك المحاولة مجدداً."
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MeethaqShieldLogo(size = 42.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.app_full_brand),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldMetallic
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val next = if (themePreference == ThemePreference.DARK) {
                            ThemePreference.LIGHT
                        } else {
                            ThemePreference.DARK
                        }
                        onToggleTheme(next)
                    },
                    modifier = Modifier.testTag("auth_theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (themePreference == ThemePreference.DARK) {
                            Icons.Filled.LightMode
                        } else {
                            Icons.Filled.DarkMode
                        },
                        contentDescription = "تبديل الوضع الليلي والنهاري",
                        tint = GoldMetallic
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Compact Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .border(1.dp, GoldMetallic.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_meethaq_hero),
                        contentDescription = "منصة ميثاق لتوثيق الاتفاقات",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NavyPrimary.copy(alpha = 0.84f),
                                        NavyDeep.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = GoldMetallic.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, GoldBright.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Gavel, contentDescription = null, tint = GoldBright, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "توثيق رقمي محمي • دخول سريع وآمن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldBright,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.auth_welcome_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.auth_welcome_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                }

                // Route Selector (Login / Sign-up)
                if (currentRoute == AuthRoute.LOGIN || currentRoute == AuthRoute.SIGN_UP) {
                    SecondaryTabRow(
                        selectedTabIndex = if (currentRoute == AuthRoute.LOGIN) 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = GoldMetallic,
                        modifier = Modifier.clip(MaterialTheme.shapes.medium)
                    ) {
                        Tab(
                            selected = currentRoute == AuthRoute.LOGIN,
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                currentRoute = AuthRoute.LOGIN
                            },
                            text = { Text("تسجيل الدخول", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("auth_tab_login")
                        )
                        Tab(
                            selected = currentRoute == AuthRoute.SIGN_UP,
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                currentRoute = AuthRoute.SIGN_UP
                            },
                            text = { Text("إنشاء حساب", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("auth_tab_signup")
                        )
                    }
                }

                // Rate limit warning banner (5 attempts / 10 min)
                if (!rateLimitStatus.allowed) {
                    val mins = ((rateLimitStatus.waitSeconds + 59L) / 60L).coerceAtLeast(1L)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = EditOrange.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EditOrange)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = EditOrange)
                            Text(
                                text = "حماية الحساب: تم بلوغ الحد الأقصى (5 محاولات خلال 10 دقائق). يرجى الانتظار حوالي $mins دقيقة.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                }

                if (infoMessage != null) {
                    Surface(
                        color = EmeraldVerified.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = infoMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                when (currentRoute) {
                    AuthRoute.LOGIN -> {
                        val candidatePhone = PhoneNumberHelper.buildE164(selectedCountry, localPhoneInput)
                        val hasValidOptionalPhone = PhoneNumberHelper.isCompleteForCountry(selectedCountry, localPhoneInput)
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "الدخول السريع الموثّق (بنقرة واحدة)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        if (hasValidOptionalPhone) {
                                            AuthRateLimiter.savePendingSignupInfo(context, "", candidatePhone)
                                        }
                                        triggerGoogleSignIn()
                                    },
                                    enabled = !isLoading && rateLimitStatus.allowed,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("google_sign_in_button"),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldMetallic,
                                        contentColor = NavyDeep
                                    )
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDeep, strokeWidth = 2.5.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.auth_signing_in), fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (hasValidOptionalPhone) {
                                                "تسجيل الدخول وربط الرقم عبر Google"
                                            } else {
                                                stringResource(R.string.auth_google_sign_in)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "دخول فوري بحساب Google — بدون كلمة مرور أو رمز تحقق SMS.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider()

                                PhoneCountryPickerField(
                                    selectedCountry = selectedCountry,
                                    onCountrySelected = {
                                        selectedCountry = it
                                        errorMessage = null
                                    },
                                    localPhone = localPhoneInput,
                                    onLocalPhoneChange = {
                                        localPhoneInput = it
                                        errorMessage = null
                                    },
                                    label = "رقم الهاتف لربطه بحسابك (اختياري)",
                                    enabled = !isLoading,
                                    testTagPrefix = "login_phone"
                                )

                                if (rateLimitStatus.remainingAttempts < AuthRateLimiter.MAX_ATTEMPTS) {
                                    Text(
                                        text = "المحاولات المتبقية: ${rateLimitStatus.remainingAttempts}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EditOrange
                                    )
                                }

                                if (onOpenInviteToken != null) {
                                    HorizontalDivider()
                                    OutlinedButton(
                                        onClick = {
                                            inviteRawInput = ""
                                            inviteInputError = null
                                            showInviteDialog = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("auth_open_invite_button"),
                                        border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Filled.Link, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("لديّ رابط / رمز دعوة لميثاق", color = GoldMetallic, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    AuthRoute.SIGN_UP -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "إنشاء حساب جديد بسهولة",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldMetallic,
                                    fontWeight = FontWeight.Bold
                                )

                                // Optional Avatar Picker (Zero-Permission Photo Picker)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .size(54.dp)
                                            .testTag("signup_pick_photo_button"),
                                        shape = CircleShape,
                                        color = GoldMetallic.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, GoldMetallic)
                                    ) {
                                        if (pickedPhotoUri != null) {
                                            AsyncImage(
                                                model = pickedPhotoUri,
                                                contentDescription = "الصورة الشخصية المختارة",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.AddAPhoto, contentDescription = "اختيار صورة شخصية", tint = GoldMetallic)
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("صورة شخصية (اختيارية)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "اختيار آمن عبر منتقي الصور دون طلب صلاحيات إضافية.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = fullNameInput,
                                    onValueChange = { fullNameInput = it.take(120) },
                                    label = { Text("الاسم الكامل") },
                                    placeholder = { Text("يُؤخذ تلقائياً من Google أو أدخل اسمك") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("signup_full_name_input")
                                )

                                PhoneCountryPickerField(
                                    selectedCountry = selectedCountry,
                                    onCountrySelected = {
                                        selectedCountry = it
                                        errorMessage = null
                                    },
                                    localPhone = localPhoneInput,
                                    onLocalPhoneChange = {
                                        localPhoneInput = it
                                        errorMessage = null
                                    },
                                    label = "رقم الهاتف (اختياري للربط بالحساب)",
                                    enabled = !isLoading,
                                    testTagPrefix = "signup_phone"
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            acceptedTerms = !acceptedTerms
                                            errorMessage = null
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = acceptedTerms,
                                        onCheckedChange = {
                                            acceptedTerms = it
                                            errorMessage = null
                                        },
                                        modifier = Modifier.testTag("signup_terms_checkbox"),
                                        colors = CheckboxDefaults.colors(checkedColor = GoldMetallic)
                                    )
                                    Text(
                                        text = "أوافق على شروط استخدام منصة ميثاق وسياسة الخصوصية والتوثيق الرقمي.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = {
                                        val cleanName = fullNameInput.trim()
                                        val candidatePhone = PhoneNumberHelper.buildE164(selectedCountry, localPhoneInput)
                                        val validPhone = if (PhoneNumberHelper.isCompleteForCountry(selectedCountry, localPhoneInput)) {
                                            candidatePhone
                                        } else {
                                            ""
                                        }
                                        AuthRateLimiter.savePendingSignupInfo(context, cleanName, validPhone)
                                        triggerGoogleSignIn()
                                    },
                                    enabled = !isLoading && acceptedTerms && rateLimitStatus.allowed,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("signup_google_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NavyDeep, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("جارٍ التسجيل…", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("إنشاء الحساب الآن عبر Google", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showInviteDialog && onOpenInviteToken != null) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("فتح دعوة ميثاق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الصق رابط الدعوة (مثل https://meethaq.app/invite/...) أو أدخل رمز الدعوة المرسل إليك لمعاينة الميثاق وتأكيد هويتك عبر حساب Google.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = inviteRawInput,
                        onValueChange = {
                            inviteRawInput = it.take(300)
                            inviteInputError = null
                        },
                        label = { Text("رابط أو رمز الدعوة *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_invite_token_input")
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
                        val token = extractInviteToken(inviteRawInput)
                        if (token == null) {
                            inviteInputError = "الرمز أو الرابط غير صالح. تأكد من لصقه كاملاً."
                        } else {
                            showInviteDialog = false
                            onOpenInviteToken(token)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep),
                    modifier = Modifier.testTag("auth_invite_submit_button")
                ) {
                    Text("معاينة الدعوة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
