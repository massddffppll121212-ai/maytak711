package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AuthManager
import com.example.data.AuthRateLimiter
import com.google.firebase.FirebaseTooManyRequestsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthSecurityTest {

    private lateinit var context: Context
    private lateinit var rateLimiter: AuthRateLimiter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rateLimiter = AuthRateLimiter(context)
        rateLimiter.resetOnSuccess()
    }

    @Test
    fun `rate limiter blocks after 5 failed attempts within 10 minutes and unlocks after window`() {
        val t0 = 1_700_000_000_000L

        assertTrue(rateLimiter.checkAllowed(t0).allowed)
        assertEquals(5, rateLimiter.checkAllowed(t0).remainingAttempts)

        repeat(4) { i ->
            val result = rateLimiter.recordFailure(t0 + i * 1_000L)
            assertTrue("Attempt ${i + 1} should still leave retries", result.allowed)
            assertEquals(4 - i, result.remainingAttempts)
        }

        // 5th failure locks out the user for the 10-minute window
        val locked = rateLimiter.recordFailure(t0 + 4_000L)
        assertFalse(locked.allowed)
        assertEquals(0, locked.remainingAttempts)
        assertTrue(locked.waitSeconds > 0L)

        // Still locked 9 minutes later
        assertFalse(rateLimiter.checkAllowed(t0 + 9 * 60_000L).allowed)

        // Sliding window unlocks 1 attempt once the oldest failure (t0) expires
        val partialUnlock = rateLimiter.checkAllowed(t0 + AuthRateLimiter.WINDOW_MILLIS + 1L)
        assertTrue(partialUnlock.allowed)
        assertEquals(1, partialUnlock.remainingAttempts)

        // All 5 attempts are restored once the full 10-minute window passes after the last failure
        val fullUnlock = rateLimiter.checkAllowed(t0 + 4_000L + AuthRateLimiter.WINDOW_MILLIS + 1L)
        assertTrue(fullUnlock.allowed)
        assertEquals(5, fullUnlock.remainingAttempts)
    }

    @Test
    fun `rate limiter resets immediately on successful authentication`() {
        val t0 = 1_700_000_000_000L
        repeat(5) { rateLimiter.recordFailure(t0) }
        assertFalse(rateLimiter.checkAllowed(t0 + 1_000L).allowed)

        rateLimiter.resetOnSuccess()
        val afterReset = rateLimiter.checkAllowed(t0 + 1_000L)
        assertTrue(afterReset.allowed)
        assertEquals(5, afterReset.remainingAttempts)
    }

    @Test
    fun `pending signup info is saved and consumed once`() {
        AuthRateLimiter.savePendingSignupInfo(context, " سارة القحطاني ", " +966501234567 ")
        val firstConsume = AuthRateLimiter.consumePendingSignupInfo(context)
        assertEquals("سارة القحطاني", firstConsume.first)
        assertEquals("+966501234567", firstConsume.second)

        val secondConsume = AuthRateLimiter.consumePendingSignupInfo(context)
        assertEquals(null, secondConsume.first)
        assertEquals(null, secondConsume.second)
    }

    @Test
    fun `device session id is deterministic per user and distinct across users`() {
        val s1 = AuthRateLimiter.computeDeviceSessionId(context, "user_a")
        val s2 = AuthRateLimiter.computeDeviceSessionId(context, "user_a")
        val s3 = AuthRateLimiter.computeDeviceSessionId(context, "user_b")

        assertTrue(s1.startsWith("android_"))
        assertEquals(s1, s2)
        assertNotEquals(s1, s3)
    }

    @Test
    fun `auth errors map to generic non-leaking Arabic messages`() {
        val rawException = RuntimeException("user-not-found: internal stack details uid=123")
        val message = AuthManager.toGenericAuthError(rawException)
        assertFalse(message.contains("user-not-found"))
        assertFalse(message.contains("uid=123"))

        val tooMany = AuthManager.toGenericAuthError(
            FirebaseTooManyRequestsException("Quota exceeded")
        )
        assertFalse(tooMany.contains("Quota"))
        assertTrue(tooMany.contains("المحاولة لاحقاً"))
    }

    @Test
    fun `phone helper normalizes 00-prefix, Arabic digits, and detects country code`() {
        // Exact user scenario: 00967773429852 -> Yemen (+967) + 773429852
        val (country, local) = com.example.ui.components.PhoneNumberHelper.detectCountryAndLocal("00967773429852")
        assertEquals("YE", country.iso)
        assertEquals("+967", country.dialCode)
        assertEquals("773429852", local)

        val e164From00 = com.example.ui.components.PhoneNumberHelper.buildE164(country, "00967773429852")
        assertEquals("+967773429852", e164From00)
        assertTrue(com.example.ui.components.PhoneNumberHelper.isValidE164(e164From00))

        // Saudi local number with leading 0 and Arabic-Indic digits
        val sa = com.example.ui.components.PhoneNumberHelper.countries.first { it.iso == "SA" }
        val e164Sa = com.example.ui.components.PhoneNumberHelper.buildE164(sa, "٠٥٠١٢٣٤٥٦٧")
        assertEquals("+966501234567", e164Sa)
        assertTrue(com.example.ui.components.PhoneNumberHelper.isValidE164(e164Sa))
    }

    @Test
    fun `phone mask formats per country and maintains valid offset mapping bounds`() {
        val helper = com.example.ui.components.PhoneNumberHelper
        val sa = helper.countries.first { it.iso == "SA" }
        val ye = helper.countries.first { it.iso == "YE" }
        val eg = helper.countries.first { it.iso == "EG" }
        val kw = helper.countries.first { it.iso == "KW" }

        assertEquals("50 123 4567", helper.formatLocalWithMask("501234567", sa.maskGroups))
        assertEquals("773 429 852", helper.formatLocalWithMask("773429852", ye.maskGroups))
        assertEquals("101 234 5678", helper.formatLocalWithMask("1012345678", eg.maskGroups))
        assertEquals("5012 3456", helper.formatLocalWithMask("50123456", kw.maskGroups))

        val transform = com.example.ui.components.PhoneMaskVisualTransformation(sa.maskGroups)
        val raw = "501234567"
        val result = transform.filter(androidx.compose.ui.text.AnnotatedString(raw))
        assertEquals("50 123 4567", result.text.text)

        var prevTrans = 0
        for (origOffset in 0..raw.length) {
            val mapped = result.offsetMapping.originalToTransformed(origOffset)
            assertTrue("origToTrans must be in bounds", mapped in 0..result.text.text.length)
            assertTrue("origToTrans must be monotonic", mapped >= prevTrans)
            prevTrans = mapped
        }

        var prevOrig = 0
        for (transOffset in 0..result.text.text.length) {
            val mapped = result.offsetMapping.transformedToOriginal(transOffset)
            assertTrue("transToOrig must be in bounds", mapped in 0..raw.length)
            assertTrue("transToOrig must be monotonic", mapped >= prevOrig)
            prevOrig = mapped
        }
    }

    @Test
    fun `phone input processor strips trunk zero, clamps max length, and detects prefixes`() {
        val helper = com.example.ui.components.PhoneNumberHelper
        val sa = helper.countries.first { it.iso == "SA" }

        // Trunk zero followed by digit strips leading 0 and clamps to 9 digits for SA
        val (saRes, saLocal) = helper.processPhoneFieldInput("050123456789", sa)
        assertEquals("SA", saRes.iso)
        assertEquals("501234567", saLocal)
        assertTrue(helper.isCompleteForCountry(saRes, saLocal))

        // Incremental 00 prefix waits until 5th char, then detects Yemen (+967)
        val (midCountry, midLocal) = helper.processPhoneFieldInput("0096", sa)
        assertEquals("SA", midCountry.iso)
        assertEquals("0096", midLocal)
        assertFalse(helper.isCompleteForCountry(midCountry, midLocal))

        val (yeCountry, yeLocal) = helper.processPhoneFieldInput("00967773429852", sa)
        assertEquals("YE", yeCountry.iso)
        assertEquals("773429852", yeLocal)
        assertTrue(helper.isCompleteForCountry(yeCountry, yeLocal))

        // Partial input is not marked complete
        assertFalse(helper.isCompleteForCountry(yeCountry, "7734"))
    }
}
