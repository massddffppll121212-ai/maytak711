package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemePreference(val arabicLabel: String) {
    SYSTEM("تلقائي (النظام)"),
    LIGHT("الوضع النهاري"),
    DARK("الوضع الليلي");

    companion object {
        fun fromRaw(raw: String?): ThemePreference =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class AgreementType(val arabicLabel: String) {
    FINANCIAL("التزام مالي"),
    PARTNERSHIP("شراكة تجارية"),
    SERVICE("تقديم خدمة / عمل"),
    LEASE("إيجار / منفعة"),
    LOAN("قرض / سلفة"),
    NDA("سرية وعدم إفشاء"),
    SALE("بيع / توريد"),
    EMPLOYMENT("عقد عمل / توظيف"),
    AGENCY("وكالة / تمثيل"),
    OTHER("اتفاق عام");

    companion object {
        fun fromRaw(raw: String?): AgreementType =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class AgreementStatus(val arabicLabel: String) {
    DRAFT("مسودة"),
    PENDING("بانتظار الموافقة"),
    APPROVED("معتمد"),
    NEEDS_EDIT("يحتاج تعديل"),
    EXPIRED("منتهٍ"),
    DISPUTED("متنازع");

    companion object {
        fun fromRaw(raw: String?): AgreementStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

enum class CurrencyCode(val arabicLabel: String, val symbol: String) {
    SAR("ريال سعودي", "ر.س"),
    YER("ريال يمني", "ر.ي"),
    AED("درهم إماراتي", "د.إ"),
    KWD("دينار كويتي", "د.ك"),
    EGP("جنيه مصري", "ج.م"),
    USD("دولار أمريكي", "$"),
    EUR("يورو", "€"),
    NONE("بدون مقابل مالي", "—");

    companion object {
        fun fromRaw(raw: String?): CurrencyCode =
            entries.firstOrNull { it.name == raw } ?: SAR
    }
}

enum class PaymentMethodType(val arabicLabel: String) {
    BANK_TRANSFER("تحويل بنكي"),
    CASH("نقدي"),
    ELECTRONIC("دفع إلكتروني"),
    INSTALLMENTS("دفعات مجدولة"),
    NONE("لا ينطبق");

    companion object {
        fun fromRaw(raw: String?): PaymentMethodType =
            entries.firstOrNull { it.name == raw } ?: BANK_TRANSFER
    }
}

enum class PaymentStatus(val arabicLabel: String) {
    PENDING_CONFIRMATION("بانتظار التأكيد"),
    CONFIRMED("مؤكد بالكامل"),
    PARTIAL_CONFIRMED("مؤكد جزئياً"),
    NOT_RECEIVED("غير مستلم");

    val isFinalized: Boolean
        get() = this != PENDING_CONFIRMATION

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING_CONFIRMATION
    }
}

enum class PaymentConfirmationMode(val arabicLabel: String) {
    FULL("استلام كامل"),
    PARTIAL("استلام جزئي"),
    NOT_RECEIVED("عدم الاستلام");

    companion object {
        fun fromRaw(raw: String?): PaymentConfirmationMode =
            entries.firstOrNull { it.name == raw } ?: FULL
    }
}

enum class AmendmentStatus(val arabicLabel: String) {
    PENDING("بانتظار إجماع الأطراف"),
    APPROVED_APPLIED("معتمد ومُطبّق"),
    REJECTED("مرفوض");

    companion object {
        fun fromRaw(raw: String?): AmendmentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class DisputeStatus(val arabicLabel: String) {
    OPEN("مفتوح"),
    UNDER_REVIEW("قيد المراجعة"),
    RESOLVED("تمت التسوية");

    companion object {
        fun fromRaw(raw: String?): DisputeStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class InvitationPurpose(val arabicLabel: String) {
    PARTY("طرف في الميثاق"),
    WITNESS("شاهد");

    companion object {
        fun fromRaw(raw: String?): InvitationPurpose =
            entries.firstOrNull { it.name == raw } ?: PARTY
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoURL: String? = null,
    val phoneVerified: Boolean = false,
    val biometricCredentialId: String? = null,
    val preferredTheme: String = ThemePreference.SYSTEM.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val themePreferenceEnum: ThemePreference
        get() = ThemePreference.fromRaw(preferredTheme)
}

@Keep
@IgnoreExtraProperties
data class VaultDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "",
    val referenceCode: String = "",
    val notes: String? = null,
    val sha256Hash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val device: String = "",
    val userAgent: String = "",
    val ipHash: String = "",
    val revoked: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Party(
    val uid: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "طرف",
    val verifiedPhone: Boolean = false,
    val biometricVerified: Boolean = false,
    val approved: Boolean = false,
    val credentialId: String? = null,
    val signedAtMillis: Long? = null,
    val verificationMethod: String? = null,
    val signatureHash: String? = null
) {
    fun canonicalKey(): String = when {
        !uid.isNullOrBlank() -> "uid:${uid.trim()}"
        email.isNotBlank() -> "email:${email.trim().lowercase()}"
        phone.isNotBlank() -> "phone:${phone.trim()}"
        else -> "name:${name.trim()}"
    }

    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role,
        "verifiedPhone" to verifiedPhone,
        "biometricVerified" to biometricVerified,
        "approved" to approved,
        "credentialId" to credentialId,
        "signedAtMillis" to signedAtMillis,
        "verificationMethod" to verificationMethod,
        "signatureHash" to signatureHash
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Witness(
    val uid: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val nationalId: String? = null,
    val approvedAtMillis: Long? = null,
    val verifiedPhone: Boolean = false,
    val biometricVerified: Boolean = false,
    val credentialId: String? = null,
    val verificationMethod: String? = null,
    val signatureHash: String? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "nationalId" to nationalId,
        "approvedAtMillis" to approvedAtMillis,
        "verifiedPhone" to verifiedPhone,
        "biometricVerified" to biometricVerified,
        "credentialId" to credentialId,
        "verificationMethod" to verificationMethod,
        "signatureHash" to signatureHash
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AgreementVersion(
    val versionId: String = "v1",
    val hash: String = "",
    val createdAtMillis: Long = 0L,
    val approvedBy: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "versionId" to versionId,
        "hash" to hash,
        "createdAtMillis" to createdAtMillis,
        "approvedBy" to approvedBy
    )
}

@Keep
@IgnoreExtraProperties
data class AgreementAttachment(
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val sha256Hash: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fileName" to fileName,
        "mimeType" to mimeType,
        "sizeBytes" to sizeBytes,
        "sha256Hash" to sha256Hash
    )
}

@Keep
@IgnoreExtraProperties
data class Agreement(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val ownerPhone: String = "",
    val agreementCode: String = "",
    val title: String = "",
    val type: String = AgreementType.OTHER.name,
    val description: String = "",
    val status: String = AgreementStatus.DRAFT.name,
    val currency: String = CurrencyCode.SAR.name,
    val amount: Double = 0.0,
    val paidTotal: Double = 0.0,
    val agreementDate: String = "",
    val startDate: String = "",
    val dueDate: String = "",
    val paymentMethod: String = PaymentMethodType.BANK_TRANSFER.name,
    val rights: List<String> = emptyList(),
    val obligations: List<String> = emptyList(),
    val specialTerms: List<String> = emptyList(),
    val parties: List<Party> = emptyList(),
    val witnesses: List<Witness> = emptyList(),
    val attachments: List<AgreementAttachment> = emptyList(),
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    val currentVersionId: String = "v1",
    val versions: List<AgreementVersion> = emptyList(),
    val contentHash: String = "",
    val approvedHash: String = "",
    val approvedSnapshot: Map<String, Any>? = null,
    val lastAppliedAmendmentId: String? = null,
    val editRequestNote: String? = null,
    val archived: Boolean = false,
    @ServerTimestamp val lockedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: AgreementType
        get() = AgreementType.fromRaw(type)

    val statusEnum: AgreementStatus
        get() = AgreementStatus.fromRaw(status)

    val currencyEnum: CurrencyCode
        get() = CurrencyCode.fromRaw(currency)

    val paymentMethodEnum: PaymentMethodType
        get() = PaymentMethodType.fromRaw(paymentMethod)

    val remainingAmount: Double
        get() = (amount - paidTotal).coerceAtLeast(0.0)

    val allPartiesSigned: Boolean
        get() = parties.isNotEmpty() && parties.all { it.approved }

    val allWitnessesSigned: Boolean
        get() = witnesses.all { it.approvedAtMillis != null }
}

@Keep
@IgnoreExtraProperties
data class OtpChallengeRecord(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val targetUid: String = "",
    val targetIdentifier: String = "",
    val otpHash: String = "",
    val otpSalt: String = "",
    val attempts: Int = 0,
    val maxAttempts: Int = 5,
    val used: Boolean = false,
    val verified: Boolean = false,
    val expiresAtMillis: Long = 0L,
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val verifiedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AgreementSignature(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val userId: String = "",
    val signerName: String = "",
    val role: String = "PARTY",
    val versionId: String = "v1",
    val versionHash: String = "",
    val phoneVerified: Boolean = false,
    val biometricVerified: Boolean = false,
    val credentialId: String? = null,
    val verificationMethod: String = "",
    val ipHash: String = "",
    val uaHash: String = "",
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    @ServerTimestamp val signedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AmendmentDiffItem(
    val fieldLabel: String = "",
    val oldValue: String = "",
    val newValue: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fieldLabel" to fieldLabel,
        "oldValue" to oldValue,
        "newValue" to newValue
    )
}

@Keep
@IgnoreExtraProperties
data class AgreementAmendment(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val baseVersionId: String = "v1",
    val proposedByUid: String = "",
    val proposedByName: String = "",
    val reason: String = "",
    val proposedTitle: String = "",
    val proposedDescription: String = "",
    val proposedAmount: Double = 0.0,
    val proposedDueDate: String = "",
    val proposedRights: List<String> = emptyList(),
    val proposedObligations: List<String> = emptyList(),
    val proposedSpecialTerms: List<String> = emptyList(),
    val diffItems: List<AmendmentDiffItem> = emptyList(),
    val requiredPartyKeys: List<String> = emptyList(),
    val approvedByUids: List<String> = emptyList(),
    val approvedPartyKeys: List<String> = emptyList(),
    val rejectedByUid: String? = null,
    val rejectionNote: String? = null,
    val status: String = AmendmentStatus.PENDING.name,
    val appliedVersionId: String? = null,
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val resolvedAt: Timestamp? = null
) {
    val statusEnum: AmendmentStatus
        get() = AmendmentStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AgreementPayment(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val recordedBy: String = "",
    val amount: Double = 0.0,
    val confirmedAmount: Double = 0.0,
    val paymentDateText: String = "",
    @ServerTimestamp val paidAt: Timestamp? = null,
    val method: String = PaymentMethodType.BANK_TRANSFER.name,
    val receiptReference: String = "",
    val attachment: AgreementAttachment? = null,
    val confirmedBy: List<String> = emptyList(),
    val confirmationMode: String? = null,
    val confirmationNote: String? = null,
    val witnessUid: String? = null,
    val witnessOtpVerified: Boolean = false,
    val status: String = PaymentStatus.PENDING_CONFIRMATION.name,
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val statusEnum: PaymentStatus
        get() = PaymentStatus.fromRaw(status)

    val methodEnum: PaymentMethodType
        get() = PaymentMethodType.fromRaw(method)

    val confirmationModeEnum: PaymentConfirmationMode?
        get() = confirmationMode?.let { PaymentConfirmationMode.fromRaw(it) }
}

@Keep
@IgnoreExtraProperties
data class DisputeReply(
    val authorUid: String = "",
    val authorName: String = "",
    val message: String = "",
    val createdAtMillis: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "authorUid" to authorUid,
        "authorName" to authorName,
        "message" to message,
        "createdAtMillis" to createdAtMillis
    )
}

@Keep
@IgnoreExtraProperties
data class AgreementDispute(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val type: String = "",
    val details: String = "",
    val evidenceNotes: List<String> = emptyList(),
    val status: String = DisputeStatus.OPEN.name,
    val openedBy: String = "",
    val openedByName: String = "",
    val replies: List<DisputeReply> = emptyList(),
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: DisputeStatus
        get() = DisputeStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val note: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val snapshotHash: String = "",
    val participantUids: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Invitation(
    @DocumentId val id: String = "",
    val agreementId: String = "",
    val agreementCode: String = "",
    val agreementTitle: String = "",
    val agreementSummary: String = "",
    val inviterUid: String = "",
    val inviterName: String = "",
    val inviteePhone: String = "",
    val inviteePhoneLast3: String = "",
    val inviteeEmail: String = "",
    val purpose: String = InvitationPurpose.PARTY.name,
    val expiresAt: Timestamp? = null,
    val usedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val purposeEnum: InvitationPurpose
        get() = InvitationPurpose.fromRaw(purpose)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
