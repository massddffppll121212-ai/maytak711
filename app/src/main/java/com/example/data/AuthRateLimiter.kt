package com.example.data

import android.content.Context
import android.os.Build
import com.example.crypto.AgreementHasher
import org.json.JSONArray
import java.net.NetworkInterface
import java.util.UUID

data class RateLimitCheckResult(
    val allowed: Boolean,
    val remainingAttempts: Int,
    val waitSeconds: Long
)

class AuthRateLimiter(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun checkAllowed(nowMillis: Long = System.currentTimeMillis()): RateLimitCheckResult {
        val recent = pruneOldAttempts(loadAttemptTimestamps(), nowMillis)
        saveAttemptTimestamps(recent)

        return if (recent.size >= MAX_ATTEMPTS) {
            val oldest = recent.minOrNull() ?: nowMillis
            val unlockAt = oldest + WINDOW_MILLIS
            val waitSeconds = ((unlockAt - nowMillis) / 1000L).coerceAtLeast(1L)
            RateLimitCheckResult(
                allowed = false,
                remainingAttempts = 0,
                waitSeconds = waitSeconds
            )
        } else {
            RateLimitCheckResult(
                allowed = true,
                remainingAttempts = MAX_ATTEMPTS - recent.size,
                waitSeconds = 0L
            )
        }
    }

    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): RateLimitCheckResult {
        val recent = pruneOldAttempts(loadAttemptTimestamps(), nowMillis).toMutableList()
        recent.add(nowMillis)
        saveAttemptTimestamps(recent)
        return checkAllowed(nowMillis)
    }

    fun resetOnSuccess() {
        prefs.edit().remove(KEY_ATTEMPTS).apply()
    }

    fun clearLegacyFailedAttemptsOnce() {
        if (!prefs.getBoolean(KEY_LEGACY_SMS_CLEARED, false)) {
            prefs.edit()
                .remove(KEY_ATTEMPTS)
                .putBoolean(KEY_LEGACY_SMS_CLEARED, true)
                .apply()
        }
    }

    private fun pruneOldAttempts(attempts: List<Long>, nowMillis: Long): List<Long> {
        val cutoff = nowMillis - WINDOW_MILLIS
        return attempts.filter { it >= cutoff }
    }

    private fun loadAttemptTimestamps(): List<Long> {
        val raw = prefs.getString(KEY_ATTEMPTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx -> arr.optLong(idx).takeIf { it > 0L } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAttemptTimestamps(list: List<Long>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_ATTEMPTS, arr.toString()).apply()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val WINDOW_MILLIS = 10 * 60 * 1000L // 10 minutes
        private const val PREFS_NAME = "meethaq_auth_security_prefs"
        private const val KEY_ATTEMPTS = "failed_auth_timestamps"
        private const val KEY_LEGACY_SMS_CLEARED = "legacy_sms_attempts_cleared_v1"
        private const val KEY_INSTALL_ID = "device_install_salt"
        private const val KEY_PENDING_SIGNUP = "pending_signup_draft"

        fun getOrCreateInstallId(context: Context): String {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = p.getString(KEY_INSTALL_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            p.edit().putString(KEY_INSTALL_ID, generated).apply()
            return generated
        }

        fun savePendingSignupInfo(context: Context, fullName: String, phone: String) {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            p.edit()
                .putString("${KEY_PENDING_SIGNUP}_name", fullName.trim())
                .putString("${KEY_PENDING_SIGNUP}_phone", phone.trim())
                .apply()
        }

        fun consumePendingSignupInfo(context: Context): Pair<String?, String?> {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = p.getString("${KEY_PENDING_SIGNUP}_name", null)?.takeIf { it.isNotBlank() }
            val phone = p.getString("${KEY_PENDING_SIGNUP}_phone", null)?.takeIf { it.isNotBlank() }
            p.edit()
                .remove("${KEY_PENDING_SIGNUP}_name")
                .remove("${KEY_PENDING_SIGNUP}_phone")
                .apply()
            return name to phone
        }

        fun computeDeviceSessionId(context: Context, uid: String): String {
            val installId = getOrCreateInstallId(context)
            return "android_" + AgreementHasher.computeSha256("$uid|$installId").take(16)
        }

        fun getDeviceLabel(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            val combined = "$manufacturer $model".trim()
            return combined.ifBlank { "Android Device" }.take(120)
        }

        fun getUserAgent(): String {
            return "MeethaqAndroid/1.0 (Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT})"
                .take(250)
        }

        fun computeNetworkIpHash(context: Context): String {
            val rawAddress = try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress != null }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            } ?: getOrCreateInstallId(context)
            return AgreementHasher.computeSha256("meethaq_ip|$rawAddress").take(32)
        }
    }
}
