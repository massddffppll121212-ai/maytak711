package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BiometricSignatureResult
import com.example.data.AgreementRepository
import com.example.data.AuthManager
import com.example.data.AuthRateLimiter
import com.example.data.OtpChallengeIssued
import com.example.data.toArabicErrorMessage
import com.example.model.Agreement
import com.example.model.AgreementAmendment
import com.example.model.AgreementAttachment
import com.example.model.AgreementDispute
import com.example.model.AgreementPayment
import com.example.model.AgreementSignature
import com.example.model.AgreementStatus
import com.example.model.AgreementType
import com.example.model.AuditLog
import com.example.model.CurrencyCode
import com.example.model.DisputeStatus
import com.example.model.Invitation
import com.example.model.OtpChallengeRecord
import com.example.model.Party
import com.example.model.PaymentConfirmationMode
import com.example.model.PaymentMethodType
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserSession
import com.example.model.VaultDocument
import com.example.model.Witness
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AgreementFilter(val arabicLabel: String) {
    ALL("الكل"),
    DRAFT("مسودة"),
    PENDING("بانتظار الموافقة"),
    APPROVED("معتمد"),
    NEEDS_EDIT("يحتاج تعديل"),
    EXPIRED("منتهٍ"),
    DISPUTED("متنازع")
}

sealed interface ActionFeedback {
    data object Idle : ActionFeedback
    data class Loading(val label: String = "جارٍ التنفيذ والتوثيق…") : ActionFeedback
    data class Success(val message: String, val createdAgreementId: String? = null) : ActionFeedback
    data class Error(val message: String) : ActionFeedback
}

class MeethaqViewModel(
    private val repository: AgreementRepository,
    private val appContext: Context,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserPhone: String = ""
) : ViewModel() {

    val currentSessionId: String = AuthRateLimiter.computeDeviceSessionId(appContext, currentUserId)

    private val _newDeviceAlert = MutableStateFlow<String?>(null)
    val newDeviceAlert: StateFlow<String?> = _newDeviceAlert.asStateFlow()

    private val _sessionRevokedExternally = MutableStateFlow(false)
    val sessionRevokedExternally: StateFlow<Boolean> = _sessionRevokedExternally.asStateFlow()

    private var currentSessionRegistered = false

    private val _selectedAgreementId = MutableStateFlow<String?>(null)
    val selectedAgreementId: StateFlow<String?> = _selectedAgreementId.asStateFlow()

    private val _editingAgreement = MutableStateFlow<Agreement?>(null)
    val editingAgreement: StateFlow<Agreement?> = _editingAgreement.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AgreementFilter.ALL)
    val selectedFilter: StateFlow<AgreementFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionFeedback = MutableStateFlow<ActionFeedback>(ActionFeedback.Idle)
    val actionFeedback: StateFlow<ActionFeedback> = _actionFeedback.asStateFlow()

    private val _activeOtpChallenge = MutableStateFlow<OtpChallengeIssued?>(null)
    val activeOtpChallenge: StateFlow<OtpChallengeIssued?> = _activeOtpChallenge.asStateFlow()

    private val _localOtpVerifiedAgreementId = MutableStateFlow<String?>(null)
    val localOtpVerifiedAgreementId: StateFlow<String?> = _localOtpVerifiedAgreementId.asStateFlow()

    private val _localOtpChallengeId = MutableStateFlow<String?>(null)
    val localOtpChallengeId: StateFlow<String?> = _localOtpChallengeId.asStateFlow()

    private val _pendingBiometricSignature = MutableStateFlow<BiometricSignatureResult?>(null)
    val pendingBiometricSignature: StateFlow<BiometricSignatureResult?> = _pendingBiometricSignature.asStateFlow()

    private val _localThemeOverride = MutableStateFlow<ThemePreference?>(null)

    private fun <T> unwrapFirestoreError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            Log.w(TAG, "Failed to observe user profile", unwrapFirestoreError<UserProfile?>(error))
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val themePreference: StateFlow<ThemePreference> = userProfile
        .map { profile ->
            _localThemeOverride.value ?: profile?.themePreferenceEnum ?: ThemePreference.SYSTEM
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ThemePreference.SYSTEM)

    val vaultState: StateFlow<UiState<List<VaultDocument>>> = repository.observeVaultDocuments(currentUserId)
        .map<List<VaultDocument>, UiState<List<VaultDocument>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError<List<VaultDocument>>(error)
            Log.w(TAG, "Error observing vault", root)
            emit(UiState.Error(toArabicErrorMessage(root)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val sessionsState: StateFlow<UiState<List<UserSession>>> = repository.observeSessions(currentUserId)
        .map<List<UserSession>, UiState<List<UserSession>>> { sessions ->
            if (currentSessionRegistered) {
                val mine = sessions.firstOrNull { it.id == currentSessionId }
                if (mine != null && mine.revoked) {
                    _sessionRevokedExternally.value = true
                }
            }
            UiState.Success(sessions)
        }
        .catch { error ->
            val root = unwrapFirestoreError<List<UserSession>>(error)
            Log.w(TAG, "Error observing sessions", root)
            emit(UiState.Error(toArabicErrorMessage(root)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val invitationsState: StateFlow<UiState<List<Invitation>>> =
        repository.observeInvitations(currentUserId, currentUserEmail, currentUserPhone)
            .map<List<Invitation>, UiState<List<Invitation>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError<List<Invitation>>(error)
                Log.w(TAG, "Error observing invitations", root)
                emit(UiState.Error(toArabicErrorMessage(root)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val agreementsState: StateFlow<UiState<List<Agreement>>> =
        repository.observeAllAgreements(currentUserId, currentUserEmail, currentUserPhone)
            .map<List<Agreement>, UiState<List<Agreement>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError<List<Agreement>>(error)
                Log.w(TAG, "Error observing agreements", root)
                emit(UiState.Error(toArabicErrorMessage(root)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementState: StateFlow<UiState<Agreement?>> = _selectedAgreementId
        .flatMapLatest { agreementId ->
            if (agreementId.isNullOrBlank()) {
                flowOf(UiState.Success(null))
            } else {
                repository.observeAgreementById(currentUserId, agreementId)
                    .map<Agreement?, UiState<Agreement?>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<Agreement?>(error)
                        Log.w(TAG, "Error observing agreement detail", root)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementPayments: StateFlow<UiState<List<AgreementPayment>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementPayments(currentUserId, agreement)
                    .map<List<AgreementPayment>, UiState<List<AgreementPayment>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<AgreementPayment>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementDisputes: StateFlow<UiState<List<AgreementDispute>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementDisputes(currentUserId, agreement)
                    .map<List<AgreementDispute>, UiState<List<AgreementDispute>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<AgreementDispute>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementAudit: StateFlow<UiState<List<AuditLog>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementAuditLogs(currentUserId, agreement)
                    .map<List<AuditLog>, UiState<List<AuditLog>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<AuditLog>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementSignatures: StateFlow<UiState<List<AgreementSignature>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementSignatures(currentUserId, agreement)
                    .map<List<AgreementSignature>, UiState<List<AgreementSignature>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<AgreementSignature>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementAmendments: StateFlow<UiState<List<AgreementAmendment>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementAmendments(currentUserId, agreement)
                    .map<List<AgreementAmendment>, UiState<List<AgreementAmendment>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<AgreementAmendment>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAgreementApprovals: StateFlow<UiState<List<OtpChallengeRecord>>> = selectedAgreementState
        .flatMapLatest { state ->
            val agreement = (state as? UiState.Success)?.data
            if (agreement == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAgreementApprovals(currentUserId, agreement)
                    .map<List<OtpChallengeRecord>, UiState<List<OtpChallengeRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError<List<OtpChallengeRecord>>(error)
                        emit(UiState.Error(toArabicErrorMessage(root)))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    init {
        viewModelScope.launch {
            val result = repository.ensureUserProfileAndSession(appContext)
            result.onSuccess { sessionInfo ->
                currentSessionRegistered = true
                if (sessionInfo.isNewDevice) {
                    _newDeviceAlert.value =
                        "تنبيه أمني: تم تسجيل دخول جديد إلى حسابك من جهاز (${sessionInfo.deviceLabel}). يمكنك مراجعة الجلسات النشطة من الإعدادات."
                }
            }
        }
    }

    fun dismissNewDeviceAlert() {
        _newDeviceAlert.value = null
    }

    fun selectAgreement(agreementId: String?) {
        _selectedAgreementId.value = agreementId
        _activeOtpChallenge.value = null
        _pendingBiometricSignature.value = null
    }

    fun startEditingAgreement(agreement: Agreement?) {
        _editingAgreement.value = agreement
    }

    fun setFilter(filter: AgreementFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearActionFeedback() {
        _actionFeedback.value = ActionFeedback.Idle
    }

    fun setLocalError(message: String) {
        _actionFeedback.value = ActionFeedback.Error(message)
    }

    fun updateThemePreference(preference: ThemePreference) {
        _localThemeOverride.value = preference
        viewModelScope.launch {
            val result = repository.updateThemePreference(preference)
            if (result.isFailure) {
                _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(result.exceptionOrNull()))
            }
        }
    }

    fun updateUserProfile(fullName: String, phone: String) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تحديث بيانات الحساب…")
        viewModelScope.launch {
            val result = repository.updateUserProfile(fullName = fullName, phone = phone)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم تحديث الملف الشخصي بنجاح.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun markPhoneVerified(phone: String) {
        viewModelScope.launch {
            val result = repository.markPhoneVerified(phone)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم توثيق رقم الهاتف وربطه بحسابك بنجاح.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun changePassword(newPassword: String) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تحديث كلمة المرور…")
        viewModelScope.launch {
            val result = AuthManager.updateLocalPasswordIfSupported(newPassword)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم تغيير كلمة المرور بنجاح.") },
                onFailure = { ActionFeedback.Error(it.localizedMessage ?: "تعذّر تحديث كلمة المرور.") }
            )
        }
    }

    fun revokeSession(sessionId: String, onCurrentSessionRevoked: () -> Unit = {}) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إنهاء الجلسة…")
        viewModelScope.launch {
            val result = repository.revokeSession(sessionId)
            result.fold(
                onSuccess = {
                    if (sessionId == currentSessionId) {
                        onCurrentSessionRevoked()
                    } else {
                        _actionFeedback.value = ActionFeedback.Success("تم تسجيل الخروج من الجلسة المحددة.")
                    }
                },
                onFailure = {
                    _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(it))
                }
            )
        }
    }

    fun revokeAllSessions(onSignOutComplete: () -> Unit) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إنهاء جميع الجلسات النشطة…")
        viewModelScope.launch {
            val result = repository.revokeAllSessions()
            result.fold(
                onSuccess = {
                    onSignOutComplete()
                },
                onFailure = {
                    _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(it))
                }
            )
        }
    }

    fun addVaultDocument(title: String, category: String, referenceCode: String, notes: String?) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تشفير وحفظ الوثيقة في الخزنة…")
        viewModelScope.launch {
            val result = repository.addVaultDocument(title, category, referenceCode, notes)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم حفظ الوثيقة في خزنتك الخاصة مع بصمة SHA-256.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun saveAgreement(
        existingAgreement: Agreement?,
        title: String,
        type: AgreementType,
        description: String,
        amount: Double,
        currency: CurrencyCode,
        paymentMethod: PaymentMethodType,
        agreementDate: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        invitedParties: List<Party>,
        witnesses: List<Witness>,
        attachments: List<AgreementAttachment>,
        submitAsPending: Boolean
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading(
            if (submitAsPending) "جارٍ حفظ الميثاق وإرسال الدعوات…" else "جارٍ حفظ المسودة…"
        )
        viewModelScope.launch {
            val result = repository.createOrUpdateAgreement(
                existingAgreement = existingAgreement,
                title = title,
                type = type,
                description = description,
                amount = amount,
                currency = currency,
                paymentMethod = paymentMethod,
                agreementDate = agreementDate,
                startDate = startDate,
                dueDate = dueDate,
                rights = rights,
                obligations = obligations,
                specialTerms = specialTerms,
                invitedParties = invitedParties,
                witnesses = witnesses,
                attachments = attachments,
                submitAsPending = submitAsPending
            )
            _actionFeedback.value = result.fold(
                onSuccess = { id ->
                    com.example.data.DraftAutoSaveStore.clear(appContext, currentUserId)
                    _editingAgreement.value = null
                    _selectedAgreementId.value = id
                    ActionFeedback.Success(
                        message = if (submitAsPending) {
                            "تم توثيق الميثاق وإرساله للأطراف للاعتماد."
                        } else {
                            "تم حفظ الميثاق كمسودة بنجاح."
                        },
                        createdAgreementId = id
                    )
                },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    suspend fun autoSaveCloudDraft(
        existingAgreement: Agreement?,
        title: String,
        type: AgreementType,
        description: String,
        amount: Double,
        currency: CurrencyCode,
        paymentMethod: PaymentMethodType,
        agreementDate: String,
        startDate: String,
        dueDate: String,
        rights: List<String>,
        obligations: List<String>,
        specialTerms: List<String>,
        invitedParties: List<Party>,
        witnesses: List<Witness>,
        attachments: List<AgreementAttachment>
    ): Result<String> {
        return repository.createOrUpdateAgreement(
            existingAgreement = existingAgreement,
            title = title,
            type = type,
            description = description,
            amount = amount,
            currency = currency,
            paymentMethod = paymentMethod,
            agreementDate = agreementDate,
            startDate = startDate,
            dueDate = dueDate,
            rights = rights,
            obligations = obligations,
            specialTerms = specialTerms,
            invitedParties = invitedParties,
            witnesses = witnesses,
            attachments = attachments,
            submitAsPending = false
        )
    }

    fun sendInvitations(agreement: Agreement) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إصدار روابط الدعوة للأطراف…")
        viewModelScope.launch {
            val result = repository.sendAgreementInvitations(agreement)
            _actionFeedback.value = result.fold(
                onSuccess = { count ->
                    ActionFeedback.Success("تم إصدار $count رابط دعوة صالحة لمدة 7 أيام بنجاح.")
                },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun archiveAgreement(agreement: Agreement, onArchived: () -> Unit = {}) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ أرشفة المسودة…")
        viewModelScope.launch {
            val result = repository.archiveAgreement(agreement)
            result.fold(
                onSuccess = {
                    _actionFeedback.value = ActionFeedback.Success("تمت أرشفة الميثاق (حذف منطقي) بنجاح.")
                    onArchived()
                },
                onFailure = {
                    _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(it))
                }
            )
        }
    }

    suspend fun claimInviteAndOpen(token: String): Result<String> {
        val res = repository.claimInvitation(token)
        res.onSuccess { agreementId ->
            _selectedAgreementId.value = agreementId
        }
        return res
    }

    fun requestAgreementOtp(agreement: Agreement, targetIdentifier: String) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إصدار رمز التحقق OTP الموقوت…")
        viewModelScope.launch {
            val result = repository.sendVerificationOtp(appContext, agreement, targetIdentifier)
            result.fold(
                onSuccess = { issued ->
                    _activeOtpChallenge.value = issued
                    _actionFeedback.value = ActionFeedback.Success(
                        "تم إصدار رمز التحقق OTP (صالح 5 دقائق / حد أقصى 5 محاولات)."
                    )
                },
                onFailure = {
                    _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(it))
                }
            )
        }
    }

    fun verifyAgreementOtp(agreement: Agreement, challengeId: String, enteredOtp: String) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ التحقق من رمز OTP عبر الخادم…")
        viewModelScope.launch {
            val result = repository.verifyVerificationOtp(appContext, agreement, challengeId, enteredOtp)
            result.fold(
                onSuccess = { outcome ->
                    if (outcome.verified) {
                        _activeOtpChallenge.value = null
                        _localOtpVerifiedAgreementId.value = agreement.id
                        _localOtpChallengeId.value = challengeId
                        _actionFeedback.value = ActionFeedback.Success(outcome.message)
                    } else {
                        if (outcome.remainingAttempts == 0) {
                            _activeOtpChallenge.value = null
                        }
                        _actionFeedback.value = ActionFeedback.Error(outcome.message)
                    }
                },
                onFailure = {
                    _actionFeedback.value = ActionFeedback.Error(toArabicErrorMessage(it))
                }
            )
        }
    }

    fun clearActiveOtpChallenge() {
        _activeOtpChallenge.value = null
    }

    fun onBiometricSignedLocally(result: BiometricSignatureResult) {
        _pendingBiometricSignature.value = result
        viewModelScope.launch {
            repository.saveUserCredentialId(result.credentialId)
        }
        _actionFeedback.value = ActionFeedback.Success(
            "تم التقاط التوقيع البيومتري/Passkey محلياً (معرّف المفتاح: ${result.credentialId.take(14)}…). اضغط الآن «تأكيد الموافقة» للتوثيق السحابي."
        )
    }

    fun signAgreementDualFactor(
        agreement: Agreement,
        phoneVerified: Boolean,
        biometricVerified: Boolean,
        credentialId: String?,
        verificationMethod: String,
        signatureHex: String?
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تسجيل توقيعك المزدوج على السيرفر…")
        viewModelScope.launch {
            val result = repository.signAgreement(
                appContext = appContext,
                agreement = agreement,
                phoneVerified = phoneVerified,
                biometricVerified = biometricVerified,
                credentialId = credentialId,
                verificationMethod = verificationMethod,
                signatureHex = signatureHex
            )
            _actionFeedback.value = result.fold(
                onSuccess = { sig ->
                    _pendingBiometricSignature.value = null
                    ActionFeedback.Success(
                        "تم تأكيد موافقتك وتوثيق التوقيع (${sig.verificationMethod}) بنجاح على الخادم."
                    )
                },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun approveAgreement(agreement: Agreement, note: String?) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ التحقق من توقيع جميع الأطراف وقفل النسخة نهائياً…")
        viewModelScope.launch {
            val result = repository.approveAgreement(agreement, note)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم اعتماد الميثاق بإجماع الأطراف وقفل نسخته الرسمية نهائياً.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun requestAmendment(
        agreement: Agreement,
        reason: String,
        proposedTitle: String,
        proposedDescription: String,
        proposedAmount: Double,
        proposedDueDate: String,
        proposedRights: List<String>,
        proposedObligations: List<String>,
        proposedSpecialTerms: List<String>
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إرسال طلب التعديل والمقارنة لجميع الأطراف…")
        viewModelScope.launch {
            val result = repository.requestAmendment(
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
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم رفع طلب التعديل المقترح بانتظار موافقة جميع الأطراف.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun respondToAmendment(
        agreement: Agreement,
        amendment: AgreementAmendment,
        approve: Boolean,
        note: String?
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تسجيل موقفك من طلب التعديل…")
        viewModelScope.launch {
            val result = repository.respondToAmendment(agreement, amendment, approve, note)
            _actionFeedback.value = result.fold(
                onSuccess = { activated ->
                    when {
                        !approve -> ActionFeedback.Success("تم رفض طلب التعديل وبقيت النسخة المعتمدة الحالية سارية دون تغيير.")
                        activated -> ActionFeedback.Success("اكتمل إجماع الأطراف وتم اعتماد التعديل وإنشاء نسخة جديدة مقفلة!")
                        else -> ActionFeedback.Success("تم تسجيل موافقتك على التعديل. بانتظار موافقة بقية الأطراف.")
                    }
                },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun requestEdits(agreement: Agreement, reason: String) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إرسال طلب التعديل…")
        viewModelScope.launch {
            val result = repository.requestEdits(agreement, reason)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تمت إعادة الميثاق للمُنشئ مع ملاحظات التعديل.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun markAgreementExpired(agreement: Agreement) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ وسم الميثاق كمنتهٍ…")
        viewModelScope.launch {
            val result = repository.markAgreementExpired(agreement)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم إغلاق الميثاق بحالة (منتهٍ).") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun recordPayment(
        agreement: Agreement,
        amount: Double,
        method: PaymentMethodType,
        receiptReference: String,
        paymentDateText: String = "",
        attachment: AgreementAttachment? = null
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ تسجيل الدفعة المالية…")
        viewModelScope.launch {
            val result = repository.recordPayment(
                agreement = agreement,
                amount = amount,
                method = method,
                receiptReference = receiptReference,
                paymentDateText = paymentDateText,
                attachment = attachment
            )
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم تسجيل الدفعة وبانتظار تأكيد الطرف المستلم.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun confirmPayment(
        agreement: Agreement,
        payment: AgreementPayment,
        mode: PaymentConfirmationMode = PaymentConfirmationMode.FULL,
        confirmedAmount: Double = payment.amount,
        note: String? = null,
        witnessOtpChallengeId: String? = null
    ) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ توثيق استلام الدفعة وقفل السجل نهائياً…")
        viewModelScope.launch {
            val result = repository.confirmPayment(
                agreement = agreement,
                payment = payment,
                mode = mode,
                confirmedAmountInput = confirmedAmount,
                note = note,
                witnessOtpChallengeId = witnessOtpChallengeId
            )
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم توثيق حالة الدفعة (${mode.arabicLabel}) وقفل السجل ضد أي تعديل.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun openDispute(agreement: Agreement, disputeType: String, details: String, evidenceNotes: List<String>) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ فتح ملف النزاع وتوثيقه…")
        viewModelScope.launch {
            val result = repository.openDispute(agreement, disputeType, details, evidenceNotes)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم فتح النزاع وتحديث حالة الميثاق إلى (متنازع).") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    fun replyToDispute(agreement: Agreement, dispute: AgreementDispute, message: String, newStatus: DisputeStatus) {
        if (_actionFeedback.value is ActionFeedback.Loading) return
        _actionFeedback.value = ActionFeedback.Loading("جارٍ إرسال الرد وتحديث النزاع…")
        viewModelScope.launch {
            val result = repository.replyToDispute(agreement, dispute, message, newStatus)
            _actionFeedback.value = result.fold(
                onSuccess = { ActionFeedback.Success("تم توثيق الرد في ملف النزاع.") },
                onFailure = { ActionFeedback.Error(toArabicErrorMessage(it)) }
            )
        }
    }

    private companion object {
        const val TAG = "MeethaqVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
