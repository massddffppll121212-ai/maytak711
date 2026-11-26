package com.example

import com.example.crypto.AgreementHasher
import com.example.model.Agreement
import com.example.model.AgreementStatus
import com.example.model.CurrencyCode
import com.example.model.Party
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.example.model.Witness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualVerificationAndFinanceTest {

    private fun sampleAgreement(
        parties: List<Party>,
        witnesses: List<Witness> = emptyList(),
        amount: Double = 25000.0,
        paidTotal: Double = 0.0
    ): Agreement {
        val code = "MTH-2026-TST001"
        val title = "عقد شراكة وتطوير تطبيق ميثاق"
        val description = "توثيق التزامات التطوير والدفعات المالية المجدولة."
        val contentHash = AgreementHasher.computeContentHash(
            agreementCode = code,
            title = title,
            type = "PARTNERSHIP",
            description = description,
            amount = amount,
            currency = CurrencyCode.SAR.name,
            paymentMethod = PaymentMethodType.BANK_TRANSFER.name,
            startDate = "2026-11-22",
            dueDate = "2026-12-31",
            rights = listOf("استلام المخرجات"),
            obligations = listOf("تسليم الدفعات بالموعد"),
            specialTerms = listOf("التعديل بالإجماع فقط"),
            ownerId = "uid_alice",
            participantEmails = listOf("alice@meethaq.app", "bob@meethaq.app")
        )
        return Agreement(
            id = "ag_test_1",
            ownerId = "uid_alice",
            ownerName = "أحمد",
            ownerEmail = "alice@meethaq.app",
            agreementCode = code,
            title = title,
            type = "PARTNERSHIP",
            description = description,
            status = AgreementStatus.PENDING.name,
            currency = CurrencyCode.SAR.name,
            amount = amount,
            paidTotal = paidTotal,
            startDate = "2026-11-22",
            dueDate = "2026-12-31",
            paymentMethod = PaymentMethodType.BANK_TRANSFER.name,
            rights = listOf("استلام المخرجات"),
            obligations = listOf("تسليم الدفعات بالموعد"),
            specialTerms = listOf("التعديل بالإجماع فقط"),
            parties = parties,
            witnesses = witnesses,
            participantUids = listOf("uid_alice", "uid_bob"),
            participantEmails = listOf("alice@meethaq.app", "bob@meethaq.app"),
            currentVersionId = "v1",
            contentHash = contentHash
        )
    }

    @Test
    fun `single party cannot finalize agreement before second party and witnesses sign`() {
        val aliceSigned = Party(
            uid = "uid_alice",
            name = "أحمد",
            email = "alice@meethaq.app",
            role = "طرف أول",
            verifiedPhone = true,
            biometricVerified = true,
            approved = true,
            credentialId = "cred_alice"
        )
        val bobUnsigned = Party(
            uid = "uid_bob",
            name = "سالم",
            email = "bob@meethaq.app",
            role = "طرف ثاني",
            approved = false
        )
        val witnessUnsigned = Witness(
            uid = "uid_witness",
            name = "خالد",
            email = "witness@meethaq.app",
            approvedAtMillis = null
        )

        val partialAgreement = sampleAgreement(
            parties = listOf(aliceSigned, bobUnsigned),
            witnesses = listOf(witnessUnsigned)
        )
        assertFalse("One party signing must NOT satisfy allPartiesSigned", partialAgreement.allPartiesSigned)
        assertFalse("Unsigned witness must NOT satisfy allWitnessesSigned", partialAgreement.allWitnessesSigned)

        // Bob signs, witness still unsigned -> still blocked from final approval
        val bobSigned = bobUnsigned.copy(
            verifiedPhone = true,
            biometricVerified = false,
            credentialId = "cred_bob_passkey",
            approved = true,
            signedAtMillis = 1_700_000_000_000L
        )
        val missingWitnessAgreement = sampleAgreement(
            parties = listOf(aliceSigned, bobSigned),
            witnesses = listOf(witnessUnsigned)
        )
        assertTrue(missingWitnessAgreement.allPartiesSigned)
        assertFalse(missingWitnessAgreement.allWitnessesSigned)

        // All parties + witnesses signed -> eligible for lock
        val readyAgreement = sampleAgreement(
            parties = listOf(aliceSigned, bobSigned),
            witnesses = listOf(witnessUnsigned.copy(verifiedPhone = true, approvedAtMillis = 1_700_000_100_000L))
        )
        assertTrue(readyAgreement.allPartiesSigned)
        assertTrue(readyAgreement.allWitnessesSigned)
    }

    @Test
    fun `canonical approvedHash is deterministic and detects any post-approval tampering`() {
        val parties = listOf(
            Party(uid = "uid_alice", name = "أحمد", verifiedPhone = true, biometricVerified = true, approved = true, credentialId = "cred_1"),
            Party(uid = "uid_bob", name = "سالم", verifiedPhone = true, biometricVerified = false, approved = true, credentialId = "cred_2")
        )
        val original = sampleAgreement(parties = parties)
        val hash1 = AgreementHasher.computeCanonicalApprovedHash(original)
        val hash2 = AgreementHasher.computeCanonicalApprovedHash(original)

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)

        val tamperedAmount = original.copy(amount = 99999.0)
        assertNotEquals(hash1, AgreementHasher.computeCanonicalApprovedHash(tamperedAmount))

        val tamperedPartyCred = original.copy(
            parties = listOf(parties[0], parties[1].copy(credentialId = "cred_forged"))
        )
        assertNotEquals(hash1, AgreementHasher.computeCanonicalApprovedHash(tamperedPartyCred))
    }

    @Test
    fun `otp challenge hash rejects wrong code, wrong uid, and binds unique salt`() {
        val otp = AgreementHasher.generateNumericOtp(6)
        assertEquals(6, otp.length)
        assertTrue(otp.all { it.isDigit() })

        val salt1 = AgreementHasher.generateSalt()
        val salt2 = AgreementHasher.generateSalt()
        assertNotEquals(salt1, salt2)

        val expectedHash = AgreementHasher.hashOtpChallenge("ag_1", "uid_bob", otp, salt1)
        assertEquals(64, expectedHash.length)
        assertEquals(expectedHash, AgreementHasher.hashOtpChallenge("ag_1", "uid_bob", otp, salt1))

        val wrongOtp = if (otp == "000000") "111111" else "000000"
        assertNotEquals(expectedHash, AgreementHasher.hashOtpChallenge("ag_1", "uid_bob", wrongOtp, salt1))
        assertNotEquals(expectedHash, AgreementHasher.hashOtpChallenge("ag_1", "uid_alice", otp, salt1))
        assertNotEquals(expectedHash, AgreementHasher.hashOtpChallenge("ag_1", "uid_bob", otp, salt2))
    }

    @Test
    fun `amendment diff captures changed fields and returns empty when identical`() {
        val current = sampleAgreement(
            parties = listOf(Party(uid = "uid_alice", name = "أحمد", approved = true))
        )

        val emptyDiff = AgreementHasher.computeAmendmentDiff(
            current = current,
            proposedTitle = current.title,
            proposedDescription = current.description,
            proposedAmount = current.amount,
            proposedDueDate = current.dueDate,
            proposedRights = current.rights,
            proposedObligations = current.obligations,
            proposedSpecialTerms = current.specialTerms
        )
        assertTrue(emptyDiff.isEmpty())

        val diff = AgreementHasher.computeAmendmentDiff(
            current = current,
            proposedTitle = "عقد شراكة محدّث - المرحلة الثانية",
            proposedDescription = current.description,
            proposedAmount = 32500.0,
            proposedDueDate = "2027-02-15",
            proposedRights = current.rights + "دعم فني 6 أشهر",
            proposedObligations = current.obligations,
            proposedSpecialTerms = current.specialTerms
        )
        assertEquals(4, diff.size)
        assertEquals(listOf("العنوان", "المبلغ المالي", "تاريخ الاستحقاق", "الحقوق"), diff.map { it.fieldLabel })
        assertEquals("25000.00", diff[1].oldValue)
        assertEquals("32500.00", diff[1].newValue)
    }

    @Test
    fun `payment finality and remaining balance calculation handle full, partial, and not received`() {
        assertFalse(PaymentStatus.PENDING_CONFIRMATION.isFinalized)
        assertTrue(PaymentStatus.CONFIRMED.isFinalized)
        assertTrue(PaymentStatus.PARTIAL_CONFIRMED.isFinalized)
        assertTrue(PaymentStatus.NOT_RECEIVED.isFinalized)
        assertEquals(3, PaymentConfirmationMode.entries.size)

        val afterFull = sampleAgreement(parties = emptyList(), amount = 10000.0, paidTotal = 4000.0)
        assertEquals(6000.0, afterFull.remainingAmount, 0.001)

        val afterPartial = afterFull.copy(paidTotal = afterFull.paidTotal + 2500.0)
        assertEquals(3500.0, afterPartial.remainingAmount, 0.001)

        // Overpayment clamps remainingAmount safely to 0.0
        val overpaid = afterFull.copy(paidTotal = 12000.0)
        assertEquals(0.0, overpaid.remainingAmount, 0.001)
    }
}
