package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.AgreementHasher
import com.example.data.DraftAutoSaveStore
import com.example.data.LocalDraftSnapshot
import com.example.model.AgreementType
import com.example.model.CurrencyCode
import com.example.model.Party
import com.example.model.PaymentMethodType
import com.example.ui.screens.extractInviteToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgreementCoreUnitTest {

    @Test
    fun `strong invite tokens are at least 20 chars and unique`() {
        val t1 = AgreementHasher.generateStrongInviteToken()
        val t2 = AgreementHasher.generateStrongInviteToken()
        assertTrue(t1.length >= 20)
        assertTrue(t2.length >= 20)
        assertNotEquals(t1, t2)
    }

    @Test
    fun `bytes sha256 produces deterministic 64-char hex`() {
        val hash = AgreementHasher.computeBytesSha256("meethaq-test".toByteArray(Charsets.UTF_8))
        assertEquals(64, hash.length)
        assertEquals(hash, AgreementHasher.computeBytesSha256("meethaq-test".toByteArray(Charsets.UTF_8)))
        assertNotEquals(hash, AgreementHasher.computeBytesSha256("meethaq-other".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `catalog supports 10 agreement types and Yemeni Riyal currency`() {
        assertEquals(10, AgreementType.entries.size)
        assertEquals("ريال يمني", CurrencyCode.YER.arabicLabel)
        assertEquals("ر.ي", CurrencyCode.YER.symbol)
    }

    @Test
    fun `extractInviteToken parses https links, scheme links, raw tokens and rejects invalid`() {
        val token = "inv_0123456789abcdef0123456789abcdef"
        assertEquals(token, extractInviteToken("https://meethaq.app/invite/$token"))
        assertEquals(token, extractInviteToken("https://meethaq.app/invite/$token?utm=sms"))
        assertEquals(token, extractInviteToken("meethaq://invite/$token"))
        assertEquals(token, extractInviteToken("  $token  "))

        assertNull(extractInviteToken(""))
        assertNull(extractInviteToken("short"))
        assertNull(extractInviteToken("https://meethaq.app/invite/too_short"))
        assertNull(extractInviteToken("https://meethaq.app/invite/bad token spaces!!"))
    }

    @Test
    fun `draft auto save store persists and clears snapshot`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val userId = "user_draft_test"
        DraftAutoSaveStore.clear(context, userId)
        assertNull(DraftAutoSaveStore.load(context, userId))

        val snapshot = LocalDraftSnapshot(
            stepIndex = 3,
            title = "عقد توريد مواد",
            type = AgreementType.SALE,
            description = "تفاصيل توريد دفعة أولى",
            currency = CurrencyCode.YER,
            amountText = "5000",
            paymentMethod = PaymentMethodType.BANK_TRANSFER,
            agreementDate = "2026-11-21",
            startDate = "2026-11-22",
            dueDate = "2026-12-22",
            rights = listOf("استلام البضاعة سليمة"),
            obligations = listOf("سداد المبلغ عند التسليم"),
            specialTerms = listOf("تسري أحكام التحكيم"),
            parties = listOf(Party(name = "طرف ثاني", phone = "+967773429852", role = "مشتري")),
            savedAtMillis = 1_700_000_000_000L
        )
        DraftAutoSaveStore.save(context, userId, snapshot)

        val loaded = DraftAutoSaveStore.load(context, userId)
        assertNotNull(loaded)
        assertEquals("عقد توريد مواد", loaded?.title)
        assertEquals(CurrencyCode.YER, loaded?.currency)
        assertEquals(3, loaded?.stepIndex)
        assertEquals(1, loaded?.parties?.size)
        assertEquals("+967773429852", loaded?.parties?.first()?.phone)

        DraftAutoSaveStore.clear(context, userId)
        assertNull(DraftAutoSaveStore.load(context, userId))
    }
}
