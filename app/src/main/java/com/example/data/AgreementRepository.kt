package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.example.crypto.AgreementHasher
import com.example.model.Agreement
import com.example.model.AgreementAmendment
import com.example.model.AgreementDispute
import com.example.model.AgreementPayment
import com.example.model.AgreementSignature
import com.example.model.AgreementStatus
import com.example.model.AgreementType
import com.example.model.AgreementVersion
import com.example.model.AuditLog
import com.example.model.CurrencyCode
import com.example.model.DisputeReply
import com.example.model.DisputeStatus
import com.example.model.Invitation
import com.example.model.InvitationPurpose
import com.example.model.OtpChallengeRecord
import com.example.model.Party
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.example.model.ThemePreference
import com.example.model.UserProfile
import com.example.model.UserSession
import com.example.model.VaultDocument
import com.example.model.Witness
import com.example.model.AgreementAttachment
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PublicInvitationLookup {
    data class Valid(val invitation: Invitation) : PublicInvitationLookup
    data class Expired(val invitation: Invitation) : PublicInvitationLookup
    data class AlreadyUsed(val invitation: Invitation) : PublicInvitationLookup
    data object InvalidToken : PublicInvitationLookup
    data object NotFound : PublicInvitationLookup
    data class Error(val message: String) : PublicInvitationLookup
}

class AgreementRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val workflows = AgreementSecurityWorkflows(db, auth)

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private data class ActorIdentity(
        val uid: String,
        val email: String,
        val phone: String,
        val name: String
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("يجب تسجيل الدخول أولاً.")
        }
        return uid
    }

    private fun requireActorIdentity(): ActorIdentity {
        val user = auth.currentUser ?: throw IllegalStateException("يجب تسجيل الدخول أولاً.")
        val email = user.email?.trim()?.lowercase(Locale.US).orEmpty()
        val phone = user.phoneNumber?.trim().orEmpty()
        val name = user.displayName?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(120)
            ?: email.substringBefore("@").takeIf { it.isNotBlank() }
            ?: phone.takeIf { it.isNotBlank() }
            ?: "مستخدم ميثاق"
        return ActorIdentity(
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

    private fun isMemberIdentity(agreement: Agreement, actor: ActorIdentity): Boolean {
        return agreement.ownerId == actor.uid ||
            (actor.uid in agreement.participantUids) ||
            (actor.email.isNotBlank() && agreement.participantEmails.any { it.equals(actor.email, ignoreCase = true) }) ||
            (actor.phone.isNotBlank() && agreement.participantPhones.any { isMatchingPhone(it, actor.phone) })
    }

    private fun unwrapAndLogFirestoreError(
        error: Throwable,
        opType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, opType, path)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeVaultDocuments(userId: String): Flow<List<VaultDocument>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/vault"
        emitAll(
            db.collection("users").document(userId).collection("vault")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VaultDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<UserSession>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastSeenAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeInvitations(
        userId: String,
        userEmail: String,
        userPhone: String = auth.currentUser?.phoneNumber.orEmpty()
    ): Flow<List<Invitation>> {
        val normalizedEmail = userEmail.trim().lowercase(Locale.US)
        val normalizedPhone = userPhone.trim()
        val sentFlow = flow {
            val path = "invitations"
            emitAll(
                db.collection(path)
                    .whereEqualTo("inviterUid", userId)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            Invitation::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                        throw error
                    }
            )
        }
        val flows = mutableListOf(sentFlow)
        if (normalizedEmail.isNotBlank()) {
            flows.add(flow {
                val path = "invitations"
                emitAll(
                    db.collection(path)
                        .whereEqualTo("inviteeEmail", normalizedEmail)
                        .snapshots()
                        .map { snapshot ->
                            snapshot.toObjects(
                                Invitation::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .catch { error ->
                            unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                            throw error
                        }
                )
            })
        }
        if (normalizedPhone.isNotBlank()) {
            flows.add(flow {
                val path = "invitations"
                emitAll(
                    db.collection(path)
                        .whereEqualTo("inviteePhone", normalizedPhone)
                        .snapshots()
                        .map { snapshot ->
                            snapshot.toObjects(
                                Invitation::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .catch { error ->
                            unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                            throw error
                        }
                )
            })
        }
        return combine(flows) { lists ->
            val merged = LinkedHashMap<String, Invitation>()
            lists.forEach { list -> list.forEach { merged[it.id] = it } }
            merged.values.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    data class SessionInitResult(
        val sessionId: String,
        val isNewDevice: Boolean,
        val deviceLabel: String
    )

    suspend fun ensureUserProfileAndSession(appContext: Context): Result<SessionInitResult> {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val validUid = requireUserId()
            val email = currentUser?.email?.trim()?.lowercase(Locale.US).orEmpty()
            val (pendingName, pendingPhone) = AuthRateLimiter.consumePendingSignupInfo(appContext)
            val authPhone = currentUser?.phoneNumber?.trim()?.take(30).orEmpty()
            val finalPhone = authPhone.ifBlank { pendingPhone.orEmpty() }.take(30)
            val phoneIsVerified = authPhone.isNotBlank()
            val fullName = pendingName
                ?.takeIf { it.isNotBlank() }
                ?.take(120)
                ?: currentUser?.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
                ?: email.substringBefore("@").ifEmpty { finalPhone.ifEmpty { "مستخدم ميثاق" } }.take(120)
            val photoUrl = currentUser?.photoUrl?.toString()?.take(500)

            val docRef = db.collection("users").document(validUid)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to validUid,
                    "fullName" to fullName,
                    "email" to email,
                    "phone" to finalPhone,
                    "phoneVerified" to phoneIsVerified,
                    "preferredTheme" to ThemePreference.SYSTEM.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!photoUrl.isNullOrBlank()) {
                    payload["photoURL"] = photoUrl
                }
                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (pendingName != null) {
                    updatePayload["fullName"] = pendingName.take(120)
                }
                if (email.isNotBlank()) {
                    updatePayload["email"] = email
                }
                if (authPhone.isNotBlank()) {
                    updatePayload["phone"] = authPhone
                    updatePayload["phoneVerified"] = true
                } else if (!pendingPhone.isNullOrBlank()) {
                    updatePayload["phone"] = pendingPhone.take(30)
                }
                if (!photoUrl.isNullOrBlank()) {
                    updatePayload["photoURL"] = photoUrl
                }
                docRef.update(updatePayload).await()
            }

            val sessionResult = registerCurrentDeviceSession(appContext, validUid)
            Result.success(sessionResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun registerCurrentDeviceSession(
        appContext: Context,
        uid: String
    ): SessionInitResult {
        val device = AuthRateLimiter.getDeviceLabel()
        val userAgent = AuthRateLimiter.getUserAgent()
        val ipHash = AuthRateLimiter.computeNetworkIpHash(appContext)
        val sessionId = AuthRateLimiter.computeDeviceSessionId(appContext, uid)
        val sessionsCol = db.collection("users").document(uid).collection("sessions")
        val sessionRef = sessionsCol.document(sessionId)

        val existing = sessionRef.get().await()
        val isNewDevice: Boolean
        if (!existing.exists()) {
            val existingSessionsCount = try {
                sessionsCol.limit(2).get().await().size()
            } catch (_: Exception) {
                0
            }
            isNewDevice = existingSessionsCount > 0
            sessionRef.set(
                mapOf(
                    "userId" to uid,
                    "device" to device,
                    "userAgent" to userAgent,
                    "ipHash" to ipHash,
                    "revoked" to false,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            isNewDevice = false
            sessionRef.update(
                mapOf(
                    "device" to device,
                    "userAgent" to userAgent,
                    "revoked" to false,
                    "lastSeenAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        return SessionInitResult(
            sessionId = sessionId,
            isNewDevice = isNewDevice,
            deviceLabel = device
        )
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val validUid = requireUserId()
            db.collection("users").document(validUid)
                .collection("sessions").document(sessionId)
                .update(
                    mapOf(
                        "revoked" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp()
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

    suspend fun revokeAllSessions(): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/sessions"
        return try {
            val validUid = requireUserId()
            val snap = db.collection("users").document(validUid)
                .collection("sessions")
                .get()
                .await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "revoked" to true,
                            "lastSeenAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        fullName: String,
        phone: String,
        photoURL: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val validUid = requireUserId()
            val cleanName = fullName.trim().take(120)
            val cleanPhone = phone.trim().take(30)
            require(cleanName.length >= 2) { "يرجى إدخال الاسم الكامل (حرفين على الأقل)." }
            val payload = mutableMapOf<String, Any>(
                "fullName" to cleanName,
                "phone" to cleanPhone,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoURL.isNullOrBlank() && photoURL.startsWith("https://")) {
                payload["photoURL"] = photoURL.take(500)
            }
            db.collection("users").document(validUid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markPhoneVerified(phone: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val validUid = requireUserId()
            val cleanPhone = phone.trim().take(30)
            require(cleanPhone.length >= 6) { "يرجى إدخال رقم هاتف صحيح." }
            db.collection("users").document(validUid)
                .update(
                    mapOf(
                        "phone" to cleanPhone,
                        "phoneVerified" to true,
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

    suspend fun updateUserPhone(phone: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val validUid = requireUserId()
            val cleanPhone = phone.trim().take(30)
            require(cleanPhone.length >= 6) { "يرجى إدخال رقم جوال صحيح." }
            db.collection("users").document(validUid)
                .update(
                    mapOf(
                        "phone" to cleanPhone,
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

    suspend fun updateThemePreference(preference: ThemePreference): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val validUid = requireUserId()
            db.collection("users").document(validUid)
                .update(
                    mapOf(
                        "preferredTheme" to preference.name,
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

    suspend fun addVaultDocument(
        title: String,
        category: String,
        referenceCode: String,
        notes: String?
    ): Result<String> {
        var path = "users/vault"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanCategory = category.trim()
            val cleanRef = referenceCode.trim()
            val cleanNotes = notes?.trim()?.take(2000).orEmpty()

            require(cleanTitle.length in 2..160) { "عنوان الوثيقة يجب أن يكون بين حرفين و 160 حرفاً." }
            require(cleanCategory.length in 2..60) { "التصنيف غير صالح." }
            require(cleanRef.length in 2..64) { "الرمز المرجعي يجب أن يكون بين حرفين و 64 حرفاً." }

            val hash = AgreementHasher.computeSha256("$uid|$cleanTitle|$cleanCategory|$cleanRef|$cleanNotes")
            val docRef = db.collection("users").document(uid).collection("vault").document()
            path = docRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to cleanTitle,
                "category" to cleanCategory,
                "referenceCode" to cleanRef,
                "sha256Hash" to hash,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanNotes.isNotBlank()) {
                payload["notes"] = cleanNotes
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeAllAgreements(
        userId: String,
        userEmail: String,
        userPhone: String = auth.currentUser?.phoneNumber.orEmpty()
    ): Flow<List<Agreement>> {
        val normalizedEmail = userEmail.trim().lowercase(Locale.US)
        val normalizedPhone = userPhone.trim()
        val flows = mutableListOf(observeByParticipantUid(userId))
        if (normalizedEmail.isNotBlank()) {
            flows.add(observeByParticipantEmail(userId, normalizedEmail))
        }
        if (normalizedPhone.isNotBlank()) {
            flows.add(observeByParticipantPhone(userId, normalizedPhone))
        }
        return combine(flows) { lists ->
            val merged = LinkedHashMap<String, Agreement>()
            lists.forEach { list -> list.forEach { merged[it.id] = it } }
            merged.values
                .filter { !it.archived }
                .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
    }

    private fun observeByParticipantUid(userId: String): Flow<List<Agreement>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "agreements"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Agreement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun observeByParticipantEmail(userId: String, normalizedEmail: String): Flow<List<Agreement>> = flow {
        require(userId.isNotBlank() && normalizedEmail.isNotBlank()) {
            "User ID and email must not be blank"
        }
        val path = "agreements"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantEmails", normalizedEmail)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Agreement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun observeByParticipantPhone(userId: String, normalizedPhone: String): Flow<List<Agreement>> = flow {
        require(userId.isNotBlank() && normalizedPhone.isNotBlank()) {
            "User ID and phone must not be blank"
        }
        val path = "agreements"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantPhones", normalizedPhone)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Agreement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementById(userId: String, agreementId: String): Flow<Agreement?> = flow {
        require(userId.isNotBlank() && agreementId.isNotBlank()) {
            "User ID and agreement ID must not be blank"
        }
        val path = "agreements/$agreementId"
        emitAll(
            db.collection("agreements").document(agreementId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        Agreement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    private fun selectSubcollectionQuery(
        col: com.google.firebase.firestore.CollectionReference,
        userId: String,
        agreement: Agreement
    ): com.google.firebase.firestore.Query {
        val userEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.US).orEmpty()
        val userPhone = auth.currentUser?.phoneNumber?.trim().orEmpty()
        return when {
            userId in agreement.participantUids -> col.whereArrayContains("participantUids", userId)
            userEmail.isNotBlank() && agreement.participantEmails.any { it.equals(userEmail, ignoreCase = true) } ->
                col.whereArrayContains("participantEmails", userEmail)
            userPhone.isNotBlank() && agreement.participantPhones.any { it == userPhone } ->
                col.whereArrayContains("participantPhones", userPhone)
            userEmail.isNotBlank() -> col.whereArrayContains("participantEmails", userEmail)
            else -> col.whereArrayContains("participantUids", userId)
        }
    }

    fun observeAgreementPayments(userId: String, agreement: Agreement): Flow<List<AgreementPayment>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/payments"
        val col = db.collection("agreements").document(agreement.id).collection("payments")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgreementPayment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: it.paidAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementDisputes(userId: String, agreement: Agreement): Flow<List<AgreementDispute>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/disputes"
        val col = db.collection("agreements").document(agreement.id).collection("disputes")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgreementDispute::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementAuditLogs(userId: String, agreement: Agreement): Flow<List<AuditLog>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/audit"
        val col = db.collection("agreements").document(agreement.id).collection("audit")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementSignatures(userId: String, agreement: Agreement): Flow<List<AgreementSignature>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/signatures"
        val col = db.collection("agreements").document(agreement.id).collection("signatures")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgreementSignature::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.signedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementAmendments(userId: String, agreement: Agreement): Flow<List<AgreementAmendment>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/amendments"
        val col = db.collection("agreements").document(agreement.id).collection("amendments")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgreementAmendment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAgreementApprovals(userId: String, agreement: Agreement): Flow<List<OtpChallengeRecord>> = flow {
        require(userId.isNotBlank() && agreement.id.isNotBlank())
        val path = "agreements/${agreement.id}/approvals"
        val col = db.collection("agreements").document(agreement.id).collection("approvals")
        val query = selectSubcollectionQuery(col, userId, agreement)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OtpChallengeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLogFirestoreError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun formatSummaryAmount(amount: Double, currency: CurrencyCode): String {
        if (currency == CurrencyCode.NONE || amount <= 0.0) return "بدون مقابل مالي"
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        }
        return "${formatter.format(amount)} ${currency.symbol}"
    }

    private fun buildInvitationPayload(
        agreementId: String,
        agreementCode: String,
        agreementTitle: String,
        type: AgreementType,
        amount: Double,
        currency: CurrencyCode,
        inviterUid: String,
        inviterName: String,
        inviteePhone: String,
        inviteeEmail: String,
        purpose: InvitationPurpose,
        expiresAt: Timestamp
    ): Map<String, Any> {
        val last3 = inviteePhone.filter { it.isDigit() }.takeLast(3)
        val summary = "${type.arabicLabel} • ${formatSummaryAmount(amount, currency)}".take(300)
        return mapOf(
            "agreementId" to agreementId,
            "agreementCode" to agreementCode,
            "agreementTitle" to agreementTitle,
            "agreementSummary" to summary,
            "inviterUid" to inviterUid,
            "inviterName" to inviterName,
            "inviteePhone" to inviteePhone,
            "inviteePhoneLast3" to last3,
            "inviteeEmail" to inviteeEmail,
            "purpose" to purpose.name,
            "expiresAt" to expiresAt,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun createOrUpdateAgreement(
        existingAgreement: Agreement?,
        title: String,
        type: AgreementType,
        description: String,
        amount: Double,
        currency: CurrencyCode,
        paymentMethod: PaymentMethodType,
        agreementDate: String = "",
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        invitedParties: List<Party>,
        witnesses: List<Witness>,
        attachments: List<AgreementAttachment> = emptyList(),
        submitAsPending: Boolean
    ): Result<String> {
        var docPath = "agreements"
        return try {
            val actor = requireActorIdentity()
            val uid = actor.uid
            val ownerEmail = actor.email
            val ownerPhone = actor.phone
            val ownerName = actor.name

            require(ownerEmail.isNotBlank() || ownerPhone.isNotBlank()) {
                "يتطلب إنشاء الميثاق حساباً موثّقاً بالبريد الإلكتروني أو برقم الهاتف."
            }

            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val cleanAgreementDate = agreementDate.trim().ifBlank {
                existingAgreement?.agreementDate?.takeIf { it.isNotBlank() } ?: todayStr
            }.take(40)
            val cleanStartDate = startDate.trim().take(40)
            val cleanDueDate = dueDate.trim().take(40)
            val cleanRights = rights.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val cleanObligations = obligations.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val cleanSpecialTerms = specialTerms.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
            val cleanAttachments = attachments.take(10)

            require(cleanTitle.length in 3..160) {
                "عنوان الميثاق يجب أن يكون بين 3 و 160 حرفاً."
            }
            require(cleanDesc.length in 10..5000) {
                "وصف الميثاق وبنوده الأساسية يجب أن يكون بين 10 و 5000 حرف."
            }
            require(amount in 0.0..1_000_000_000.0) {
                "المبلغ المالي خارج النطاق المسموح."
            }

            val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
            val phoneRegex = Regex("^\\+?[0-9]{7,15}$")

            val sanitizedParties = invitedParties.map { p ->
                p.copy(
                    name = p.name.trim().take(120),
                    email = p.email.trim().lowercase(Locale.US).take(150),
                    phone = p.phone.trim().take(30),
                    role = p.role.trim().ifBlank { "طرف مشارك" }.take(60)
                )
            }.filter { it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank()) }

            require(sanitizedParties.isNotEmpty()) {
                "يجب إضافة طرف آخر واحد على الأقل مع رقم الهاتف أو البريد الإلكتروني."
            }
            sanitizedParties.forEach { p ->
                if (p.email.isNotBlank()) {
                    require(p.email.matches(emailRegex)) {
                        "صيغة البريد الإلكتروني للطرف (${p.name}) غير صحيحة."
                    }
                }
                if (p.phone.isNotBlank()) {
                    require(p.phone.matches(phoneRegex)) {
                        "صيغة رقم الهاتف للطرف (${p.name}) غير صحيحة."
                    }
                }
            }

            val sanitizedWitnesses = witnesses.map { w ->
                w.copy(
                    name = w.name.trim().take(120),
                    email = w.email.trim().lowercase(Locale.US).take(150),
                    phone = w.phone.trim().take(30),
                    nationalId = w.nationalId?.trim()?.takeIf { it.isNotBlank() }?.take(30)
                )
            }.filter { it.name.isNotBlank() && (it.email.isNotBlank() || it.phone.isNotBlank()) }.take(4)

            sanitizedWitnesses.forEach { w ->
                if (w.email.isNotBlank()) {
                    require(w.email.matches(emailRegex)) {
                        "صيغة البريد الإلكتروني للشاهد (${w.name}) غير صحيحة."
                    }
                }
                if (w.phone.isNotBlank()) {
                    require(w.phone.matches(phoneRegex)) {
                        "صيغة رقم الهاتف للشاهد (${w.name}) غير صحيحة."
                    }
                }
            }

            val ownerParty = Party(
                uid = uid,
                name = ownerName,
                email = ownerEmail,
                phone = ownerPhone,
                role = "الطرف الأول (المُنشئ)",
                verifiedPhone = ownerPhone.isNotBlank(),
                biometricVerified = false,
                approved = false
            )
            val otherParties = sanitizedParties.filterNot {
                (ownerEmail.isNotBlank() && it.email.equals(ownerEmail, ignoreCase = true)) ||
                    (ownerPhone.isNotBlank() && isMatchingPhone(it.phone, ownerPhone))
            }
            val allParties = (listOf(ownerParty) + otherParties).take(6)

            val participantEmails = (
                listOf(ownerEmail) +
                    allParties.map { it.email } +
                    sanitizedWitnesses.map { it.email }
                ).map { it.trim().lowercase(Locale.US) }.filter { it.isNotBlank() }.distinct().take(10)

            val participantPhones = (
                listOf(ownerPhone) +
                    allParties.map { it.phone } +
                    sanitizedWitnesses.map { it.phone }
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)

            val agreementCode = existingAgreement?.agreementCode
                ?.takeIf { it.isNotBlank() }
                ?: AgreementHasher.generateAgreementCode()

            val targetStatus = if (submitAsPending) AgreementStatus.PENDING else AgreementStatus.DRAFT
            val contentHash = AgreementHasher.computeContentHash(
                agreementCode = agreementCode,
                title = cleanTitle,
                type = type.name,
                description = cleanDesc,
                amount = amount,
                currency = currency.name,
                paymentMethod = paymentMethod.name,
                startDate = cleanStartDate,
                dueDate = cleanDueDate,
                rights = cleanRights,
                obligations = cleanObligations,
                specialTerms = cleanSpecialTerms,
                ownerId = uid,
                participantEmails = participantEmails
            )

            val batch = db.batch()
            val resultId: String

            if (existingAgreement == null) {
                val agreementRef = db.collection("agreements").document()
                docPath = agreementRef.path
                resultId = agreementRef.id
                val version = AgreementVersion(
                    versionId = "v1",
                    hash = contentHash,
                    createdAtMillis = System.currentTimeMillis(),
                    approvedBy = emptyList()
                )

                val agreementPayload = mapOf(
                    "ownerId" to uid,
                    "ownerName" to ownerName,
                    "ownerEmail" to ownerEmail,
                    "ownerPhone" to ownerPhone,
                    "agreementCode" to agreementCode,
                    "title" to cleanTitle,
                    "type" to type.name,
                    "description" to cleanDesc,
                    "status" to targetStatus.name,
                    "currency" to currency.name,
                    "amount" to amount,
                    "paidTotal" to 0.0,
                    "agreementDate" to cleanAgreementDate,
                    "startDate" to cleanStartDate,
                    "dueDate" to cleanDueDate,
                    "paymentMethod" to paymentMethod.name,
                    "rights" to cleanRights,
                    "obligations" to cleanObligations,
                    "specialTerms" to cleanSpecialTerms,
                    "parties" to allParties.map { it.toMap() },
                    "witnesses" to sanitizedWitnesses.map { it.toMap() },
                    "attachments" to cleanAttachments.map { it.toMap() },
                    "participantUids" to listOf(uid),
                    "participantEmails" to participantEmails,
                    "participantPhones" to participantPhones,
                    "currentVersionId" to "v1",
                    "versions" to listOf(version.toMap()),
                    "contentHash" to contentHash,
                    "archived" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(agreementRef, agreementPayload)

                val auditRef = agreementRef.collection("audit").document()
                batch.set(
                    auditRef,
                    mapOf(
                        "agreementId" to resultId,
                        "action" to if (submitAsPending) "CREATED_AND_SENT" else "DRAFT_CREATED",
                        "actorUid" to uid,
                        "actorName" to ownerName,
                        "note" to if (submitAsPending) {
                            "تمت صياغة الميثاق وإرسال الدعوات للأطراف للاعتماد."
                        } else {
                            "تم حفظ الميثاق كمسودة أولية."
                        },
                        "snapshotHash" to contentHash,
                        "participantUids" to listOf(uid),
                        "participantEmails" to participantEmails,
                        "participantPhones" to participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                require(existingAgreement.ownerId == uid) {
                    "فقط منشئ الميثاق يمكنه تعديل المسودة أو الاستجابة لطلب التعديل."
                }
                require(
                    existingAgreement.statusEnum == AgreementStatus.DRAFT ||
                        existingAgreement.statusEnum == AgreementStatus.NEEDS_EDIT
                ) {
                    "لا يمكن تعديل الميثاق بعد اعتماده أو إغلاقه."
                }

                val agreementRef = db.collection("agreements").document(existingAgreement.id)
                docPath = agreementRef.path
                resultId = existingAgreement.id
                val nextVersionNum = existingAgreement.versions.size + 1
                val nextVersionId = "v$nextVersionNum"
                val newVersion = AgreementVersion(
                    versionId = nextVersionId,
                    hash = contentHash,
                    createdAtMillis = System.currentTimeMillis(),
                    approvedBy = listOf(uid)
                )
                val updatedVersions = (existingAgreement.versions + newVersion).takeLast(20)
                val updatedUids = (existingAgreement.participantUids + uid).distinct()

                val updatePayload = mapOf(
                    "title" to cleanTitle,
                    "type" to type.name,
                    "description" to cleanDesc,
                    "status" to targetStatus.name,
                    "currency" to currency.name,
                    "amount" to amount,
                    "agreementDate" to cleanAgreementDate,
                    "startDate" to cleanStartDate,
                    "dueDate" to cleanDueDate,
                    "paymentMethod" to paymentMethod.name,
                    "rights" to cleanRights,
                    "obligations" to cleanObligations,
                    "specialTerms" to cleanSpecialTerms,
                    "parties" to allParties.map { it.toMap() },
                    "witnesses" to sanitizedWitnesses.map { it.toMap() },
                    "attachments" to cleanAttachments.map { it.toMap() },
                    "participantUids" to updatedUids,
                    "participantEmails" to participantEmails,
                    "participantPhones" to participantPhones,
                    "currentVersionId" to nextVersionId,
                    "versions" to updatedVersions.map { it.toMap() },
                    "contentHash" to contentHash,
                    "editRequestNote" to "",
                    "archived" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(agreementRef, updatePayload)

                val auditRef = agreementRef.collection("audit").document()
                batch.set(
                    auditRef,
                    mapOf(
                        "agreementId" to resultId,
                        "action" to if (submitAsPending) "UPDATED_AND_RESENT" else "DRAFT_UPDATED",
                        "actorUid" to uid,
                        "actorName" to ownerName,
                        "note" to "تم تحديث بنود الميثاق (الإصدار $nextVersionId).",
                        "snapshotHash" to contentHash,
                        "participantUids" to updatedUids,
                        "participantEmails" to participantEmails,
                        "participantPhones" to participantPhones,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (submitAsPending) {
                val expiresAt = Timestamp(Date(System.currentTimeMillis() + 7L * 86_400_000L))
                otherParties.forEach { party ->
                    val token = AgreementHasher.generateStrongInviteToken("inv")
                    batch.set(
                        db.collection("invitations").document(token),
                        buildInvitationPayload(
                            agreementId = resultId,
                            agreementCode = agreementCode,
                            agreementTitle = cleanTitle,
                            type = type,
                            amount = amount,
                            currency = currency,
                            inviterUid = uid,
                            inviterName = ownerName,
                            inviteePhone = party.phone,
                            inviteeEmail = party.email,
                            purpose = InvitationPurpose.PARTY,
                            expiresAt = expiresAt
                        )
                    )
                }
                sanitizedWitnesses.forEach { witness ->
                    val token = AgreementHasher.generateStrongInviteToken("wit")
                    batch.set(
                        db.collection("invitations").document(token),
                        buildInvitationPayload(
                            agreementId = resultId,
                            agreementCode = agreementCode,
                            agreementTitle = cleanTitle,
                            type = type,
                            amount = amount,
                            currency = currency,
                            inviterUid = uid,
                            inviterName = ownerName,
                            inviteePhone = witness.phone,
                            inviteeEmail = witness.email,
                            purpose = InvitationPurpose.WITNESS,
                            expiresAt = expiresAt
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(resultId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendAgreementInvitations(agreement: Agreement): Result<Int> {
        val docPath = "agreements/${agreement.id}"
        return try {
            val actor = requireActorIdentity()
            val uid = actor.uid
            require(agreement.ownerId == uid) {
                "فقط منشئ الميثاق يمكنه إرسال الدعوات."
            }
            require(
                agreement.statusEnum == AgreementStatus.DRAFT ||
                    agreement.statusEnum == AgreementStatus.NEEDS_EDIT ||
                    agreement.statusEnum == AgreementStatus.PENDING
            ) {
                "لا يمكن إرسال دعوات لميثاق في حالته الحالية."
            }

            val nonOwnerParties = agreement.parties.filterNot {
                it.uid == uid ||
                    (actor.email.isNotBlank() && it.email.equals(actor.email, ignoreCase = true)) ||
                    (actor.phone.isNotBlank() && isMatchingPhone(it.phone, actor.phone))
            }
            require(nonOwnerParties.isNotEmpty() || agreement.witnesses.isNotEmpty()) {
                "لا يوجد أطراف أو شهود مدعوون لإرسال رابط الدعوة لهم."
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val batch = db.batch()
            if (agreement.statusEnum == AgreementStatus.DRAFT || agreement.statusEnum == AgreementStatus.NEEDS_EDIT) {
                batch.update(
                    agreementRef,
                    mapOf(
                        "status" to AgreementStatus.PENDING.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 7L * 86_400_000L))
            var count = 0
            nonOwnerParties.forEach { party ->
                if (party.email.isNotBlank() || party.phone.isNotBlank()) {
                    val token = AgreementHasher.generateStrongInviteToken("inv")
                    batch.set(
                        db.collection("invitations").document(token),
                        buildInvitationPayload(
                            agreementId = agreement.id,
                            agreementCode = agreement.agreementCode,
                            agreementTitle = agreement.title,
                            type = agreement.typeEnum,
                            amount = agreement.amount,
                            currency = agreement.currencyEnum,
                            inviterUid = uid,
                            inviterName = agreement.ownerName.ifBlank { actor.name },
                            inviteePhone = party.phone,
                            inviteeEmail = party.email,
                            purpose = InvitationPurpose.PARTY,
                            expiresAt = expiresAt
                        )
                    )
                    count++
                }
            }
            agreement.witnesses.forEach { witness ->
                if (witness.email.isNotBlank() || witness.phone.isNotBlank()) {
                    val token = AgreementHasher.generateStrongInviteToken("wit")
                    batch.set(
                        db.collection("invitations").document(token),
                        buildInvitationPayload(
                            agreementId = agreement.id,
                            agreementCode = agreement.agreementCode,
                            agreementTitle = agreement.title,
                            type = agreement.typeEnum,
                            amount = agreement.amount,
                            currency = agreement.currencyEnum,
                            inviterUid = uid,
                            inviterName = agreement.ownerName.ifBlank { actor.name },
                            inviteePhone = witness.phone,
                            inviteeEmail = witness.email,
                            purpose = InvitationPurpose.WITNESS,
                            expiresAt = expiresAt
                        )
                    )
                    count++
                }
            }

            val auditRef = agreementRef.collection("audit").document()
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "INVITATIONS_SENT",
                    "actorUid" to uid,
                    "actorName" to actor.name,
                    "note" to "تم إصدار $count رابط دعوة صالحة لمدة 7 أيام للأطراف والشهود.",
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to agreement.participantUids,
                    "participantEmails" to agreement.participantEmails,
                    "participantPhones" to agreement.participantPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun archiveAgreement(agreement: Agreement): Result<Unit> {
        val docPath = "agreements/${agreement.id}"
        return try {
            val actor = requireActorIdentity()
            require(agreement.ownerId == actor.uid) {
                "فقط منشئ الميثاق يمكنه أرشفة المسودة."
            }
            require(
                agreement.statusEnum == AgreementStatus.DRAFT ||
                    agreement.statusEnum == AgreementStatus.NEEDS_EDIT
            ) {
                "لا يمكن حذف أو أرشفة الميثاق بعد إرساله أو اعتماده؛ يتيح النظام أرشفة المسودات فقط."
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val auditRef = agreementRef.collection("audit").document()
            val batch = db.batch()
            batch.update(
                agreementRef,
                mapOf(
                    "status" to AgreementStatus.DRAFT.name,
                    "archived" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "LOGICAL_ARCHIVE",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "تم حذف الميثاق منطقياً (أرشفة) من القائمة مع بقاء السجل التدقيقي محفوظاً.",
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to agreement.participantUids,
                    "participantEmails" to agreement.participantEmails,
                    "participantPhones" to agreement.participantPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun getPublicInvitation(token: String): PublicInvitationLookup {
        val cleanToken = token.trim()
        if (cleanToken.length < 20 || !cleanToken.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            return PublicInvitationLookup.InvalidToken
        }
        return try {
            val snap = db.collection("invitations").document(cleanToken).get().await()
            if (!snap.exists()) {
                PublicInvitationLookup.NotFound
            } else {
                val inv = snap.toObject(
                    Invitation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return PublicInvitationLookup.NotFound
                when {
                    inv.usedAt != null -> PublicInvitationLookup.AlreadyUsed(inv)
                    inv.expiresAt != null && inv.expiresAt.toDate().before(Date()) ->
                        PublicInvitationLookup.Expired(inv)
                    else -> PublicInvitationLookup.Valid(inv)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PublicInvitationLookup.Error(toArabicErrorMessage(e))
        }
    }

    suspend fun claimInvitation(token: String): Result<String> {
        val cleanToken = token.trim()
        val path = "invitations/$cleanToken"
        return try {
            val actor = requireActorIdentity()
            require(cleanToken.length >= 20) { "رمز الدعوة غير صالح." }
            val ref = db.collection("invitations").document(cleanToken)
            val snap = ref.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("لم يتم العثور على هذه الدعوة."))
            }
            val inv = snap.toObject(
                Invitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("تعذّر قراءة بيانات الدعوة."))

            if (inv.usedAt != null) {
                return Result.success(inv.agreementId)
            }
            if (inv.expiresAt != null && inv.expiresAt.toDate().before(Date())) {
                return Result.failure(IllegalStateException("انتهت صلاحية رابط الدعوة."))
            }

            val tokenEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.US).orEmpty()
            val tokenPhone = auth.currentUser?.phoneNumber?.trim().orEmpty()
            val emailMatches = tokenEmail.isNotBlank() &&
                inv.inviteeEmail.isNotBlank() &&
                inv.inviteeEmail.equals(tokenEmail, ignoreCase = true)
            val tokenPhoneMatches = tokenPhone.isNotBlank() &&
                inv.inviteePhone.isNotBlank() &&
                isMatchingPhone(inv.inviteePhone, tokenPhone)

            if (emailMatches || tokenPhoneMatches) {
                ref.update("usedAt", FieldValue.serverTimestamp()).await()
            }
            Result.success(inv.agreementId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveUserCredentialId(credentialId: String): Result<Unit> =
        workflows.saveUserCredentialId(credentialId)

    suspend fun sendVerificationOtp(
        appContext: Context,
        agreement: Agreement,
        targetIdentifier: String
    ): Result<OtpChallengeIssued> =
        workflows.sendVerificationOtp(appContext, agreement, targetIdentifier)

    suspend fun verifyVerificationOtp(
        appContext: Context,
        agreement: Agreement,
        challengeId: String,
        enteredOtp: String
    ): Result<OtpVerifyOutcome> =
        workflows.verifyVerificationOtp(appContext, agreement, challengeId, enteredOtp)

    suspend fun signAgreement(
        appContext: Context,
        agreement: Agreement,
        phoneVerified: Boolean,
        biometricVerified: Boolean,
        credentialId: String?,
        verificationMethod: String,
        signatureHex: String?
    ): Result<AgreementSignature> =
        workflows.signAgreement(
            appContext = appContext,
            agreement = agreement,
            phoneVerified = phoneVerified,
            biometricVerified = biometricVerified,
            credentialId = credentialId,
            verificationMethod = verificationMethod,
            signatureHex = signatureHex
        )

    suspend fun approveAgreement(agreement: Agreement, note: String?): Result<Unit> =
        workflows.approveAgreement(agreement, note)

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
    ): Result<String> =
        workflows.requestAmendment(
            agreement = agreement,
            reason = reason,
            proposedTitle = proposedTitle,
            proposedDescription = proposedDescription,
            proposedAmount = proposedAmount,
            proposedDueDate = proposedDueDate,
            proposedRights = proposedRights,
            proposedObligations = proposedObligations,
            proposedSpecialTerms = proposedSpecialTerms
        )

    suspend fun respondToAmendment(
        agreement: Agreement,
        amendment: AgreementAmendment,
        approve: Boolean,
        note: String?
    ): Result<Boolean> =
        workflows.respondToAmendment(agreement, amendment, approve, note)

    suspend fun requestEdits(agreement: Agreement, reason: String): Result<Unit> {
        val docPath = "agreements/${agreement.id}"
        return try {
            val actor = requireActorIdentity()
            val cleanReason = reason.trim().take(500)
            require(cleanReason.length >= 3) { "يرجى توضيح التعديل المطلوب (3 أحرف على الأقل)." }
            require(agreement.statusEnum == AgreementStatus.PENDING) {
                "يمكن طلب التعديل فقط للمواثيق التي بانتظار الموافقة."
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val auditRef = agreementRef.collection("audit").document()

            val batch = db.batch()
            batch.update(
                agreementRef,
                mapOf(
                    "status" to AgreementStatus.NEEDS_EDIT.name,
                    "editRequestNote" to cleanReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "EDITS_REQUESTED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to cleanReason,
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to agreement.participantUids,
                    "participantEmails" to agreement.participantEmails,
                    "participantPhones" to agreement.participantPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAgreementExpired(agreement: Agreement): Result<Unit> {
        val docPath = "agreements/${agreement.id}"
        return try {
            val actor = requireActorIdentity()
            require(
                agreement.statusEnum == AgreementStatus.PENDING ||
                    agreement.statusEnum == AgreementStatus.APPROVED
            ) {
                "لا يمكن إنهاء الميثاق من هذه الحالة."
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val auditRef = agreementRef.collection("audit").document()

            val batch = db.batch()
            batch.update(
                agreementRef,
                mapOf(
                    "status" to AgreementStatus.EXPIRED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "MARKED_EXPIRED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "تم إغلاق الميثاق بحالة منتهٍ وإتمام مدته.",
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to agreement.participantUids,
                    "participantEmails" to agreement.participantEmails,
                    "participantPhones" to agreement.participantPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
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
        paymentDateText: String = "",
        attachment: AgreementAttachment? = null
    ): Result<String> =
        workflows.recordPayment(
            agreement = agreement,
            amount = amount,
            method = method,
            receiptReference = receiptReference,
            paymentDateText = paymentDateText,
            attachment = attachment
        )

    suspend fun confirmPayment(
        agreement: Agreement,
        payment: AgreementPayment,
        mode: PaymentConfirmationMode = PaymentConfirmationMode.FULL,
        confirmedAmountInput: Double = payment.amount,
        note: String? = null,
        witnessOtpChallengeId: String? = null
    ): Result<Unit> =
        workflows.confirmPayment(
            agreement = agreement,
            payment = payment,
            mode = mode,
            confirmedAmountInput = confirmedAmountInput,
            note = note,
            witnessOtpChallengeId = witnessOtpChallengeId
        )

    suspend fun openDispute(
        agreement: Agreement,
        disputeType: String,
        details: String,
        evidenceNotes: List<String>
    ): Result<String> {
        var docPath = "agreements/${agreement.id}/disputes"
        return try {
            val actor = requireActorIdentity()
            val cleanType = disputeType.trim().take(80)
            val cleanDetails = details.trim().take(2000)
            val cleanEvidence = evidenceNotes.map { it.trim() }.filter { it.isNotEmpty() }.take(10)

            require(cleanType.length >= 2) { "يرجى تحديد نوع النزاع." }
            require(cleanDetails.length >= 10) { "يرجى شرح تفاصيل النزاع (10 أحرف على الأقل)." }
            require(
                agreement.statusEnum == AgreementStatus.PENDING ||
                    agreement.statusEnum == AgreementStatus.APPROVED
            ) {
                "لا يمكن فتح نزاع من الحالة الحالية للميثاق."
            }

            val updatedUids = (agreement.participantUids + actor.uid).distinct()
            val updatedEmails = if (actor.email.isNotBlank()) {
                (agreement.participantEmails + actor.email).distinct()
            } else {
                agreement.participantEmails
            }
            val updatedPhones = if (actor.phone.isNotBlank()) {
                (agreement.participantPhones + actor.phone).distinct()
            } else {
                agreement.participantPhones
            }

            val agreementRef = db.collection("agreements").document(agreement.id)
            val disputeRef = agreementRef.collection("disputes").document()
            val auditRef = agreementRef.collection("audit").document()
            docPath = disputeRef.path

            val batch = db.batch()
            batch.update(
                agreementRef,
                mapOf(
                    "status" to AgreementStatus.DISPUTED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                disputeRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "type" to cleanType,
                    "details" to cleanDetails,
                    "evidenceNotes" to cleanEvidence,
                    "status" to DisputeStatus.OPEN.name,
                    "openedBy" to actor.uid,
                    "openedByName" to actor.name,
                    "replies" to emptyList<Map<String, Any>>(),
                    "participantUids" to updatedUids,
                    "participantEmails" to updatedEmails,
                    "participantPhones" to updatedPhones,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "DISPUTE_OPENED",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "فتح نزاع ($cleanType): ${cleanDetails.take(120)}",
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to updatedUids,
                    "participantEmails" to updatedEmails,
                    "participantPhones" to updatedPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(disputeRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun replyToDispute(
        agreement: Agreement,
        dispute: AgreementDispute,
        message: String,
        newStatus: DisputeStatus
    ): Result<Unit> {
        val docPath = "agreements/${agreement.id}/disputes/${dispute.id}"
        return try {
            val actor = requireActorIdentity()
            val cleanMsg = message.trim().take(800)
            require(cleanMsg.length >= 2) { "يرجى كتابة الرد قبل الإرسال." }

            val updatedReplies = (
                dispute.replies + DisputeReply(
                    authorUid = actor.uid,
                    authorName = actor.name,
                    message = cleanMsg,
                    createdAtMillis = System.currentTimeMillis()
                )
                ).takeLast(30)

            val agreementRef = db.collection("agreements").document(agreement.id)
            val disputeRef = agreementRef.collection("disputes").document(dispute.id)
            val auditRef = agreementRef.collection("audit").document()

            val batch = db.batch()
            batch.update(
                disputeRef,
                mapOf(
                    "replies" to updatedReplies.map { it.toMap() },
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                auditRef,
                mapOf(
                    "agreementId" to agreement.id,
                    "action" to "DISPUTE_REPLY",
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "note" to "رد على النزاع (${newStatus.arabicLabel}): ${cleanMsg.take(120)}",
                    "snapshotHash" to agreement.contentHash,
                    "participantUids" to agreement.participantUids,
                    "participantEmails" to agreement.participantEmails,
                    "participantPhones" to agreement.participantPhones,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
