package com.example.crypto

import com.example.model.Agreement
import com.example.model.AmendmentDiffItem
import com.example.model.Party
import com.example.model.Witness
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object AgreementHasher {

    private val random = SecureRandom()
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateAgreementCode(year: Int = 2026): String {
        val suffix = buildString {
            repeat(6) {
                append(CODE_CHARS[random.nextInt(CODE_CHARS.length)])
            }
        }
        return "MTH-$year-$suffix"
    }

    fun generateToken(prefix: String = "inv"): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val hex = bytes.joinToString(separator = "") { b -> "%02x".format(Locale.US, b) }
        return "${prefix}_$hex"
    }

    fun generateStrongInviteToken(prefix: String = "inv"): String = generateToken(prefix)

    fun generateNumericOtp(digits: Int = 6): String {
        val cleanDigits = digits.coerceIn(4, 8)
        return buildString {
            repeat(cleanDigits) {
                append(random.nextInt(10))
            }
        }
    }

    fun generateSalt(): String = generateToken("slt")

    fun hashOtpChallenge(
        agreementId: String,
        targetUid: String,
        otp: String,
        salt: String
    ): String {
        val payload = "${agreementId.trim()}|${targetUid.trim()}|${otp.trim()}|${salt.trim()}"
        return computeSha256(payload)
    }

    fun computeSha256(raw: String): String {
        return computeBytesSha256(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun computeBytesSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
    }

    fun computeContentHash(
        agreementCode: String,
        title: String,
        type: String,
        description: String,
        amount: Double,
        currency: String,
        paymentMethod: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        ownerId: String,
        participantEmails: List<String>
    ): String {
        val canonicalPayload = listOf(
            agreementCode.trim(),
            title.trim(),
            type.trim(),
            description.trim(),
            String.format(Locale.US, "%.2f", amount),
            currency.trim(),
            paymentMethod.trim(),
            startDate.trim(),
            dueDate.trim(),
            rights.joinToString(";").trim(),
            obligations.joinToString(";").trim(),
            specialTerms.joinToString(";").trim(),
            ownerId.trim(),
            participantEmails.map { it.trim().lowercase(Locale.US) }.sorted().joinToString(",")
        ).joinToString(separator = "|")

        return computeSha256(canonicalPayload)
    }

    fun computeApprovedSnapshotHash(
        agreementCode: String,
        versionId: String,
        contentHash: String,
        title: String,
        type: String,
        description: String,
        amount: Double,
        currency: String,
        paymentMethod: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        parties: List<Party>,
        witnesses: List<Witness>
    ): String {
        val canonicalParties = parties.joinToString(";") { party ->
            listOf(
                party.uid.orEmpty().trim(),
                party.name.trim(),
                party.email.trim().lowercase(Locale.US),
                party.phone.trim(),
                party.role.trim(),
                party.approved.toString(),
                party.verifiedPhone.toString(),
                party.biometricVerified.toString(),
                party.credentialId.orEmpty().trim(),
                party.verificationMethod.orEmpty().trim()
            ).joinToString(":")
        }
        val canonicalWitnesses = witnesses.joinToString(";") { w ->
            listOf(
                w.uid.orEmpty().trim(),
                w.name.trim(),
                w.email.trim().lowercase(Locale.US),
                w.phone.trim(),
                (w.approvedAtMillis != null).toString(),
                w.verifiedPhone.toString(),
                w.biometricVerified.toString(),
                w.credentialId.orEmpty().trim()
            ).joinToString(":")
        }

        val payload = listOf(
            "APPROVED_V1",
            agreementCode.trim(),
            versionId.trim(),
            contentHash.trim(),
            title.trim(),
            type.trim(),
            description.trim(),
            String.format(Locale.US, "%.2f", amount),
            currency.trim(),
            paymentMethod.trim(),
            startDate.trim(),
            dueDate.trim(),
            rights.joinToString(";").trim(),
            obligations.joinToString(";").trim(),
            specialTerms.joinToString(";").trim(),
            canonicalParties,
            canonicalWitnesses
        ).joinToString("|")

        return computeSha256(payload)
    }

    fun computeCanonicalApprovedHash(agreement: Agreement): String = computeApprovedSnapshotHash(
        agreementCode = agreement.agreementCode,
        versionId = agreement.currentVersionId,
        contentHash = agreement.contentHash,
        title = agreement.title,
        type = agreement.type,
        description = agreement.description,
        amount = agreement.amount,
        currency = agreement.currency,
        paymentMethod = agreement.paymentMethod,
        startDate = agreement.startDate,
        dueDate = agreement.dueDate,
        rights = agreement.rights,
        obligations = agreement.obligations,
        specialTerms = agreement.specialTerms,
        parties = agreement.parties,
        witnesses = agreement.witnesses
    )

    fun computeAmendmentDiff(
        current: Agreement,
        proposedTitle: String,
        proposedDescription: String,
        proposedAmount: Double,
        proposedDueDate: String,
        proposedRights: List<String>,
        proposedObligations: List<String>,
        proposedSpecialTerms: List<String>
    ): List<AmendmentDiffItem> {
        val diffs = mutableListOf<AmendmentDiffItem>()
        if (current.title.trim() != proposedTitle.trim()) {
            diffs.add(AmendmentDiffItem("العنوان", current.title.trim(), proposedTitle.trim()))
        }
        if (current.description.trim() != proposedDescription.trim()) {
            diffs.add(AmendmentDiffItem("الوصف ونطاق الاتفاق", current.description.trim(), proposedDescription.trim()))
        }
        if (kotlin.math.abs(current.amount - proposedAmount) > 0.001) {
            diffs.add(
                AmendmentDiffItem(
                    "المبلغ المالي",
                    String.format(Locale.US, "%.2f", current.amount),
                    String.format(Locale.US, "%.2f", proposedAmount)
                )
            )
        }
        if (current.dueDate.trim() != proposedDueDate.trim()) {
            diffs.add(AmendmentDiffItem("تاريخ الاستحقاق", current.dueDate.ifBlank { "—" }, proposedDueDate.ifBlank { "—" }))
        }
        if (current.rights != proposedRights) {
            diffs.add(
                AmendmentDiffItem(
                    "الحقوق",
                    current.rights.joinToString(" | ").ifBlank { "—" },
                    proposedRights.joinToString(" | ").ifBlank { "—" }
                )
            )
        }
        if (current.obligations != proposedObligations) {
            diffs.add(
                AmendmentDiffItem(
                    "الالتزامات",
                    current.obligations.joinToString(" | ").ifBlank { "—" },
                    proposedObligations.joinToString(" | ").ifBlank { "—" }
                )
            )
        }
        if (current.specialTerms != proposedSpecialTerms) {
            diffs.add(
                AmendmentDiffItem(
                    "الشروط الخاصة",
                    current.specialTerms.joinToString(" | ").ifBlank { "—" },
                    proposedSpecialTerms.joinToString(" | ").ifBlank { "—" }
                )
            )
        }
        return diffs
    }

    fun verifyAgreementIntegrity(agreement: Agreement): Boolean {
        val recomputed = computeContentHash(
            agreementCode = agreement.agreementCode,
            title = agreement.title,
            type = agreement.type,
            description = agreement.description,
            amount = agreement.amount,
            currency = agreement.currency,
            paymentMethod = agreement.paymentMethod,
            startDate = agreement.startDate,
            dueDate = agreement.dueDate,
            rights = agreement.rights,
            obligations = agreement.obligations,
            specialTerms = agreement.specialTerms,
            ownerId = agreement.ownerId,
            participantEmails = agreement.participantEmails
        )
        return recomputed == agreement.contentHash
    }
}
