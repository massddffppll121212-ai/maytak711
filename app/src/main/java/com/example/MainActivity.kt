package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AgreementRepository
import com.example.data.AuthManager
import com.example.data.AuthManager.authStateFlow
import com.example.model.ThemePreference
import com.example.ui.MeethaqAuthenticatedApp
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.InvitePublicScreen
import com.example.ui.screens.extractInviteToken
import com.example.ui.theme.MeethaqTheme
import com.example.ui.viewmodel.MeethaqViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val pendingInviteTokenFlow = MutableStateFlow<String?>(null)
    private val pendingAgreementToOpenFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractInviteToken(intent?.dataString)?.let { token ->
            pendingInviteTokenFlow.value = token
        }
        setContent {
            AppNavigation(
                pendingInviteTokenFlow = pendingInviteTokenFlow,
                pendingAgreementToOpenFlow = pendingAgreementToOpenFlow
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractInviteToken(intent.dataString)?.let { token ->
            pendingInviteTokenFlow.value = token
        }
    }
}

@Composable
fun AppNavigation(
    pendingInviteTokenFlow: MutableStateFlow<String?>,
    pendingAgreementToOpenFlow: MutableStateFlow<String?>
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var unauthThemePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }

    val pendingInviteToken by pendingInviteTokenFlow.collectAsStateWithLifecycle()
    val pendingAgreementToOpen by pendingAgreementToOpenFlow.collectAsStateWithLifecycle()
    var bypassInviteForSignIn by remember { mutableStateOf(false) }

    val user = currentUser
    // ProtectedRoute Gate: redirects all internal screens to AuthScreen when unauthenticated,
    // except public /invite/{token} preview + OTP verification flow.
    if (user == null) {
        MeethaqTheme(themePreference = unauthThemePreference) {
            val activeToken = pendingInviteToken
            if (activeToken != null && !bypassInviteForSignIn) {
                InvitePublicScreen(
                    token = activeToken,
                    isSignedIn = false,
                    currentUserEmail = "",
                    currentUserPhone = "",
                    onBack = { pendingInviteTokenFlow.value = null },
                    onRequestSignIn = { bypassInviteForSignIn = true },
                    onVerifiedAndReady = { agreementId ->
                        pendingAgreementToOpenFlow.value = agreementId
                        pendingInviteTokenFlow.value = null
                        bypassInviteForSignIn = false
                    }
                )
            } else {
                AuthScreen(
                    themePreference = unauthThemePreference,
                    onToggleTheme = { unauthThemePreference = it },
                    onAuthSuccess = { bypassInviteForSignIn = false },
                    onOpenInviteToken = { token ->
                        bypassInviteForSignIn = false
                        pendingInviteTokenFlow.value = token
                    }
                )
            }
        }
    } else {
        val uid = user.uid
        val email = user.email?.trim()?.lowercase(Locale.US).orEmpty()
        val phone = user.phoneNumber?.trim().orEmpty()
        val displayName = user.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: email.substringBefore("@").ifEmpty { phone.ifEmpty { "مستخدم ميثاق" } }
        val photoUrl = user.photoUrl?.toString()

        LaunchedEffect(uid) {
            bypassInviteForSignIn = false
        }

        key(uid) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val credentialManager = remember(context) { CredentialManager.create(context) }

            val viewModel: MeethaqViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MeethaqViewModel(
                            repository = AgreementRepository(db),
                            appContext = app.applicationContext,
                            currentUserId = uid,
                            currentUserEmail = email,
                            currentUserPhone = phone
                        )
                    }
                }
            )

            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val sessionRevoked by viewModel.sessionRevokedExternally.collectAsStateWithLifecycle()
            val effectivePhone = userProfile?.phone?.ifBlank { phone } ?: phone

            LaunchedEffect(sessionRevoked) {
                if (sessionRevoked) {
                    AuthManager.signOut(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            }

            MeethaqTheme(themePreference = themePreference) {
                val activeToken = pendingInviteToken
                if (activeToken != null) {
                    InvitePublicScreen(
                        token = activeToken,
                        isSignedIn = true,
                        currentUserEmail = email,
                        currentUserPhone = effectivePhone,
                        onBack = { pendingInviteTokenFlow.value = null },
                        onRequestSignIn = { pendingInviteTokenFlow.value = null },
                        onVerifiedAndReady = { agreementId ->
                            viewModel.selectAgreement(agreementId)
                            pendingAgreementToOpenFlow.value = agreementId
                            pendingInviteTokenFlow.value = null
                        }
                    )
                } else {
                    MeethaqAuthenticatedApp(
                        viewModel = viewModel,
                        displayName = displayName,
                        photoUrl = photoUrl,
                        pendingAgreementToOpen = pendingAgreementToOpen,
                        onConsumedPendingAgreement = { pendingAgreementToOpenFlow.value = null },
                        onOpenInviteToken = { token -> pendingInviteTokenFlow.value = token },
                        onSignOutClick = {
                            viewModel.revokeSession(viewModel.currentSessionId) {
                                AuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        },
                        onSignOutAllDevicesClick = {
                            viewModel.revokeAllSessions {
                                AuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

