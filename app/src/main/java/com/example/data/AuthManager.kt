package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AuthManager {

    private const val TAG = "Auth"

    fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener(listener) }
    }

    /**
     * تحويل أخطاء المصادقة إلى رسائل عامة آمنة لا تكشف وجود الحساب أو تفاصيل داخلية.
     */
    fun toGenericAuthError(error: Throwable?): String {
        return when (error) {
            is NoCredentialException ->
                "لا يوجد حساب Google مسجّل على هذا الجهاز حالياً. أضف حساب Google من إعدادات الجهاز ثم أعد المحاولة."
            is GetCredentialException ->
                "تعذّر فتح نافذة اختيار حساب Google. تأكد من تفعيل خدمات Google وحاول مجدداً."
            is FirebaseTooManyRequestsException ->
                "تم تجاوز عدد المحاولات المسموح به مؤقتاً. يرجى المحاولة لاحقاً."
            is FirebaseAuthException -> {
                val code = error.errorCode
                when {
                    code.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                        "تم تجاوز الحد المسموح للمحاولات. يرجى المحاولة لاحقاً."
                    code.contains("CREDENTIAL_ALREADY_IN_USE", ignoreCase = true) ->
                        "هذا الحساب مرتبط بمستخدم آخر بالفعل."
                    code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                        code.contains("APP_NOT_AUTHORIZED", ignoreCase = true) ||
                        code.contains("MISSING_CLIENT_IDENTIFIER", ignoreCase = true) ->
                        "طريقة المصادقة المحددة غير مفعّلة حالياً، يرجى استخدام الدخول السريع عبر Google."
                    code.contains("NETWORK", ignoreCase = true) ->
                        "تعذّر الاتصال بالخادم، تحقق من اتصالك بالإنترنت."
                    else -> "تعذّر إتمام تسجيل الدخول. يرجى التحقق من اتصالك والمحاولة عبر Google."
                }
            }
            else -> "حدث خطأ غير متوقع، يرجى المحاولة لاحقاً."
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val activityContext = context as? Activity ?: run {
                    onUnauthenticated()
                    return@launch
                }
                val cred = credentialManager.getCredential(activityContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    AuthRateLimiter(context).resetOnSuccess()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        val rateLimiter = AuthRateLimiter(context)
        val status = rateLimiter.checkAllowed()
        if (!status.allowed) {
            val mins = ((status.waitSeconds + 59L) / 60L).coerceAtLeast(1L)
            onAuthError("تم إيقاف محاولات الدخول مؤقتاً. يرجى المحاولة بعد $mins دقيقة.")
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError(context.getString(R.string.error_config_missing))
            return
        }

        val activity = context as? Activity ?: run {
            onAuthError("تعذّر إتمام الطلب حالياً، حاول لاحقاً.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    rateLimiter.resetOnSuccess()
                    onAuthSuccess()
                } else {
                    rateLimiter.recordFailure()
                    onAuthError("تعذّر التحقق من بيانات الاعتماد، حاول لاحقاً.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Credential Manager error: ${e.message}", e)
                onAuthError(toGenericAuthError(e))
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                rateLimiter.recordFailure()
                onAuthError(toGenericAuthError(e))
            }
        }
    }

    fun hasPasswordProvider(user: FirebaseUser?): Boolean {
        return user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
    }

    suspend fun updateLocalPasswordIfSupported(newPassword: String): Result<Unit> {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("يجب تسجيل الدخول أولاً."))
        if (!hasPasswordProvider(user)) {
            return Result.failure(
                IllegalStateException("حسابك مسجّل وموثّق عبر Google ولا يستخدم كلمة مرور محلية.")
            )
        }
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("كلمة المرور يجب أن تكون 8 أحرف على الأقل."))
        }
        return try {
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Password update failed", e)
            Result.failure(IllegalStateException(toGenericAuthError(e)))
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}

