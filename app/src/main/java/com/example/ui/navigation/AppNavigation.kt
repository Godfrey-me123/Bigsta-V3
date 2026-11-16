package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.features.admin.AdminConsoleScreen
import com.example.features.admin.AdminViewModel
import com.example.features.authentication.AuthScreen
import com.example.features.authentication.AuthViewModel
import com.example.features.notifications.NotificationsScreen
import com.example.features.payments.BillingRechargeScreen
import com.example.features.payments.PaymentsViewModel
import com.example.features.settings.SettingsScreen
import com.example.features.sms.SmsConsentScreen
import com.example.features.sms.SmsViewModel
import com.example.features.user.DocumentToolsScreen
import com.example.features.user.DownloadsScreen
import com.example.features.user.DocxToPdfScreen
import com.example.features.user.TemplateLibraryScreen
import com.example.features.user.ServiceCatalogScreen
import com.example.features.user.GovernmentFormsScreen
import com.example.features.user.IdCardStudioScreen
import com.example.features.user.ImageStudioScreen
import com.example.features.user.TemplateStudioEditorScreen
import com.example.features.user.UserDashboardScreen
import com.example.features.user.UserDashboardViewModel
import kotlinx.coroutines.launch

object BigstaRoutes {
    const val DASHBOARD = "dashboard"
    const val ID_STUDIO = "id_studio"
    const val TEMPLATE_EDITOR = "template_editor"
    const val FORMS = "forms"
    const val PDF_TOOLS = "pdf_tools"
    const val PDF_LIBRARY = "pdf_library"
    const val DOCX_TO_PDF = "docx_to_pdf"
    const val DOWNLOADS = "downloads"
    const val TEMPLATES = "templates"
    const val PASSPORT_STUDIO = "passport_studio"
    const val SERVICE_CATALOG = "services"
    const val IMAGE_STUDIO = "image_studio"
    const val BILLING = "billing"
    const val SMS = "sms"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN = "admin"
    const val SETTINGS = "settings"
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val topNavItems = listOf(
    TopNavItem(BigstaRoutes.DASHBOARD, "Home", Icons.Default.Home, "bottom_nav_home"),
    TopNavItem(BigstaRoutes.ID_STUDIO, "Studio", Icons.Default.Badge, "bottom_nav_studio"),
    TopNavItem(BigstaRoutes.BILLING, "Billing", Icons.Default.AccountBalanceWallet, "bottom_nav_billing"),
    TopNavItem(BigstaRoutes.SMS, "SMS", Icons.Default.Sms, "bottom_nav_sms"),
    TopNavItem(BigstaRoutes.SETTINGS, "Settings", Icons.Default.Settings, "bottom_nav_settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigstaAppRoot(
    appContainer: AppContainer,
    sharedSmsText: String?,
    onConsumedSharedSms: () -> Unit,
    notificationTargetRoute: String?,
    onConsumedNotificationRoute: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(appContainer.authRepository)
    )
    val session by authViewModel.session.collectAsStateWithLifecycle()

    if (!session.isAuthenticated) {
        AuthScreen(viewModel = authViewModel)
        return
    }

    val dashboardViewModel: UserDashboardViewModel = viewModel(
        factory = UserDashboardViewModel.Factory(
            authRepository = appContainer.authRepository,
            serviceRequestRepository = appContainer.serviceRequestRepository,
            paymentRepository = appContainer.paymentRepository,
            templateRepository = appContainer.templateRepository,
            connectivityMonitor = appContainer.connectivityMonitor,
            localDownloadDao = appContainer.database.localDownloadDao(),
            notificationManager = appContainer.notificationManager
        )
    )
    val paymentsViewModel: PaymentsViewModel = viewModel(
        factory = PaymentsViewModel.Factory(
            paymentRepository = appContainer.paymentRepository,
            notificationManager = appContainer.notificationManager
        )
    )
    val smsViewModel: SmsViewModel = viewModel(
        factory = SmsViewModel.Factory(
            smsRepository = appContainer.smsTransactionRepository,
            appPreferences = appContainer.appPreferences,
            authRepository = appContainer.authRepository
        )
    )
    val adminViewModel: AdminViewModel = viewModel(
        factory = AdminViewModel.Factory(
            adminRepository = appContainer.adminRepository,
            authRepository = appContainer.authRepository,
            notificationManager = appContainer.notificationManager
        )
    )

    val unreadNotifications by appContainer.database.appNotificationDao()
        .observeUnreadCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateSafe(route: String) {
        val adminOnlyRoutes = setOf(BigstaRoutes.ADMIN, BigstaRoutes.TEMPLATE_EDITOR, BigstaRoutes.TEMPLATES)
        if (route in adminOnlyRoutes && !session.isAdmin) {
            return
        }
        if (topNavItems.any { it.route == route }) {
            navigateTopLevel(route)
        } else {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(sharedSmsText) {
        if (!sharedSmsText.isNullOrBlank()) {
            smsViewModel.onIncomingSharedSms(sharedSmsText)
            navigateSafe(BigstaRoutes.SMS)
            onConsumedSharedSms()
        }
    }

    LaunchedEffect(notificationTargetRoute) {
        if (!notificationTargetRoute.isNullOrBlank()) {
            navigateSafe(notificationTargetRoute)
            onConsumedNotificationRoute()
        }
    }

    val showTopChrome = topNavItems.any { it.route == currentRoute }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ServiceMenuDrawer(
                session = session,
                currentRoute = currentRoute,
                onNavigate = { route -> navigateSafe(route) },
                onSignOut = { authViewModel.signOut() },
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTopChrome && !useNavRail) {
                        NavigationBar {
                            topNavItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
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
                    if (showTopChrome && useNavRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            topNavItems.forEach { item ->
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = BigstaRoutes.DASHBOARD
                        ) {
                            composable(BigstaRoutes.DASHBOARD) {
                                UserDashboardScreen(
                                    viewModel = dashboardViewModel,
                                    unreadNotifications = unreadNotifications,
                                    onNavigate = { route -> navigateSafe(route) },
                                    onSelectOffer = { offer ->
                                        paymentsViewModel.selectOfferAsPackage(offer)
                                        navigateSafe(BigstaRoutes.BILLING)
                                    },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                            }
                            composable(BigstaRoutes.ID_STUDIO) {
                                IdCardStudioScreen(
                                    viewModel = dashboardViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(BigstaRoutes.TEMPLATE_EDITOR) {
                                if (!session.isAdmin) {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                } else {
                                    TemplateStudioEditorScreen(
                                        viewModel = dashboardViewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable(BigstaRoutes.FORMS) {
                                GovernmentFormsScreen(
                                    viewModel = dashboardViewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenStudio = { navigateSafe(BigstaRoutes.TEMPLATE_EDITOR) }
                                )
                            }
                            composable(BigstaRoutes.SERVICE_CATALOG) {
                                ServiceCatalogScreen(onBack = { navController.popBackStack() }, onNavigate = { route -> navigateSafe(route) })
                            }
                            composable("${BigstaRoutes.FORMS}/{serviceType}") { entry ->
                                val serviceType = entry.arguments?.getString("serviceType").orEmpty()
                                GovernmentFormsScreen(
                                    viewModel = dashboardViewModel,
                                    onBack = { navController.popBackStack() },
                                    initialServiceType = serviceType,
                                    onOpenStudio = { navigateSafe(BigstaRoutes.TEMPLATE_EDITOR) }
                                )
                            }
                            composable(BigstaRoutes.TEMPLATES) {
                                if (!session.isAdmin) {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                } else {
                                    TemplateLibraryScreen(
                                        viewModel = dashboardViewModel,
                                        onBack = { navController.popBackStack() },
                                        onOpenStudio = { navigateSafe(BigstaRoutes.TEMPLATE_EDITOR) }
                                    )
                                }
                            }
                            composable(BigstaRoutes.DOWNLOADS) {
                                DownloadsScreen(viewModel = dashboardViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(BigstaRoutes.PDF_LIBRARY) {
                                DocumentToolsScreen(viewModel = dashboardViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(BigstaRoutes.DOCX_TO_PDF) {
                                DocxToPdfScreen(viewModel = dashboardViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(BigstaRoutes.PASSPORT_STUDIO) {
                                ImageStudioScreen(viewModel = dashboardViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(BigstaRoutes.PDF_TOOLS) {
                                DocumentToolsScreen(
                                    viewModel = dashboardViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(BigstaRoutes.IMAGE_STUDIO) {
                                ImageStudioScreen(
                                    viewModel = dashboardViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(BigstaRoutes.BILLING) {
                                BillingRechargeScreen(
                                    viewModel = paymentsViewModel,
                                    currentBalance = dashboardState.profile?.effectiveTokens ?: 0,
                                    onOpenSmsIntake = { navigateSafe(BigstaRoutes.SMS) },
                                    onBack = null
                                )
                            }
                            composable(BigstaRoutes.SMS) {
                                SmsConsentScreen(
                                    viewModel = smsViewModel,
                                    onBack = null
                                )
                            }
                            composable(BigstaRoutes.NOTIFICATIONS) {
                                NotificationsScreen(
                                    notificationDao = appContainer.database.appNotificationDao(),
                                    appPreferences = appContainer.appPreferences,
                                    onNavigateRoute = { route -> navigateSafe(route) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(BigstaRoutes.ADMIN) {
                                if (!session.isAdmin) {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                } else {
                                    AdminConsoleScreen(
                                        viewModel = adminViewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable(BigstaRoutes.SETTINGS) {
                                SettingsScreen(
                                    authViewModel = authViewModel,
                                    dashboardViewModel = dashboardViewModel,
                                    onNavigate = { route -> navigateSafe(route) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
