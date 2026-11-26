package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.crypto.BiometricSignatureResult
import com.example.crypto.BiometricSigner
import com.example.data.OtpChallengeIssued
import com.example.model.Agreement
import com.example.model.AgreementAmendment
import com.example.model.AgreementSignature
import com.example.model.AgreementStatus
import com.example.model.AmendmentStatus
import com.example.model.Party
import com.example.model.UiState
import com.example.model.Witness
import com.example.ui.components.formatArabicDate
import com.example.ui.theme.CrimsonRejected
import com.example.ui.theme.EditOrange
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PendingBlue

@Composable
fun DualVerificationSection(
    agreement: Agreement,
    signaturesState: UiState<List<AgreementSignature>>,
    currentUserId: String,
    currentUserEmail: String,
    currentUserPhone: String,
    activeOtpChallenge: OtpChallengeIssued?,
    otpVerifiedLocallyForAgreement: Boolean,
    pendingBiometricSignature: BiometricSignatureResult?,
    isBusy: Boolean,
    onRequestOtp: (targetIdentifier: String) -> Unit,
    onVerifyOtp: (challengeId: String, code: String) -> Unit,
    onBiometricSigned: (BiometricSignatureResult) -> Unit,
    onSignAgreement: (
        phoneVerified: Boolean,
        biometricVerified: Boolean,
        credentialId: String?,
        verificationMethod: String,
        signatureHex: String?
    ) -> Unit,
    onApproveAndLockAgreement: (note: String?) -> Unit,
    onLocalFeedbackError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var otpInput by remember { mutableStateOf("") }
    var showPasskeyFallbackDialog by remember { mutableStateOf(false) }
    var passkeyFallbackReason by remember { mutableStateOf("") }
    var passkeyConfirmPhrase by remember { mutableStateOf("") }
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var finalizeNoteInput by remember { mutableStateOf("") }

    val matchingParty: Party? = remember(agreement.parties, currentUserId, currentUserEmail, currentUserPhone) {
        agreement.parties.firstOrNull { p ->
            p.uid == currentUserId ||
                (currentUserEmail.isNotBlank() && p.email.equals(currentUserEmail, ignoreCase = true)) ||
                (currentUserPhone.isNotBlank() && p.phone.trim() == currentUserPhone.trim())
        }
    }
    val matchingWitness: Witness? = remember(agreement.witnesses, currentUserId, currentUserEmail, currentUserPhone) {
        agreement.witnesses.firstOrNull { w ->
            w.uid == currentUserId ||
                (currentUserEmail.isNotBlank() && w.email.equals(currentUserEmail, ignoreCase = true)) ||
                (currentUserPhone.isNotBlank() && w.phone.trim() == currentUserPhone.trim())
        }
    }

    val isCallerListedSigner = matchingParty != null || matchingWitness != null
    val callerAlreadySigned = (matchingParty?.approved == true) || (matchingWitness?.approvedAtMillis != null)
    val callerPhoneVerified = (matchingParty?.verifiedPhone == true) ||
        (matchingWitness?.verifiedPhone == true) ||
        otpVerifiedLocallyForAgreement
    val callerBiometricReady = pendingBiometricSignature != null ||
        (matchingParty?.biometricVerified == true) ||
        (matchingWitness?.biometricVerified == true)

    val signedPartiesCount = agreement.parties.count { it.approved }
    val totalPartiesCount = agreement.parties.size.coerceAtLeast(1)
    val signedWitnessesCount = agreement.witnesses.count { it.approvedAtMillis != null }
    val totalWitnessesCount = agreement.witnesses.size

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, GoldMetallic.copy(alpha = 0.55f))
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
                    Icon(Icons.Filled.PhonelinkLock, contentDescription = null, tint = GoldMetallic)
                    Text(
                        text = "التوثيق المزدوج والاعتماد النهائي",
                        style = MaterialTheme.typography.titleSmall,
                        color = GoldMetallic,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (agreement.allPartiesSigned && agreement.allWitnessesSigned) {
                        EmeraldVerified.copy(alpha = 0.14f)
                    } else {
                        PendingBlue.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = "$signedPartiesCount/$totalPartiesCount أطراف",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (agreement.allPartiesSigned) EmeraldVerified else PendingBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar for multi-party signature completion
            LinearProgressIndicator(
                progress = { signedPartiesCount.toFloat() / totalPartiesCount.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (agreement.allPartiesSigned) EmeraldVerified else GoldMetallic,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (totalWitnessesCount > 0) {
                Text(
                    text = "تقدم موافقة الشهود: $signedWitnessesCount / $totalWitnessesCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mandatory legal notice required by specification
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = EditOrange.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, EditOrange.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = EditOrange, modifier = Modifier.size(18.dp))
                    Text(
                        text = "تنبيه قانوني: التحقق البيومتري وحده ليس إثباتاً قانونياً قاطعاً للهوية.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (agreement.statusEnum == AgreementStatus.PENDING && isCallerListedSigner) {
                if (!callerAlreadySigned) {
                    // PHASE 1: OTP Verification
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (callerPhoneVerified) EmeraldVerified.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المرحلة 1 — التحقق برمز OTP الموقوت",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (callerPhoneVerified) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "تم التحقق",
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val targetHint = matchingParty?.phone?.ifBlank { matchingParty.email }
                                ?: matchingWitness?.phone?.ifBlank { matchingWitness.email }
                                ?: currentUserPhone.ifBlank { currentUserEmail }

                            OutlinedButton(
                                onClick = { onRequestOtp(targetHint) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("dual_verify_send_otp_button")
                            ) {
                                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (activeOtpChallenge == null) "إرسال رمز تحقق OTP (5 دقائق / 5 محاولات)" else "إعادة إصدار رمز OTP جديد"
                                )
                            }

                            if (activeOtpChallenge != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PendingBlue.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, PendingBlue.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "رمز التحقق الموثّق لـ (${activeOtpChallenge.targetIdentifier}): ${activeOtpChallenge.verificationCode}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = PendingBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "يُخزَّن الرمز مشفراً بـ SHA-256 في approvals ويُمنع إعادة استخدامه بعد التحقق.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = otpInput,
                                        onValueChange = { raw ->
                                            if (raw.all { it.isDigit() }) otpInput = raw.take(6)
                                        },
                                        label = { Text("أدخل رمز OTP") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("dual_verify_otp_input")
                                    )
                                    Button(
                                        onClick = {
                                            onVerifyOtp(activeOtpChallenge.challengeId, otpInput)
                                        },
                                        enabled = !isBusy && otpInput.length >= 4,
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("dual_verify_otp_confirm_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldMetallic,
                                            contentColor = NavyDeep
                                        )
                                    ) {
                                        Text("تحقق", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // PHASE 2: Biometric / Device Passkey Keystore Sign
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (callerBiometricReady) EmeraldVerified.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المرحلة 2 — التوقيع البيومتري / Passkey",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pendingBiometricSignature != null) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "جاهز",
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "تبقى بيانات البصمة داخل جهازك فقط ولا تُرسل أو تُخزَّن؛ يُحفظ فقط معرّف المفتاح credentialId.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = {
                                    if (activity == null) {
                                        val (credId, sigHex) = BiometricSigner.signChallengeWithKeystore(
                                            uid = currentUserId,
                                            challengePayload = "${agreement.agreementCode}|${agreement.contentHash}"
                                        )
                                        onBiometricSigned(
                                            BiometricSignatureResult(
                                                credentialId = credId,
                                                signatureHex = sigHex,
                                                biometricVerified = false,
                                                verificationMethod = "PASSKEY_KEYSTORE_DEVICE"
                                            )
                                        )
                                    } else {
                                        BiometricSigner.authenticateAndSign(
                                            activity = activity,
                                            uid = currentUserId,
                                            agreementCode = agreement.agreementCode,
                                            versionHash = agreement.contentHash,
                                            onSuccess = { result -> onBiometricSigned(result) },
                                            onRequireDevicePasskeyFallback = { reason ->
                                                passkeyFallbackReason = reason
                                                passkeyConfirmPhrase = ""
                                                showPasskeyFallbackDialog = true
                                            },
                                            onError = { msg -> onLocalFeedbackError(msg) }
                                        )
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("dual_verify_biometric_button"),
                                border = BorderStroke(1.dp, GoldMetallic)
                            ) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = GoldMetallic)
                                Spacer(Modifier.width(6.dp))
                                Text("توقيع بيومتري", color = GoldMetallic, fontWeight = FontWeight.Bold)
                            }

                            if (pendingBiometricSignature != null) {
                                Text(
                                    text = "تم تجهيز المفتاح: ${pendingBiometricSignature.credentialId} (${pendingBiometricSignature.verificationMethod})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldVerified,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // PHASE 3: Confirm Approval (Server transactional sign)
                    Button(
                        onClick = {
                            val method = when {
                                callerPhoneVerified && pendingBiometricSignature?.biometricVerified == true ->
                                    "DUAL_OTP_AND_BIOMETRIC"
                                callerPhoneVerified && pendingBiometricSignature != null ->
                                    "DUAL_OTP_AND_PASSKEY"
                                pendingBiometricSignature != null ->
                                    pendingBiometricSignature.verificationMethod
                                callerPhoneVerified -> "OTP_VERIFIED_ONLY"
                                else -> "UNVERIFIED"
                            }
                            onSignAgreement(
                                callerPhoneVerified,
                                pendingBiometricSignature?.biometricVerified == true,
                                pendingBiometricSignature?.credentialId,
                                method,
                                pendingBiometricSignature?.signatureHex
                            )
                        },
                        enabled = !isBusy && (callerPhoneVerified || pendingBiometricSignature != null),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("dual_verify_confirm_sign_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldMetallic,
                            contentColor = NavyDeep
                        )
                    ) {
                        Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تأكيد الموافقة وتثبيت توقيعي", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = EmeraldVerified.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldVerified)
                            Text(
                                text = "تم توثيق توقيعك على هذه النسخة (${matchingParty?.verificationMethod ?: matchingWitness?.verificationMethod ?: "موثّق"}).",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Final Lock Button (Enforces unanimous parties + witnesses on backend transaction & rules!)
                HorizontalDivider()
                Text(
                    text = if (agreement.allPartiesSigned && agreement.allWitnessesSigned) {
                        "وقّع جميع الأطراف والشهود. يمكنك الآن تنفيذ القفل والاعتماد النهائي:"
                    } else {
                        "الاعتماد النهائي متاح فقط بعد توقيع جميع الأطراف والشهود (لا يمكن لطرف واحد اعتماد الاتفاق منفرداً)."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        finalizeNoteInput = ""
                        showFinalizeDialog = true
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dual_verify_finalize_lock_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldVerified,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اعتماد وقفل نهائي للاتفاق", fontWeight = FontWeight.Bold)
                }
            }

            // Immutable signatures receipts list
            when (signaturesState) {
                is UiState.Loading -> CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(20.dp))
                is UiState.Error -> Text(signaturesState.message, style = MaterialTheme.typography.labelSmall, color = CrimsonRejected)
                is UiState.Success -> {
                    if (signaturesState.data.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "سجل التواقيع الموثّقة (${signaturesState.data.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldMetallic,
                            fontWeight = FontWeight.Bold
                        )
                        signaturesState.data.forEach { sig ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${sig.signerName} (${sig.role})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(sig.verificationMethod, style = MaterialTheme.typography.labelSmall, color = EmeraldVerified)
                                    }
                                    Text(
                                        text = "OTP: ${if (sig.phoneVerified) "✓" else "—"} • بيومتري: ${if (sig.biometricVerified) "✓" else "Passkey"} • ${sig.versionId}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (!sig.credentialId.isNullOrBlank()) {
                                        Text(
                                            text = "credentialId: ${sig.credentialId} • ${formatArabicDate(sig.signedAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasskeyFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showPasskeyFallbackDialog = false },
            title = { Text("توقيع عبر Passkey الجهاز (Android Keystore)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = passkeyFallbackReason,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "سيتم إنشاء مفتاح توقيع EC P-256 محمي داخل AndroidKeyStore على جهازك وحفظ معرّفه العام credentialId فقط.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = passkeyConfirmPhrase,
                        onValueChange = { passkeyConfirmPhrase = it.take(30) },
                        label = { Text("اكتب «موافق» لتفعيل Passkey") },
                        singleLine = true,
                        modifier = Modifier.testTag("passkey_fallback_confirm_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasskeyFallbackDialog = false
                        val (credId, sigHex) = BiometricSigner.signChallengeWithKeystore(
                            uid = currentUserId,
                            challengePayload = "${agreement.agreementCode}|${agreement.contentHash}|${System.currentTimeMillis()}"
                        )
                        onBiometricSigned(
                            BiometricSignatureResult(
                                credentialId = credId,
                                signatureHex = sigHex,
                                biometricVerified = false,
                                verificationMethod = "DEVICE_PASSKEY_KEYSTORE"
                            )
                        )
                    },
                    enabled = passkeyConfirmPhrase.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("توقيع بـ Passkey") }
            },
            dismissButton = {
                TextButton(onClick = { showPasskeyFallbackDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showFinalizeDialog) {
        AlertDialog(
            onDismissRequest = { showFinalizeDialog = false },
            title = { Text("الاعتماد النهائي وقفل الميثاق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "سيتم التحقق عبر معاملة Firestore الذرية (Transaction) من توقيع جميع الأطراف والشهود، ثم حساب approvedHash وتثبيت النسخة نهائياً."
                    )
                    OutlinedTextField(
                        value = finalizeNoteInput,
                        onValueChange = { finalizeNoteInput = it.take(500) },
                        label = { Text("ملاحظة الاعتماد (اختياري)") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinalizeDialog = false
                        onApproveAndLockAgreement(finalizeNoteInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified)
                ) { Text("اعتماد وقفل نهائي") }
            },
            dismissButton = {
                TextButton(onClick = { showFinalizeDialog = false }) { Text("تراجع") }
            }
        )
    }
}

@Composable
fun AmendmentsSection(
    agreement: Agreement,
    amendmentsState: UiState<List<AgreementAmendment>>,
    currentUserId: String,
    currentUserEmail: String,
    currentUserPhone: String,
    isParticipant: Boolean,
    isBusy: Boolean,
    onRequestAmendment: (
        reason: String,
        proposedTitle: String,
        proposedDescription: String,
        proposedAmount: Double,
        proposedDueDate: String,
        proposedRights: List<String>,
        proposedObligations: List<String>,
        proposedSpecialTerms: List<String>
    ) -> Unit,
    onRespondToAmendment: (AgreementAmendment, approve: Boolean, note: String?) -> Unit
) {
    var showRequestDialog by remember { mutableStateOf(false) }
    var reasonInput by remember { mutableStateOf("") }
    var titleInput by remember(agreement.id, agreement.currentVersionId) { mutableStateOf(agreement.title) }
    var descInput by remember(agreement.id, agreement.currentVersionId) { mutableStateOf(agreement.description) }
    var amountInput by remember(agreement.id, agreement.currentVersionId) { mutableStateOf(agreement.amount.toString()) }
    var dueDateInput by remember(agreement.id, agreement.currentVersionId) { mutableStateOf(agreement.dueDate) }

    val amendments = (amendmentsState as? UiState.Success)?.data.orEmpty()
    if (agreement.statusEnum != AgreementStatus.APPROVED && amendments.isEmpty()) return

    val callerPartyIdx = agreement.parties.indexOfFirst { p ->
        p.uid == currentUserId ||
            (currentUserEmail.isNotBlank() && p.email.equals(currentUserEmail, ignoreCase = true)) ||
            (currentUserPhone.isNotBlank() && p.phone.trim() == currentUserPhone.trim())
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, PendingBlue.copy(alpha = 0.45f))
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
                    Icon(Icons.Filled.Difference, contentDescription = null, tint = PendingBlue)
                    Text(
                        text = "التعديلات والإصدارات الجديدة",
                        style = MaterialTheme.typography.titleSmall,
                        color = PendingBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "لا يمكن تعديل الميثاق المعتمد مباشرةً. أي تعديل يتطلب اقتراحاً رسمياً وموافقة جميع الأطراف بالإجماع لإصدار نسخة جديدة مقفلة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isParticipant && agreement.statusEnum == AgreementStatus.APPROVED) {
                OutlinedButton(
                    onClick = {
                        reasonInput = ""
                        titleInput = agreement.title
                        descInput = agreement.description
                        amountInput = agreement.amount.toString()
                        dueDateInput = agreement.dueDate
                        showRequestDialog = true
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("request_amendment_button"),
                    border = BorderStroke(1.dp, GoldMetallic)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = GoldMetallic)
                    Spacer(Modifier.width(6.dp))
                    Text("طلب تعديل ميثاق معتمد (نسخة جديدة)", color = GoldMetallic, fontWeight = FontWeight.Bold)
                }
            }

            amendments.forEach { amend ->
                val isPending = amend.statusEnum == AmendmentStatus.PENDING
                val callerKey = if (callerPartyIdx >= 0) {
                    amend.requiredPartyKeys.getOrNull(callerPartyIdx)
                        ?: "p$callerPartyIdx:${agreement.parties[callerPartyIdx].canonicalKey()}"
                } else {
                    ""
                }
                val callerAlreadyVoted = callerKey.isNotBlank() && callerKey in amend.approvedPartyKeys

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        when (amend.statusEnum) {
                            AmendmentStatus.PENDING -> EditOrange.copy(alpha = 0.6f)
                            AmendmentStatus.APPROVED_APPLIED -> EmeraldVerified.copy(alpha = 0.6f)
                            AmendmentStatus.REJECTED -> CrimsonRejected.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تعديل على ${amend.baseVersionId} • ${amend.proposedByName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = amend.statusEnum.arabicLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (amend.statusEnum) {
                                    AmendmentStatus.PENDING -> EditOrange
                                    AmendmentStatus.APPROVED_APPLIED -> EmeraldVerified
                                    AmendmentStatus.REJECTED -> CrimsonRejected
                                }
                            )
                        }
                        Text(text = "السبب: ${amend.reason}", style = MaterialTheme.typography.bodySmall)

                        // Structured Diff display
                        amend.diffItems.forEach { diff ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(diff.fieldLabel, style = MaterialTheme.typography.labelMedium, color = GoldMetallic, fontWeight = FontWeight.Bold)
                                    Text("السابق: ${diff.oldValue}", style = MaterialTheme.typography.labelSmall, color = CrimsonRejected)
                                    Text("المقترح: ${diff.newValue}", style = MaterialTheme.typography.labelSmall, color = EmeraldVerified)
                                }
                            }
                        }

                        Text(
                            text = "الموافقات: ${amend.approvedPartyKeys.size} / ${amend.requiredPartyKeys.size.coerceAtLeast(agreement.parties.size)}" +
                                (amend.appliedVersionId?.let { " • أُنشئ الإصدار $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isPending && callerPartyIdx >= 0 && !callerAlreadyVoted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRespondToAmendment(amend, true, null) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("amendment_approve_button_${amend.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified)
                                ) {
                                    Text("موافقة على التعديل", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onRespondToAmendment(amend, false, null) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("amendment_reject_button_${amend.id}"),
                                    border = BorderStroke(1.dp, CrimsonRejected)
                                ) {
                                    Text("رفض", color = CrimsonRejected, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("اقتراح تعديل رسمي (نسخة جديدة)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it.take(500) },
                        label = { Text("سبب التعديل *") },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it.take(160) },
                        label = { Text("العنوان المقترح") }
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it.take(5000) },
                        label = { Text("الوصف المقترح") },
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { raw ->
                            if (raw.all { it.isDigit() || it == '.' }) amountInput = raw.take(14)
                        },
                        label = { Text("المبلغ المقترح") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = dueDateInput,
                        onValueChange = { dueDateInput = it.take(40) },
                        label = { Text("تاريخ الاستحقاق المقترح") }
                    )
                }
            },
            confirmButton = {
                val parsedAmount = amountInput.toDoubleOrNull() ?: agreement.amount
                Button(
                    onClick = {
                        showRequestDialog = false
                        onRequestAmendment(
                            reasonInput,
                            titleInput,
                            descInput,
                            parsedAmount,
                            dueDateInput,
                            agreement.rights,
                            agreement.obligations,
                            agreement.specialTerms
                        )
                    },
                    enabled = reasonInput.trim().length >= 3 && titleInput.trim().length >= 3 && descInput.trim().length >= 10,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = NavyDeep)
                ) { Text("رفع التعديل للأطراف") }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
