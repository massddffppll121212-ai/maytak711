package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.MeethaqShieldLogo
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AgreementDetailScreen
import com.example.ui.screens.AgreementsListScreen
import com.example.ui.screens.CreateAgreementScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.VerifyScreen
import com.example.ui.theme.EditOrange
import com.example.ui.theme.GoldMetallic
import com.example.ui.viewmodel.MeethaqViewModel

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_AGREEMENTS = "agreements"
const val ROUTE_CREATE = "create"
const val ROUTE_VERIFY = "verify"
const val ROUTE_ACCOUNT = "account"
const val ROUTE_DETAIL = "detail"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val mainTabs = listOf(
    NavTab(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home, "nav_dashboard"),
    NavTab(ROUTE_AGREEMENTS, R.string.nav_agreements, Icons.Filled.Description, Icons.Outlined.Description, "nav_agreements"),
    NavTab(ROUTE_CREATE, R.string.nav_create, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_create"),
    NavTab(ROUTE_VERIFY, R.string.nav_verify, Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint, "nav_verify"),
    NavTab(ROUTE_ACCOUNT, R.string.nav_account, Icons.Filled.Person, Icons.Outlined.Person, "nav_account")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeethaqAuthenticatedApp(
    viewModel: MeethaqViewModel,
    displayName: String,
    photoUrl: String?,
    pendingAgreementToOpen: String? = null,
    onConsumedPendingAgreement: () -> Unit = {},
    onOpenInviteToken: (String) -> Unit = {},
    onSignOutClick: () -> Unit,
    onSignOutAllDevicesClick: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val newDeviceAlert by viewModel.newDeviceAlert.collectAsStateWithLifecycle()
    val agreementsState by viewModel.agreementsState.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val invitationsState by viewModel.invitationsState.collectAsStateWithLifecycle()
    val selectedAgreementState by viewModel.selectedAgreementState.collectAsStateWithLifecycle()
    val selectedAgreementPayments by viewModel.selectedAgreementPayments.collectAsStateWithLifecycle()
    val selectedAgreementDisputes by viewModel.selectedAgreementDisputes.collectAsStateWithLifecycle()
    val selectedAgreementAudit by viewModel.selectedAgreementAudit.collectAsStateWithLifecycle()
    val selectedAgreementSignatures by viewModel.selectedAgreementSignatures.collectAsStateWithLifecycle()
    val selectedAgreementAmendments by viewModel.selectedAgreementAmendments.collectAsStateWithLifecycle()
    val activeOtpChallenge by viewModel.activeOtpChallenge.collectAsStateWithLifecycle()
    val localOtpVerifiedAgreementId by viewModel.localOtpVerifiedAgreementId.collectAsStateWithLifecycle()
    val localOtpChallengeId by viewModel.localOtpChallengeId.collectAsStateWithLifecycle()
    val pendingBiometricSignature by viewModel.pendingBiometricSignature.collectAsStateWithLifecycle()
    val editingAgreement by viewModel.editingAgreement.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

    fun navigateToTab(route: String) {
        viewModel.clearActionFeedback()
        if (route != ROUTE_CREATE) {
            viewModel.startEditingAgreement(null)
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openAgreementDetail(agreementId: String) {
        viewModel.clearActionFeedback()
        viewModel.selectAgreement(agreementId)
        navController.navigate(ROUTE_DETAIL) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(pendingAgreementToOpen) {
        val id = pendingAgreementToOpen
        if (!id.isNullOrBlank()) {
            openAgreementDetail(id)
            onConsumedPendingAgreement()
        }
    }

    val resolvedPhone = userProfile?.phone?.ifBlank { viewModel.currentUserPhone } ?: viewModel.currentUserPhone
    val resolvedName = userProfile?.fullName?.ifBlank { displayName } ?: displayName

    val showChrome = currentRoute != ROUTE_DETAIL

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (showChrome) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MeethaqShieldLogo(size = 34.dp)
                                Text(
                                    text = stringResource(R.string.app_full_brand),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            bottomBar = {
                if (showChrome && !useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        mainTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GoldMetallic,
                                    selectedTextColor = GoldMetallic,
                                    indicatorColor = GoldMetallic.copy(alpha = 0.16f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            mainTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                val label = stringResource(tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(tab.testTag),
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = GoldMetallic,
                                        selectedTextColor = GoldMetallic,
                                        indicatorColor = GoldMetallic.copy(alpha = 0.16f)
                                    )
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // In-App New Device Sign-In Notification Banner
                    if (newDeviceAlert != null && showChrome) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = EditOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, EditOrange)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = EditOrange)
                                Text(
                                    text = newDeviceAlert!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissNewDeviceAlert() }) {
                                    Text("حسناً")
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    agreementsState = agreementsState,
                                    currentUserName = resolvedName,
                                    currentUserEmail = viewModel.currentUserEmail,
                                    currentUserPhone = resolvedPhone,
                                    currentUserId = viewModel.currentUserId,
                                    onCreateAgreementClick = {
                                        viewModel.startEditingAgreement(null)
                                        navigateToTab(ROUTE_CREATE)
                                    },
                                    onViewAllAgreementsClick = { navigateToTab(ROUTE_AGREEMENTS) },
                                    onOpenVerifyClick = { navigateToTab(ROUTE_VERIFY) },
                                    onOpenAccountClick = { navigateToTab(ROUTE_ACCOUNT) },
                                    onOpenInviteToken = onOpenInviteToken,
                                    onAgreementClick = { openAgreementDetail(it) }
                                )
                            }

                            composable(ROUTE_AGREEMENTS) {
                                AgreementsListScreen(
                                    agreementsState = agreementsState,
                                    selectedFilter = selectedFilter,
                                    searchQuery = searchQuery,
                                    currentUserEmail = viewModel.currentUserEmail,
                                    currentUserId = viewModel.currentUserId,
                                    onFilterSelected = viewModel::setFilter,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onAgreementClick = { openAgreementDetail(it) },
                                    onCreateAgreementClick = {
                                        viewModel.startEditingAgreement(null)
                                        navigateToTab(ROUTE_CREATE)
                                    }
                                )
                            }

                            composable(ROUTE_CREATE) {
                                CreateAgreementScreen(
                                    currentUserId = viewModel.currentUserId,
                                    currentUserName = resolvedName,
                                    currentUserEmail = viewModel.currentUserEmail,
                                    currentUserPhone = resolvedPhone,
                                    editingAgreement = editingAgreement,
                                    actionFeedback = actionFeedback,
                                    onSaveSubmit = viewModel::saveAgreement,
                                    onAutoSaveCloudDraft = viewModel::autoSaveCloudDraft,
                                    onCancelEditing = {
                                        viewModel.startEditingAgreement(null)
                                        navigateToTab(ROUTE_AGREEMENTS)
                                    },
                                    onAgreementSaved = { newId ->
                                        viewModel.selectAgreement(newId)
                                        navController.navigate(ROUTE_DETAIL) {
                                            popUpTo(ROUTE_DASHBOARD) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onClearFeedback = viewModel::clearActionFeedback
                                )
                            }

                            composable(ROUTE_VERIFY) {
                                VerifyScreen(
                                    agreementsState = agreementsState,
                                    vaultState = vaultState,
                                    invitationsState = invitationsState,
                                    actionFeedback = actionFeedback,
                                    onOpenAgreement = { openAgreementDetail(it) },
                                    onOpenInviteToken = onOpenInviteToken,
                                    onAddVaultDocument = viewModel::addVaultDocument,
                                    onClearFeedback = viewModel::clearActionFeedback
                                )
                            }

                            composable(ROUTE_ACCOUNT) {
                                AccountScreen(
                                    displayName = displayName,
                                    email = viewModel.currentUserEmail,
                                    photoUrl = photoUrl,
                                    currentSessionId = viewModel.currentSessionId,
                                    userProfile = userProfile,
                                    sessionsState = sessionsState,
                                    themePreference = themePreference,
                                    actionFeedback = actionFeedback,
                                    onThemeSelected = viewModel::updateThemePreference,
                                    onUpdateProfile = viewModel::updateUserProfile,
                                    onPhoneVerified = viewModel::markPhoneVerified,
                                    onChangePassword = viewModel::changePassword,
                                    onRevokeSession = { sid -> viewModel.revokeSession(sid) },
                                    onSignOutClick = onSignOutClick,
                                    onSignOutAllDevicesClick = onSignOutAllDevicesClick,
                                    onClearFeedback = viewModel::clearActionFeedback
                                )
                            }

                            composable(ROUTE_DETAIL) {
                                val selectedAgreement = (selectedAgreementState as? com.example.model.UiState.Success)?.data
                                val otpVerifiedHere = selectedAgreement != null && localOtpVerifiedAgreementId == selectedAgreement.id
                                val verifiedChallengeHere = if (otpVerifiedHere) localOtpChallengeId else null
                                AgreementDetailScreen(
                                    agreementState = selectedAgreementState,
                                    paymentsState = selectedAgreementPayments,
                                    disputesState = selectedAgreementDisputes,
                                    auditState = selectedAgreementAudit,
                                    invitationsState = invitationsState,
                                    signaturesState = selectedAgreementSignatures,
                                    amendmentsState = selectedAgreementAmendments,
                                    currentUserId = viewModel.currentUserId,
                                    currentUserEmail = viewModel.currentUserEmail,
                                    currentUserPhone = resolvedPhone,
                                    activeOtpChallenge = activeOtpChallenge,
                                    otpVerifiedLocally = otpVerifiedHere,
                                    verifiedWitnessOtpChallengeId = verifiedChallengeHere,
                                    pendingBiometricSignature = pendingBiometricSignature,
                                    actionFeedback = actionFeedback,
                                    onBack = {
                                        viewModel.clearActionFeedback()
                                        navController.popBackStack()
                                    },
                                    onEditDraftOrNeedsEdit = { ag ->
                                        viewModel.clearActionFeedback()
                                        viewModel.startEditingAgreement(ag)
                                        navController.navigate(ROUTE_CREATE) {
                                            popUpTo(ROUTE_DASHBOARD) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onSendInvitations = viewModel::sendInvitations,
                                    onArchiveAgreement = { ag ->
                                        viewModel.archiveAgreement(ag) {
                                            navController.popBackStack()
                                        }
                                    },
                                    onRequestOtp = viewModel::requestAgreementOtp,
                                    onVerifyOtp = viewModel::verifyAgreementOtp,
                                    onBiometricSigned = viewModel::onBiometricSignedLocally,
                                    onSignAgreement = viewModel::signAgreementDualFactor,
                                    onApproveAgreement = viewModel::approveAgreement,
                                    onRequestAmendment = viewModel::requestAmendment,
                                    onRespondToAmendment = viewModel::respondToAmendment,
                                    onRequestEdits = viewModel::requestEdits,
                                    onMarkExpired = viewModel::markAgreementExpired,
                                    onRecordPayment = viewModel::recordPayment,
                                    onConfirmPayment = viewModel::confirmPayment,
                                    onOpenDispute = viewModel::openDispute,
                                    onReplyToDispute = viewModel::replyToDispute,
                                    onLocalFeedbackError = viewModel::setLocalError,
                                    onClearFeedback = viewModel::clearActionFeedback
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
