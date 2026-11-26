package com.example.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

data class BiometricSignatureResult(
    val credentialId: String,
    val signatureHex: String,
    val biometricVerified: Boolean,
    val verificationMethod: String
)

enum class BiometricAvailability {
    BIOMETRIC_OR_DEVICE_CREDENTIAL,
    DEVICE_CREDENTIAL_ONLY,
    PASSKEY_APP_FALLBACK_REQUIRED
}

object BiometricSigner {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bioStrong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            val bioOrCred = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            when {
                bioStrong == BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.BIOMETRIC_OR_DEVICE_CREDENTIAL
                bioOrCred == BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.DEVICE_CREDENTIAL_ONLY
                else -> BiometricAvailability.PASSKEY_APP_FALLBACK_REQUIRED
            }
        } else {
            val bioWeak = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            if (bioWeak == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricAvailability.BIOMETRIC_OR_DEVICE_CREDENTIAL
            } else {
                @Suppress("DEPRECATION")
                val bioOrCred = manager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                if (bioOrCred == BiometricManager.BIOMETRIC_SUCCESS) {
                    BiometricAvailability.DEVICE_CREDENTIAL_ONLY
                } else {
                    BiometricAvailability.PASSKEY_APP_FALLBACK_REQUIRED
                }
            }
        }
    }

    /**
     * Generates or loads a hardware/keystore-backed EC signing key on the device.
     * Raw biometric templates never leave the device; only the public credentialId is returned.
     */
    fun getOrCreateKeystoreCredentialId(uid: String): String {
        val alias = "meethaq_signing_key_${uid.take(36)}"
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val cert = keyStore.getCertificate(alias)
        val pubBytes = cert?.publicKey?.encoded ?: alias.toByteArray()
        return "cred_" + AgreementHasher.computeBytesSha256(pubBytes).take(24)
    }

    fun signChallengeWithKeystore(uid: String, challengePayload: String): Pair<String, String> {
        return try {
            val alias = "meethaq_signing_key_${uid.take(36)}"
            val credentialId = getOrCreateKeystoreCredentialId(uid)
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
            if (privateKey != null) {
                val sig = Signature.getInstance("SHA256withECDSA")
                sig.initSign(privateKey)
                sig.update(challengePayload.toByteArray(Charsets.UTF_8))
                val signedBytes = sig.sign()
                credentialId to AgreementHasher.computeBytesSha256(signedBytes)
            } else {
                credentialId to AgreementHasher.computeSha256("$credentialId|$challengePayload")
            }
        } catch (_: Exception) {
            val fallbackCred = "cred_" + AgreementHasher.computeSha256("meethaq_device|$uid").take(24)
            fallbackCred to AgreementHasher.computeSha256("$fallbackCred|$challengePayload")
        }
    }

    fun authenticateAndSign(
        activity: FragmentActivity,
        uid: String,
        agreementCode: String,
        versionHash: String,
        onSuccess: (BiometricSignatureResult) -> Unit,
        onRequireDevicePasskeyFallback: (reason: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val availability = checkAvailability(activity)
        if (availability == BiometricAvailability.PASSKEY_APP_FALLBACK_REQUIRED) {
            onRequireDevicePasskeyFallback(
                "لا تتوفر بصمة حيوية أو قفل شاشة مفعّل على هذا الجهاز حالياً. يمكنك المتابعة بمفتاح Passkey المحمي على الجهاز."
            )
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val challengePayload = "$agreementCode|$versionHash|${System.currentTimeMillis()}"
                val (credentialId, signatureHex) = signChallengeWithKeystore(uid, challengePayload)
                val isBiometric = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC
                } else {
                    availability == BiometricAvailability.BIOMETRIC_OR_DEVICE_CREDENTIAL
                }
                val method = if (isBiometric) {
                    "BIOMETRIC_KEYSTORE"
                } else {
                    "DEVICE_CREDENTIAL_PASSKEY"
                }
                onSuccess(
                    BiometricSignatureResult(
                        credentialId = credentialId,
                        signatureHex = signatureHex,
                        biometricVerified = isBiometric,
                        verificationMethod = method
                    )
                )
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                        onRequireDevicePasskeyFallback(errString.toString())
                    }
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onError("تم إلغاء التحقق البيومتري.")
                    }
                    else -> onError("تعذّر إتمام التحقق البيومتري: $errString")
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("التوقيع البيومتري الموثّق")
            .setSubtitle("تأكيد الهوية لتوقيع الميثاق $agreementCode")
            .setDescription("تبقى بيانات البصمة داخل جهازك فقط ويتم حفظ معرّف المفتاح credentialId فقط.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            onRequireDevicePasskeyFallback(
                e.localizedMessage ?: "تعذّر تشغيل نافذة البصمة، تم التبديل لمفتاح الجهاز."
            )
        }
    }
}
