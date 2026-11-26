package com.example.data

import android.content.Context
import com.example.crypto.AgreementHasher
import com.example.model.Agreement
import com.example.model.AgreementAmendment
import com.example.model.AgreementAttachment
import com.example.model.AgreementPayment
import com.example.model.AgreementSignature
import com.example.model.AgreementStatus
import com.example.model.AgreementVersion
import com.example.model.AmendmentStatus
import com.example.model.OtpChallengeRecord
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OtpChallengeIssued(
    val challengeId: String,
    val targetIdentifier: String,
    val expiresAtMillis: Long,
    val verificationCode: String
)

data class OtpVerifyOutcome(
    val verified: Boolean,
    val message: String,
    val remainingAttempts: Int
)

internal class AgreementSecurityWorkflows(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private data class Actor(
        val uid: String,
        val email: String,
        val phone: String,
        val name: String
    )

    private fun requireActor(): Actor {
        val user = auth.currentUser ?: throw IllegalStateException("يجب تسجيل الدخول أولاً.")
        val email = user.email?.trim()?.lowercase(Locale.US).orEmpty()
        val phone = user.phoneNumber?.trim().orEmpty()
        val name = user.displayName?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(120)
            ?: email.substringBefore("@").takeIf { it.isNotBlank() }
            ?: phone.takeIf { it.isNotBlank() }
            ?: "مستخدم ميثاق"
        return Actor(
            uid = user.uid,
            email = email,
            phone = phone,
            name = name.take(120)
        )
    }

    private fun isMatchingPhone(a: String, b: String): Boolean {
        val ca = a.trim()
        val cb = b.trim()
        return ca.isNotBlank() && cb.isNotBlank() && ca == cb
    }

    private fun isMember(agreement: Agreement, actor: Actor): Boolean {
        return agreement.ownerId == actor.uid ||
            (actor.uid in agreement.participantUids) ||
            (actor.email.isNotBlank() && agreement.participantEmails.any { it.equals(actor.email, ignoreCase = true) }) ||
            (actor.phone.isNotBlank() && agreement.participantPhones.any { isMatchingPhone(it, actor.phone) })
    }

    private fun buildUpdatedParticipantFields(
        agreement: Agreement,
        actor: Actor
    ): Triple<List<String>, List<String>, List<String>> {
        val uids = (agreement.participantUids + actor.uid).distinct().take(10)
        val emails = if (actor.email.isNotBlank()) {
            (agreement.participantEmails + actor.email).distinct().take(10)
        } else {
            agreement.participantEmails
        }
        val phones = if (actor.phone.isNotBlank()) {
            (agreement.participantPhones + actor.phone).distinct().take(10)
        } else {
            agreement.participantPhones
        }
        return Triple(uids, emails, phones)
    }

    suspend fun saveUserCredentialId(credentialId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val actor = requireActor()
            val cleanCred = credentialId.trim().take(128)
            require(cleanCred.isNotBlank()) { "معرّف المفتاح غير صالح." }
            db.collection("users").document(actor.uid)
                .update(
                    mapOf(
                        "biometricCredentialId" to cleanCred,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendVerificationOtp(
        appContext: Context,
        agreement: Agreement,
        targetIdentifierInput: String
    ): Result<OtpChallengeIssued> {
        var docPath = "agreements/${agreement.id}/approvals"
        return try {
            val actor = requireActor()
            require(isMember(agreement, actor)) { "غير مصرح لك بطلب رمز تحقق لهذا الميثاق." }

            val limiter = AuthRateLimiter(appContext)
            val rateCheck = limiter.checkAllowed()
            if (!rateCheck.allowed) {
                val mins = (rateCheck.waitSeconds / 60L).coerceAtLeast(1L)
                return Result.failure(
                    IllegalStateException("تم تجاوز الحد المسموح (5 محاولات / 10 دقائق). يرجى الانتظار $mins دقيقة.")
                )
            }

            val cleanTarget = targetIdentifierInput.trim().ifBlank {
                actor.phone.ifBlank { actor.email.ifBlank { actor.uid } }
            }.take(150)

            val otp = AgreementHasher.generateNumericOtp(6)
            val salt = AgreementHasher.generateSalt()
            val otpHash = AgreementHasher.hashOtpChallenge(agreement.id, actor.uid, otp, salt)
            val expiresAtMillis = System.currentTimeMillis() + 5L * 60_000L

            val (uids, emails, phones) = buildUpdatedParticipantFields(agreement, actor)
            val agreementRef = db.collection("agreements").document(agreement.id)
            val approvalRef = agreementRef.collection("approvals").document()
            val auditRef = agreementRef.collection("audit").document()
            docPath = approvalRef.path

            val batch = db.batch()
            batch.set(
                approvalRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "targetUid" to actor.uid,
                    "targetIdentifier" to cleanTarget,
                    "otpHash" to otpHash,
                    "otpSalt" to salt,
                    "attempts" to 0,
                    "maxAttempts" to 5,
                    "used" to false,
                    "verified" to false,
                    "expiresAtMillis" to expiresAtMillis,
                    "participantUids" to uids,
                    "participantEmails" to emails,
                    "participantPhones" to phones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "OTP_CHALLENGE_ISSUED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "إصدار تحدي تحقق OTP لـ $cleanTarget (صالح لمدة 5 دقائق وبحد أقصى 5 محاولات).",
                    "metadata" to mapOf(
                        "challengeId" to approvalRef.id,
                        "targetIdentifier" to cleanTarget,
                        "expiresAtMillis" to expiresAtMillis
                    ),
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to uids,
                    "participantEmails" to emails,
                    "participantPhones" to phones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(
                OtpChallengeIssued(
                    challengeId = approvalRef.id,
                    targetIdentifier = cleanTarget,
                    expiresAtMillis = expiresAtMillis,
                    verificationCode = otp
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyVerificationOtp(
        appContext: Context,
        agreement: Agreement,
        challengeId: String,
        enteredOtp: String
    ): Result<OtpVerifyOutcome> {
        val docPath = "agreements/${agreement.id}/approvals/$challengeId"
        return try {
            val actor = requireActor()
            val cleanCode = enteredOtp.trim()
            require(cleanCode.length in 4..8 && cleanCode.all { it.isDigit() }) {
                "يرجى إدخال رمز التحقق المكوّن من 6 أرقام."
            }

            val limiter = AuthRateLimiter(appContext)
            val rateCheck = limiter.checkAllowed()
            if (!rateCheck.allowed) {
                val mins = (rateCheck.waitSeconds / 60L).coerceAtLeast(1L)
                return Result.failure(
                    IllegalStateException("تم تعليق المحاولات مؤقتاً (5 محاولات / 10 دقائق). حاول بعد $mins دقيقة.")
                )
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val approvalRef = agreementRef.collection("approvals").document(challengeId)
            val auditRef = agreementRef.collection("audit").document()

            val outcome = db.runTransaction { tx ->
                val approvalSnap = tx.get(approvalRef)
                if (!approvalSnap.exists()) {
                    throw IllegalStateException("لم يتم العثور على جلسة التحقق المطلوبة.")
                }
                val record = approvalSnap.toObject(
                    OtpChallengeRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("تعذّر قراءة تحدي OTP.")

                require(record.targetUid == actor.uid) { "هذا الرمز غير مخصص لحسابك." }
                require(!record.used) { "تم استخدام هذا الرمز مسبقاً ولا يمكن إعادة استخدامه." }
                require(record.attempts < record.maxAttempts) {
                    "تم استنفاد الحد الأقصى لمحاولات التحقق (5 محاولات). اطلب رمزاً جديداً."
                }
                val nowMillis = System.currentTimeMillis()
                require(nowMillis <= record.expiresAtMillis) {
                    "انتهت صلاحية رمز التحقق. يرجى طلب رمز جديد."
                }

                val agreementSnap = tx.get(agreementRef)
                val latestAgreement = agreementSnap.toObject(
                    Agreement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("الميثاق غير موجود.")

                val candidateHash = AgreementHasher.hashOtpChallenge(
                    agreementId = agreement.id,
                    targetUid = actor.uid,
                    otp = cleanCode,
                    salt = record.otpSalt
                )
                val nextAttempts = record.attempts + 1

                if (candidateHash != record.otpHash) {
                    val remaining = (record.maxAttempts - nextAttempts).coerceAtLeast(0)
                    tx.update(
                        approvalRef,
                        mapOf(
                            "attempts" to nextAttempts,
                            "used" to (nextAttempts >= record.maxAttempts)
                        )
                    )
                    return@runTransaction OtpVerifyOutcome(
                        verified = false,
                        message = if (remaining == 0) {
                            "الرمز غير صحيح وتم إغلاق التحدي بعد 5 محاولات خاطئة."
                        } else {
                            "رمز التحقق غير صحيح. المحاولات المتبقية: $remaining"
                        },
                        remainingAttempts = remaining
                    )
                }

                val updatedParties = latestAgreement.parties.map { p ->
                    val matches = (p.uid == actor.uid) ||
                        (actor.email.isNotBlank() && p.email.equals(actor.email, ignoreCase = true)) ||
                        (actor.phone.isNotBlank() && isMatchingPhone(p.phone, actor.phone))
                    if (matches) p.copy(uid = actor.uid, verifiedPhone = true) else p
                }
                val updatedWitnesses = latestAgreement.witnesses.map { w ->
                    val matches = (w.uid == actor.uid) ||
                        (actor.email.isNotBlank() && w.email.equals(actor.email, ignoreCase = true)) ||
                        (actor.phone.isNotBlank() && isMatchingPhone(w.phone, actor.phone))
                    if (matches) w.copy(uid = actor.uid, verifiedPhone = true) else w
                }
                val updatedUids = (latestAgreement.participantUids + actor.uid).distinct().take(10)

                tx.update(
                    approvalRef,
                    mapOf(
                        "attempts" to nextAttempts,
                        "used" to true,
                        "verified" to true,
                        "verifiedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (latestAgreement.statusEnum == AgreementStatus.PENDING) {
                    tx.update(
                        agreementRef,
                        mapOf(
                            "parties" to updatedParties.map { it.toMap() },
                            "witnesses" to updatedWitnesses.map { it.toMap() },
                            "participantUids" to updatedUids,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.set(
                    auditRef,
                    mapOf(
                        "agreementId" to agreement.id,
                        "action" to "OTP_VERIFIED",
                        "actorUid" to actor.uid,
                        "actorName" to actor.name,
                        "note" to "تم التحقق من رمز OTP بنجاح للطرف/الشاهد.",
                        "metadata" to mapOf(
                            "challengeId" to challengeId,
                            "attemptsUsed" to nextAttempts
                        ),
                        "snapshotHash" to latestAgreement.contentHash,
                        "participantUids" to updatedUids,
                        "participantEmails" to latestAgreement.participantEmails,
                        "participantPhones" to latestAgreement.participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                OtpVerifyOutcome(
                    verified = true,
                    message = "تم التحقق من الهاتف/OTP بنجاح وتحديث حالة التحقق.",
                    remainingAttempts = record.maxAttempts - nextAttempts
                )
            }.await()

            if (outcome.verified) {
                limiter.resetOnSuccess()
                db.collection("users").document(actor.uid).update(
                    mapOf(
                        "phoneVerified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                limiter.recordFailure()
            }
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun signAgreement(
        appContext: Context,
        agreement: Agreement,
        phoneVerified: Boolean,
        biometricVerified: Boolean,
        credentialId: String?,
        verificationMethod: String,
        signatureHex: String?
    ): Result<AgreementSignature> {
        val docPath = "agreements/${agreement.id}/signatures"
        return try {
            val actor = requireActor()
            require(agreement.statusEnum == AgreementStatus.PENDING) {
                "التوقيع متاح فقط للمواثيق التي في حالة (بانتظار الموافقة)."
            }
            require(isMember(agreement, actor)) {
                "غير مصرح لك بتوقيع هذا الميثاق."
            }
            require(phoneVerified || biometricVerified || !credentialId.isNullOrBlank()) {
                "يجب إكمال خطوة واحدة على الأقل (OTP أو التوقيع البيومتري) قبل تأكيد الموافقة."
            }

            val ipHash = AuthRateLimiter.computeNetworkIpHash(appContext)
            val uaHash = AgreementHasher.computeSha256(AuthRateLimiter.getUserAgent()).take(32)
            val agreementRef = db.collection("agreements").document(agreement.id)
            val sigRef = agreementRef.collection("signatures").document()
            val auditRef = agreementRef.collection("audit").document()
            val nowMillis = System.currentTimeMillis()
            val cleanMethod = verificationMethod.trim().ifBlank { "DUAL_VERIFICATION" }.take(80)

            val recordedSig = db.runTransaction { tx ->
                val snap = tx.get(agreementRef)
                val latest = snap.toObject(
                    Agreement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("الميثاق غير موجود.")

                require(latest.statusEnum == AgreementStatus.PENDING) {
                    "تغيرت حالة الميثاق ولا يمكن إضافة توقيع جديد الآن."
                }

                var matchedAsParty = false
                var matchedAsWitness = false
                val effectivePhoneVerified = phoneVerified

                val updatedParties = latest.parties.map { p ->
                    val matches = (p.uid == actor.uid) ||
                        (actor.email.isNotBlank() && p.email.equals(actor.email, ignoreCase = true)) ||
                        (actor.phone.isNotBlank() && isMatchingPhone(p.phone, actor.phone))
                    if (matches) {
                        matchedAsParty = true
                        p.copy(
                            uid = actor.uid,
                            verifiedPhone = p.verifiedPhone || effectivePhoneVerified,
                            biometricVerified = p.biometricVerified || biometricVerified,
                            approved = true,
                            credentialId = credentialId ?: p.credentialId,
                            signedAtMillis = nowMillis,
                            verificationMethod = cleanMethod,
                            signatureHash = signatureHex ?: p.signatureHash
                        )
                    } else {
                        p
                    }
                }

                val updatedWitnesses = latest.witnesses.map { w ->
                    val matches = (w.uid == actor.uid) ||
                        (actor.email.isNotBlank() && w.email.equals(actor.email, ignoreCase = true)) ||
                        (actor.phone.isNotBlank() && isMatchingPhone(w.phone, actor.phone))
                    if (matches) {
                        matchedAsWitness = true
                        w.copy(
                            uid = actor.uid,
                            verifiedPhone = w.verifiedPhone || effectivePhoneVerified,
                            biometricVerified = w.biometricVerified || biometricVerified,
                            approvedAtMillis = nowMillis,
                            credentialId = credentialId ?: w.credentialId,
                            verificationMethod = cleanMethod,
                            signatureHash = signatureHex ?: w.signatureHash
                        )
                    } else {
                        w
                    }
                }

                require(matchedAsParty || matchedAsWitness) {
                    "هويتك غير مدرجة ضمن قائمة الأطراف أو الشهود في هذا الميثاق."
                }

                val updatedUids = (latest.participantUids + actor.uid).distinct().take(10)
                val updatedVersions = latest.versions.map { ver ->
                    if (ver.versionId == latest.currentVersionId) {
                        ver.copy(approvedBy = (ver.approvedBy + actor.uid).distinct())
                    } else {
                        ver
                    }
                }.ifEmpty {
                    listOf(
                        AgreementVersion(
                            versionId = latest.currentVersionId,
                            hash = latest.contentHash,
                            createdAtMillis = nowMillis,
                            approvedBy = updatedUids
                        )
                    )
                }

                tx.update(
                    agreementRef,
                    mapOf(
                        "parties" to updatedParties.map { it.toMap() },
                        "witnesses" to updatedWitnesses.map { it.toMap() },
                        "participantUids" to updatedUids,
                        "versions" to updatedVersions.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val signerRole = if (matchedAsParty) "PARTY" else "WITNESS"
                val sigPayload = mutableMapOf<String, Any>(
                    "agreementId" to latest.id,
                    "userId" to actor.uid,
                    "signerName" to actor.name,
                    "role" to signerRole,
                    "versionId" to latest.currentVersionId,
                    "versionHash" to latest.contentHash,
                    "phoneVerified" to effectivePhoneVerified,
                    "biometricVerified" to biometricVerified,
                    "verificationMethod" to cleanMethod,
                    "ipHash" to ipHash,
                    "uaHash" to uaHash,
                    "participantUids" to updatedUids,
                    "participantEmails" to latest.participantEmails,
                    "participantPhones" to latest.participantPhones,
                    "signedAt" to FieldValue.serverTimestamp()
                )
                if (!credentialId.isNullOrBlank()) {
                    sigPayload["credentialId"] = credentialId
                }
                tx.set(sigRef, sigPayload)

                tx.set(
                    auditRef,
                    mapOf(
                        "agreementId" to latest.id,
                        "action" to "PARTY_DUAL_SIGNED",
                        "actorUid" to actor.uid,
                        "actorName" to actor.name,
                        "note" to "توقيع موثّق ($signerRole) عبر: $cleanMethod.",
                        "metadata" to mapOf(
                            "role" to signerRole,
                            "versionId" to latest.currentVersionId,
                            "versionHash" to latest.contentHash,
                            "phoneVerified" to effectivePhoneVerified,
                            "biometricVerified" to biometricVerified,
                            "verificationMethod" to cleanMethod,
                            "credentialId" to (credentialId ?: ""),
                            "ipHash" to ipHash,
                            "uaHash" to uaHash
                        ),
                        "snapshotHash" to latest.contentHash,
                        "participantUids" to updatedUids,
                        "participantEmails" to latest.participantEmails,
                        "participantPhones" to latest.participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                AgreementSignature(
                    id = sigRef.id,
                    agreementId = latest.id,
                    userId = actor.uid,
                    signerName = actor.name,
                    role = signerRole,
                    versionId = latest.currentVersionId,
                    versionHash = latest.contentHash,
                    phoneVerified = effectivePhoneVerified,
                    biometricVerified = biometricVerified,
                    credentialId = credentialId,
                    verificationMethod = cleanMethod,
                    ipHash = ipHash,
                    uaHash = uaHash,
                    participantUids = updatedUids,
                    participantEmails = latest.participantEmails,
                    participantPhones = latest.participantPhones
                )
            }.await()

            Result.success(recordedSig)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun approveAgreement(agreement: Agreement, note: String?): Result<Unit> {
        val docPath = "agreements/${agreement.id}"
        return try {
            val actor = requireActor()
            require(agreement.id.isNotBlank()) { "معرّف الميثاق غير صالح." }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val auditRef = agreementRef.collection("audit").document()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            db.runTransaction { tx ->
                val snap = tx.get(agreementRef)
                val latest = snap.toObject(
                    Agreement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("الميثاق غير موجود.")

                require(latest.statusEnum == AgreementStatus.PENDING) {
                    "هذا الميثاق ليس بانتظار الاعتماد النهائي."
                }
                require(isMember(latest, actor)) { "غير مصرح لك باعتماد هذا الميثاق." }

                val unsignedParties = latest.parties.filter { !it.approved }
                require(latest.parties.isNotEmpty() && unsignedParties.isEmpty()) {
                    "لا يمكن اعتماد الاتفاق نهائياً قبل توقيع جميع الأطراف. المتبقي: ${unsignedParties.joinToString("، ") { it.name }}."
                }

                val unsignedWitnesses = latest.witnesses.filter { it.approvedAtMillis == null }
                require(unsignedWitnesses.isEmpty()) {
                    "لا يمكن اعتماد الاتفاق قبل موافقة جميع الشهود المطلوبين (${unsignedWitnesses.joinToString("، ") { it.name }})."
                }

                val updatedUids = (latest.participantUids + actor.uid).distinct().take(10)
                val nowMillis = System.currentTimeMillis()
                val updatedVersions = latest.versions.map { ver ->
                    if (ver.versionId == latest.currentVersionId) {
                        ver.copy(approvedBy = (ver.approvedBy + updatedUids).distinct())
                    } else {
                        ver
                    }
                }.ifEmpty {
                    listOf(
                        AgreementVersion(
                            versionId = latest.currentVersionId,
                            hash = latest.contentHash,
                            createdAtMillis = nowMillis,
                            approvedBy = updatedUids
                        )
                    )
                }

                val approvedHash = AgreementHasher.computeCanonicalApprovedHash(latest)
                val approvedSnapshot = mapOf(
                    "agreementCode" to latest.agreementCode,
                    "title" to latest.title,
                    "type" to latest.type,
                    "description" to latest.description,
                    "amount" to latest.amount,
                    "currency" to latest.currency,
                    "paymentMethod" to latest.paymentMethod,
                    "startDate" to latest.startDate,
                    "dueDate" to latest.dueDate,
                    "rights" to latest.rights,
                    "obligations" to latest.obligations,
                    "specialTerms" to latest.specialTerms,
                    "parties" to latest.parties.map { it.toMap() },
                    "witnesses" to latest.witnesses.map { it.toMap() },
                    "contentHash" to latest.contentHash,
                    "approvedHash" to approvedHash,
                    "versionId" to latest.currentVersionId,
                    "finalizedByUid" to actor.uid
                )

                tx.update(
                    agreementRef,
                    mapOf(
                        "status" to AgreementStatus.APPROVED.name,
                        "parties" to latest.parties.map { it.toMap() },
                        "witnesses" to latest.witnesses.map { it.toMap() },
                        "participantUids" to updatedUids,
                        "versions" to updatedVersions.map { it.toMap() },
                        "approvedSnapshot" to approvedSnapshot,
                        "approvedHash" to approvedHash,
                        "lockedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    auditRef,
                    mapOf(
                        "agreementId" to latest.id,
                        "action" to "APPROVED_AND_LOCKED",
                        "actorUid" to actor.uid,
                        "actorName" to actor.name,
                        "note" to (cleanNote ?: "تم الاعتماد النهائي بإجماع الأطراف والشهود وقفل النسخة (${latest.currentVersionId})."),
                        "metadata" to mapOf(
                            "versionId" to latest.currentVersionId,
                            "approvedHash" to approvedHash,
                            "partiesCount" to latest.parties.size,
                            "witnessesCount" to latest.witnesses.size
                        ),
                        "snapshotHash" to latest.contentHash,
                        "participantUids" to updatedUids,
                        "participantEmails" to latest.participantEmails,
                        "participantPhones" to latest.participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun requestAmendment(
        agreement: Agreement,
        reason: String,
        proposedTitle: String,
        proposedDescription: String,
        proposedAmount: Double,
        proposedDueDate: String,
        proposedRights: List<String>,
        proposedObligations: List<String>,
        proposedSpecialTerms: List<String>
    ): Result<String> {
        var docPath = "agreements/${agreement.id}/amendments"
        return try {
            val actor = requireActor()
            require(agreement.statusEnum == AgreementStatus.APPROVED) {
                "تعديلات الإصدار متاحة فقط للمواثيق المعتمدة والمقفلة."
            }
            require(isMember(agreement, actor)) { "غير مصرح لك باقتراح تعديل لهذا الميثاق." }

            val cleanReason = reason.trim().take(500)
            val cleanTitle = proposedTitle.trim().take(160)
            val cleanDesc = proposedDescription.trim().take(5000)
            val cleanDue = proposedDueDate.trim().take(40)
            val cleanRights = proposedRights.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val cleanObligations = proposedObligations.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val cleanSpecial = proposedSpecialTerms.map { it.trim() }.filter { it.isNotEmpty() }.take(20)

            require(cleanReason.length >= 3) { "يرجى ذكر سبب التعديل (3 أحرف على الأقل)." }
            require(cleanTitle.length >= 3) { "العنوان المقترح يجب أن يكون 3 أحرف على الأقل." }
            require(cleanDesc.length >= 10) { "الوصف المقترح يجب أن يكون 10 أحرف على الأقل." }
            require(proposedAmount in 0.0..1_000_000_000.0) { "المبلغ المقترح غير صالح." }

            val diffs = AgreementHasher.computeAmendmentDiff(
                current = agreement,
                proposedTitle = cleanTitle,
                proposedDescription = cleanDesc,
                proposedAmount = proposedAmount,
                proposedDueDate = cleanDue,
                proposedRights = cleanRights,
                proposedObligations = cleanObligations,
                proposedSpecialTerms = cleanSpecial
            )
            require(diffs.isNotEmpty()) { "لم يتم تغيير أي بند عن النسخة المعتمدة الحالية." }

            val requiredKeys = agreement.parties.mapIndexed { idx, p -> "p$idx:${p.canonicalKey()}" }
            val proposerPartyIdx = agreement.parties.indexOfFirst { p ->
                (p.uid == actor.uid) ||
                    (actor.email.isNotBlank() && p.email.equals(actor.email, ignoreCase = true)) ||
                    (actor.phone.isNotBlank() && isMatchingPhone(p.phone, actor.phone))
            }
            val initialApprovedKeys = if (proposerPartyIdx >= 0 && requiredKeys.size > 1) {
                listOf(requiredKeys[proposerPartyIdx])
            } else {
                emptyList()
            }
            val initialApprovedUids = if (initialApprovedKeys.isNotEmpty()) listOf(actor.uid) else emptyList()

            val (uids, emails, phones) = buildUpdatedParticipantFields(agreement, actor)
            val agreementRef = db.collection("agreements").document(agreement.id)
            val amendRef = agreementRef.collection("amendments").document()
            val auditRef = agreementRef.collection("audit").document()
            docPath = amendRef.path

            val batch = db.batch()
            batch.set(
                amendRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "baseVersionId" to agreement.currentVersionId,
                    "proposedByUid" to actor.uid,
                    "proposedByName" to actor.name,
                    "reason" to cleanReason,
                    "proposedTitle" to cleanTitle,
                    "proposedDescription" to cleanDesc,
                    "proposedAmount" to proposedAmount,
                    "proposedDueDate" to cleanDue,
                    "proposedRights" to cleanRights,
                    "proposedObligations" to cleanObligations,
                    "proposedSpecialTerms" to cleanSpecial,
                    "diffItems" to diffs.take(10).map { it.toMap() },
                    "requiredPartyKeys" to requiredKeys,
                    "approvedByUids" to initialApprovedUids,
                    "approvedPartyKeys" to initialApprovedKeys,
                    "status" to AmendmentStatus.PENDING.name,
                    "participantUids" to uids,
                    "participantEmails" to emails,
                    "participantPhones" to phones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "AMENDMENT_REQUESTED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "اقتراح تعديل جديد على النسخة ${agreement.currentVersionId}: $cleanReason",
                    "metadata" to mapOf(
                        "amendmentId" to amendRef.id,
                        "baseVersionId" to agreement.currentVersionId,
                        "changesCount" to diffs.size
                    ),
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to uids,
                    "participantEmails" to emails,
                    "participantPhones" to phones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(amendRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun respondToAmendment(
        agreement: Agreement,
        amendment: AgreementAmendment,
        approve: Boolean,
        note: String?
    ): Result<Boolean> {
        val docPath = "agreements/${agreement.id}/amendments/${amendment.id}"
        return try {
            val actor = requireActor()
            require(isMember(agreement, actor)) { "غير مصرح لك بالرد على هذا التعديل." }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val amendRef = agreementRef.collection("amendments").document(amendment.id)
            val auditRef = agreementRef.collection("audit").document()
            val cleanNote = note?.trim()?.take(500).orEmpty()

            val appliedNewVersion = db.runTransaction { tx ->
                val amendSnap = tx.get(amendRef)
                val latestAmend = amendSnap.toObject(
                    AgreementAmendment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("طلب التعديل غير موجود.")

                require(latestAmend.statusEnum == AmendmentStatus.PENDING) {
                    "تم حسم طلب التعديل هذا مسبقاً."
                }

                val agSnap = tx.get(agreementRef)
                val latestAg = agSnap.toObject(
                    Agreement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("الميثاق غير موجود.")

                require(latestAg.statusEnum == AgreementStatus.APPROVED) {
                    "الميثاق لم يعد في حالة معتمدة."
                }

                if (!approve) {
                    tx.update(
                        amendRef,
                        mapOf(
                            "status" to AmendmentStatus.REJECTED.name,
                            "rejectedByUid" to actor.uid,
                            "rejectionNote" to cleanNote.ifBlank { "تم رفض التعديل المقترح من أحد الأطراف." },
                            "resolvedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.set(
                        auditRef,
                        mapOf(
                            "agreementId" to latestAg.id,
                            "action" to "AMENDMENT_REJECTED",
                            "actorUid" to actor.uid,
                            "actorName" to actor.name,
                            "note" to (cleanNote.ifBlank { "رفض طلب التعديل المقترح على النسخة ${latestAg.currentVersionId}." }),
                            "metadata" to mapOf("amendmentId" to amendment.id),
                            "snapshotHash" to latestAg.contentHash,
                            "participantUids" to latestAg.participantUids,
                            "participantEmails" to latestAg.participantEmails,
                            "participantPhones" to latestAg.participantPhones,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    return@runTransaction false
                }

                val partyIdx = latestAg.parties.indexOfFirst { p ->
                    (p.uid == actor.uid) ||
                        (actor.email.isNotBlank() && p.email.equals(actor.email, ignoreCase = true)) ||
                        (actor.phone.isNotBlank() && isMatchingPhone(p.phone, actor.phone))
                }
                require(partyIdx >= 0) { "فقط أطراف الميثاق يمكنهم التصويت على اعتماد التعديل." }

                val partyKey = latestAmend.requiredPartyKeys.getOrNull(partyIdx)
                    ?: "p$partyIdx:${latestAg.parties[partyIdx].canonicalKey()}"

                val updatedApprovedKeys = (latestAmend.approvedPartyKeys + partyKey).distinct()
                val updatedApprovedUids = (latestAmend.approvedByUids + actor.uid).distinct()
                val isUnanimous = updatedApprovedKeys.size >= latestAg.parties.size

                if (!isUnanimous) {
                    tx.update(
                        amendRef,
                        mapOf(
                            "approvedByUids" to updatedApprovedUids,
                            "approvedPartyKeys" to updatedApprovedKeys
                        )
                    )
                    tx.set(
                        auditRef,
                        mapOf(
                            "agreementId" to latestAg.id,
                            "action" to "AMENDMENT_PARTY_APPROVED",
                            "actorUid" to actor.uid,
                            "actorName" to actor.name,
                            "note" to "موافقة طرف على التعديل (${updatedApprovedKeys.size}/${latestAg.parties.size}). بانتظار بقية الأطراف.",
                            "metadata" to mapOf(
                                "amendmentId" to amendment.id,
                                "approvalsCount" to updatedApprovedKeys.size,
                                "requiredCount" to latestAg.parties.size
                            ),
                            "snapshotHash" to latestAg.contentHash,
                            "participantUids" to latestAg.participantUids,
                            "participantEmails" to latestAg.participantEmails,
                            "participantPhones" to latestAg.participantPhones,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    false
                } else {
                    val nextVersionNum = latestAg.versions.size + 1
                    val nextVersionId = "v$nextVersionNum"
                    val newContentHash = AgreementHasher.computeContentHash(
                        agreementCode = latestAg.agreementCode,
                        title = latestAmend.proposedTitle,
                        type = latestAg.type,
                        description = latestAmend.proposedDescription,
                        amount = latestAmend.proposedAmount,
                        currency = latestAg.currency,
                        paymentMethod = latestAg.paymentMethod,
                        startDate = latestAg.startDate,
                        dueDate = latestAmend.proposedDueDate,
                        rights = latestAmend.proposedRights,
                        obligations = latestAmend.proposedObligations,
                        specialTerms = latestAmend.proposedSpecialTerms,
                        ownerId = latestAg.ownerId,
                        participantEmails = latestAg.participantEmails
                    )
                    val newApprovedHash = AgreementHasher.computeApprovedSnapshotHash(
                        agreementCode = latestAg.agreementCode,
                        versionId = nextVersionId,
                        contentHash = newContentHash,
                        title = latestAmend.proposedTitle,
                        type = latestAg.type,
                        description = latestAmend.proposedDescription,
                        amount = latestAmend.proposedAmount,
                        currency = latestAg.currency,
                        paymentMethod = latestAg.paymentMethod,
                        startDate = latestAg.startDate,
                        dueDate = latestAmend.proposedDueDate,
                        rights = latestAmend.proposedRights,
                        obligations = latestAmend.proposedObligations,
                        specialTerms = latestAmend.proposedSpecialTerms,
                        parties = latestAg.parties,
                        witnesses = latestAg.witnesses
                    )
                    val newVersion = AgreementVersion(
                        versionId = nextVersionId,
                        hash = newContentHash,
                        createdAtMillis = System.currentTimeMillis(),
                        approvedBy = updatedApprovedUids
                    )
                    val updatedVersions = (latestAg.versions + newVersion).takeLast(20)
                    val newSnapshot = mapOf(
                        "agreementCode" to latestAg.agreementCode,
                        "title" to latestAmend.proposedTitle,
                        "type" to latestAg.type,
                        "description" to latestAmend.proposedDescription,
                        "amount" to latestAmend.proposedAmount,
                        "currency" to latestAg.currency,
                        "paymentMethod" to latestAg.paymentMethod,
                        "startDate" to latestAg.startDate,
                        "dueDate" to latestAmend.proposedDueDate,
                        "rights" to latestAmend.proposedRights,
                        "obligations" to latestAmend.proposedObligations,
                        "specialTerms" to latestAmend.proposedSpecialTerms,
                        "contentHash" to newContentHash,
                        "approvedHash" to newApprovedHash,
                        "versionId" to nextVersionId,
                        "amendmentId" to amendment.id
                    )

                    tx.update(
                        amendRef,
                        mapOf(
                            "approvedByUids" to updatedApprovedUids,
                            "approvedPartyKeys" to updatedApprovedKeys,
                            "status" to AmendmentStatus.APPROVED_APPLIED.name,
                            "appliedVersionId" to nextVersionId,
                            "resolvedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.update(
                        agreementRef,
                        mapOf(
                            "title" to latestAmend.proposedTitle,
                            "description" to latestAmend.proposedDescription,
                            "amount" to latestAmend.proposedAmount,
                            "dueDate" to latestAmend.proposedDueDate,
                            "rights" to latestAmend.proposedRights,
                            "obligations" to latestAmend.proposedObligations,
                            "specialTerms" to latestAmend.proposedSpecialTerms,
                            "currentVersionId" to nextVersionId,
                            "versions" to updatedVersions.map { it.toMap() },
                            "contentHash" to newContentHash,
                            "approvedHash" to newApprovedHash,
                            "approvedSnapshot" to newSnapshot,
                            "lastAppliedAmendmentId" to amendment.id,
                            "lockedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.set(
                        auditRef,
                        mapOf(
                            "agreementId" to latestAg.id,
                            "action" to "AMENDMENT_APPLIED_V_NEXT",
                            "actorUid" to actor.uid,
                            "actorName" to actor.name,
                            "note" to "اعتماد التعديل بالإجماع وإنشاء النسخة الجديدة $nextVersionId مع قفلها رقمياً.",
                            "metadata" to mapOf(
                                "amendmentId" to amendment.id,
                                "newVersionId" to nextVersionId,
                                "approvedHash" to newApprovedHash
                            ),
                            "snapshotHash" to newContentHash,
                            "participantUids" to latestAg.participantUids,
                            "participantEmails" to latestAg.participantEmails,
                            "participantPhones" to latestAg.participantPhones,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }
            }.await()

            Result.success(appliedNewVersion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        agreement: Agreement,
        amount: Double,
        method: PaymentMethodType,
        receiptReference: String,
        paymentDateText: String,
        attachment: AgreementAttachment?
    ): Result<String> {
        var docPath = "agreements/${agreement.id}/payments"
        return try {
            val actor = requireActor()
            require(isMember(agreement, actor)) { "غير مصرح لك بتسجيل دفعات على هذا الميثاق." }
            require(amount > 0.0 && amount <= 1_000_000_000.0) {
                "يرجى إدخال مبلغ دفعة صحيح أكبر من الصفر."
            }
            val cleanReceipt = receiptReference.trim().take(300)
            require(cleanReceipt.isNotBlank()) { "يرجى إدخال رقم المرجع أو الإيصال." }

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val cleanDate = paymentDateText.trim().ifBlank { todayStr }.take(40)
            val (uids, emails, phones) = buildUpdatedParticipantFields(agreement, actor)

            val agreementRef = db.collection("agreements").document(agreement.id)
            val payRef = agreementRef.collection("payments").document()
            val auditRef = agreementRef.collection("audit").document()
            docPath = payRef.path

            val payload = mutableMapOf<String, Any>(
                "agreementId" to agreement.id,
                "recordedBy" to actor.uid,
                "amount" to amount,
                "confirmedAmount" to 0.0,
                "paymentDateText" to cleanDate,
                "paidAt" to FieldValue.serverTimestamp(),
                "method" to method.name,
                "receiptReference" to cleanReceipt,
                "confirmedBy" to listOf(actor.uid),
                "status" to PaymentStatus.PENDING_CONFIRMATION.name,
                "participantUids" to uids,
                "participantEmails" to emails,
                "participantPhones" to phones,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (attachment != null) {
                payload["attachment"] = attachment.toMap()
            }

            val batch = db.batch()
            batch.set(payRef, payload)
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "PAYMENT_RECORDED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "تسجيل دفعة بمبلغ $amount (${method.arabicLabel}) بتاريخ $cleanDate — مرجع: $cleanReceipt",
                    "metadata" to mapOf(
                        "paymentId" to payRef.id,
                        "amount" to amount,
                        "method" to method.name,
                        "paymentDate" to cleanDate,
                        "receiptReference" to cleanReceipt,
                        "hasAttachment" to (attachment != null)
                    ),
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to uids,
                    "participantEmails" to emails,
                    "participantPhones" to phones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(payRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun confirmPayment(
        agreement: Agreement,
        payment: AgreementPayment,
        mode: PaymentConfirmationMode,
        confirmedAmountInput: Double,
        note: String?,
        witnessOtpChallengeId: String? = null
    ): Result<Unit> {
        val docPath = "agreements/${agreement.id}/payments/${payment.id}"
        return try {
            val actor = requireActor()
            require(isMember(agreement, actor)) { "غير مصرح لك بتأكيد هذه الدفعة." }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val payRef = agreementRef.collection("payments").document(payment.id)
            val auditRef = agreementRef.collection("audit").document()
            val cleanNote = note?.trim()?.take(500).orEmpty()

            db.runTransaction { tx ->
                val paySnap = tx.get(payRef)
                val latestPayment = paySnap.toObject(
                    AgreementPayment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("سجل الدفعة غير موجود.")

                require(latestPayment.statusEnum == PaymentStatus.PENDING_CONFIRMATION) {
                    "هذه الدفعة مؤكدة أو محسومة مسبقاً ولا يمكن تعديلها مطلقاً."
                }

                val agSnap = tx.get(agreementRef)
                val latestAgreement = agSnap.toObject(
                    Agreement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("الميثاق غير موجود.")

                val isDistinctRecipient = actor.uid != latestPayment.recordedBy
                var witnessProofVerified = false
                var witnessUidUsed: String? = null

                if (!witnessOtpChallengeId.isNullOrBlank()) {
                    val otpRef = agreementRef.collection("approvals").document(witnessOtpChallengeId)
                    val otpSnap = tx.get(otpRef)
                    val otpRecord = otpSnap.toObject(
                        OtpChallengeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (otpRecord != null && otpRecord.verified && otpRecord.used) {
                        val isWitness = latestAgreement.witnesses.any { w ->
                            w.uid == actor.uid ||
                                (actor.email.isNotBlank() && w.email.equals(actor.email, ignoreCase = true)) ||
                                (actor.phone.isNotBlank() && isMatchingPhone(w.phone, actor.phone))
                        }
                        if (isWitness) {
                            witnessProofVerified = true
                            witnessUidUsed = actor.uid
                        }
                    }
                }

                require(isDistinctRecipient || witnessProofVerified) {
                    "لا يمكنك تأكيد استلام دفعة سجلتها بنفسك. يلزم تأكيد الطرف المستلم الآخر أو شاهد موثّق برمز OTP."
                }

                val (finalStatus, finalAmount) = when (mode) {
                    PaymentConfirmationMode.FULL -> PaymentStatus.CONFIRMED to latestPayment.amount
                    PaymentConfirmationMode.PARTIAL -> {
                        require(confirmedAmountInput > 0.0 && confirmedAmountInput < latestPayment.amount) {
                            "في الاستلام الجزئي، يجب أن يكون المبلغ المؤكد أكبر من 0 وأقل من ${latestPayment.amount}."
                        }
                        PaymentStatus.PARTIAL_CONFIRMED to confirmedAmountInput
                    }
                    PaymentConfirmationMode.NOT_RECEIVED -> PaymentStatus.NOT_RECEIVED to 0.0
                }

                val updatedConfirmed = (latestPayment.confirmedBy + actor.uid).distinct().take(10)
                val payUpdate = mutableMapOf<String, Any>(
                    "confirmedBy" to updatedConfirmed,
                    "confirmedAmount" to finalAmount,
                    "confirmationMode" to mode.name,
                    "status" to finalStatus.name
                )
                if (cleanNote.isNotBlank()) {
                    payUpdate["confirmationNote"] = cleanNote
                }
                if (witnessProofVerified && witnessUidUsed != null) {
                    payUpdate["witnessUid"] = witnessUidUsed
                    payUpdate["witnessOtpVerified"] = true
                }

                tx.update(payRef, payUpdate)

                if (finalAmount > 0.0) {
                    val newPaidTotal = latestAgreement.paidTotal + finalAmount
                    tx.update(
                        agreementRef,
                        mapOf(
                            "paidTotal" to newPaidTotal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.set(
                    auditRef,
                    mapOf(
                        "agreementId" to latestAgreement.id,
                        "action" to "PAYMENT_${mode.name}",
                        "actorUid" to actor.uid,
                        "actorName" to actor.name,
                        "note" to "توثيق حالة الدفعة (${mode.arabicLabel}) بمبلغ مؤكد $finalAmount — مرجع: ${latestPayment.receiptReference}.",
                        "metadata" to mapOf(
                            "paymentId" to payment.id,
                            "mode" to mode.name,
                            "recordedAmount" to latestPayment.amount,
                            "confirmedAmount" to finalAmount,
                            "witnessProof" to witnessProofVerified
                        ),
                        "snapshotHash" to latestAgreement.contentHash,
                        "participantUids" to latestAgreement.participantUids,
                        "participantEmails" to latestAgreement.participantEmails,
                        "participantPhones" to latestAgreement.participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
