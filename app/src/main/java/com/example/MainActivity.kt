package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.i18n.AppLanguage
import com.example.i18n.ArabicStrings
import com.example.i18n.EnglishStrings
import com.example.i18n.LocalAppStrings
import com.example.ui.components.FilePickerOptionSheet
import com.example.ui.navigation.AboutRoute
import com.example.ui.navigation.DiscoverRoute
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PrivacyRoute
import com.example.ui.navigation.QrScannerRoute
import com.example.ui.navigation.ReceiveRoute
import com.example.ui.navigation.SendFlowRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TransferDetailRoute
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FilesBrowserScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendFlowScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransferDetailScreen
import com.example.ui.theme.DropLinkTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DropLinkRoot()
        }
    }
}

@Composable
fun DropLinkRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val strings = if (settings.language == AppLanguage.AR) ArabicStrings else EnglishStrings
    val layoutDirection = if (settings.language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        DropLinkTheme(themeMode = settings.themeMode) {
            DropLinkNavScaffold(vm = vm)
        }
    }
}

@Composable
private fun DropLinkNavScaffold(vm: MainViewModel) {
    val s = LocalAppStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val activeTasks by vm.activeTasks.collectAsStateWithLifecycle()
    val receiveSession by vm.receiveSession.collectAsStateWithLifecycle()
    val sendShareSession by vm.sendShareSession.collectAsStateWithLifecycle()
    val storageSummary by vm.storageSummary.collectAsStateWithLifecycle()
    val receivedFiles by vm.receivedFiles.collectAsStateWithLifecycle()
    val peers by vm.allPeers.collectAsStateWithLifecycle()
    val isNsdDiscovering by vm.isNsdDiscovering.collectAsStateWithLifecycle()
    val wifiDirectState by vm.wifiDirectState.collectAsStateWithLifecycle()
    val bluetoothState by vm.bluetoothState.collectAsStateWithLifecycle()
    val allTransfers by vm.allTransfers.collectAsStateWithLifecycle()
    val recentTransfers by vm.recentTransfers.collectAsStateWithLifecycle()
    val trustedDevices by vm.trustedDevices.collectAsStateWithLifecycle()
    val selectedFiles by vm.selectedFilesForSend.collectAsStateWithLifecycle()

    var showPickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val openMultipleDocsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addPickedUris(uris)
            navController.navigate(SendFlowRoute) { launchSingleTop = true }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addPickedUris(uris)
            navController.navigate(SendFlowRoute) { launchSingleTop = true }
        }
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            vm.addPickedFolderTree(uri)
            navController.navigate(SendFlowRoute) { launchSingleTop = true }
        }
    }

    if (showPickerSheet) {
        FilePickerOptionSheet(
            onDismiss = { showPickerSheet = false },
            onPickAnyFiles = { openMultipleDocsLauncher.launch(arrayOf("*/*")) },
            onPickMedia = {
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onPickFolder = { openFolderLauncher.launch(null) }
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isTopLevel = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<DiscoverRoute>() ||
            dest.hasRoute<HistoryRoute>() ||
            dest.hasRoute<FilesRoute>()
    } ?: true

    val navItems = listOf(
        Triple(HomeRoute, s.navHome, Icons.Rounded.Home),
        Triple(DiscoverRoute, s.navDiscover, Icons.Rounded.Devices),
        Triple(HistoryRoute, s.navHistory, Icons.Rounded.History),
        Triple(FilesRoute, s.navFiles, Icons.Rounded.Folder)
    )

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isTopLevel && useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    navItems.forEach { (route, label, icon) ->
                        val selected = when (route) {
                            HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                            DiscoverRoute -> currentDest?.hasRoute<DiscoverRoute>() == true
                            HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                            FilesRoute -> currentDest?.hasRoute<FilesRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(route) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (isTopLevel && !useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { (route, label, icon) ->
                                val selected = when (route) {
                                    HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    DiscoverRoute -> currentDest?.hasRoute<DiscoverRoute>() == true
                                    HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                                    FilesRoute -> currentDest?.hasRoute<FilesRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_${route::class.simpleName}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable<HomeRoute> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            HomeScreen(
                                receiveSession = receiveSession,
                                sendShareSession = sendShareSession,
                                activeTasks = activeTasks,
                                peers = peers,
                                recentTransfers = recentTransfers,
                                storageSummary = storageSummary,
                                isDiscovering = isNsdDiscovering,
                                onOpenSendPicker = { showPickerSheet = true },
                                onStartReceive = {
                                    vm.startReceiveSession()
                                    navController.navigate(ReceiveRoute)
                                },
                                onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onRefreshDiscovery = { vm.startDiscovery() },
                                onPeerClick = { peer ->
                                    if (selectedFiles.isEmpty()) {
                                        showPickerSheet = true
                                    } else {
                                        vm.sendSelectedFilesToPeer(peer)
                                        navController.navigate(SendFlowRoute)
                                    }
                                },
                                onCancelTask = { vm.cancelTransfer(it) },
                                onOpenTransferDetail = { id -> navController.navigate(TransferDetailRoute(id)) },
                                onOpenAllHistory = { navigateTopLevel(HistoryRoute) },
                                onOpenAllDevices = { navigateTopLevel(DiscoverRoute) },
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable<DiscoverRoute> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            DiscoverScreen(
                                peers = peers,
                                isNsdDiscovering = isNsdDiscovering,
                                wifiDirectState = wifiDirectState,
                                bluetoothState = bluetoothState,
                                onRefreshDiscovery = { vm.startDiscovery() },
                                onConnectManual = { input -> vm.connectManualPeer(input) },
                                onSelectPeerToSend = { peer ->
                                    if (selectedFiles.isEmpty()) {
                                        showPickerSheet = true
                                    } else {
                                        vm.sendSelectedFilesToPeer(peer)
                                        navController.navigate(SendFlowRoute)
                                    }
                                },
                                onConnectWifiDirect = { vm.connectWifiDirectPeer(it) },
                                onCreateWifiDirectGroup = { vm.createWifiDirectGroup() },
                                onStartBluetoothScan = { vm.startBluetoothScan() },
                                onStartBluetoothListen = { vm.startBluetoothReceive() },
                                onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable<HistoryRoute> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            HistoryScreen(
                                transfers = allTransfers,
                                onOpenDetail = { id -> navController.navigate(TransferDetailRoute(id)) },
                                onClearHistory = { vm.clearHistory() },
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable<FilesRoute> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            FilesBrowserScreen(
                                files = receivedFiles,
                                onRefresh = { vm.refreshFilesAndStorage() },
                                onOpenFile = { rec -> vm.openFile(rec.uri.toString(), rec.mimeType) },
                                onShareFile = { rec -> vm.shareFile(rec.uri.toString(), rec.mimeType) },
                                onSendFile = { rec ->
                                    vm.addReceivedFilesToSend(listOf(rec))
                                    navController.navigate(SendFlowRoute)
                                },
                                onDeleteFile = { rec -> vm.deleteReceivedFile(rec) },
                                onOpenSaveFolder = { vm.openSaveFolder() },
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable<SendFlowRoute> {
                        SendFlowScreen(
                            selectedFiles = selectedFiles,
                            peers = peers,
                            activeTasks = activeTasks,
                            shareSession = sendShareSession,
                            onBack = { navController.popBackStack() },
                            onAddMoreFiles = { showPickerSheet = true },
                            onRemoveFile = { vm.removeSelectedFile(it) },
                            onClearFiles = { vm.clearSelectedFiles() },
                            onStartBrowserShare = { vm.startBrowserShareSession() },
                            onStopBrowserShare = { vm.stopBrowserShareSession() },
                            onSendToPeer = { peer -> vm.sendSelectedFilesToPeer(peer) },
                            onCancelTask = { vm.cancelTransfer(it) },
                            onClearFinishedTasks = { vm.clearFinishedTasks() },
                            onOpenQrScanner = { navController.navigate(QrScannerRoute) }
                        )
                    }

                    composable<ReceiveRoute> {
                        ReceiveScreen(
                            receiveSession = receiveSession,
                            activeTasks = activeTasks,
                            onBack = { navController.popBackStack() },
                            onStartSession = { vm.startReceiveSession() },
                            onStopSession = { vm.stopReceiveSession() },
                            onRespondOffer = { id, accept, trust -> vm.respondToPendingOffer(id, accept, trust) },
                            onCancelTask = { vm.cancelTransfer(it) },
                            onOpenSaveFolder = { vm.openSaveFolder() }
                        )
                    }

                    composable<QrScannerRoute> {
                        QrScannerScreen(
                            onBack = { navController.popBackStack() },
                            onQrDetected = { raw ->
                                vm.handleQrPayload(
                                    raw = raw,
                                    onNavigateToSend = {
                                        navController.popBackStack()
                                        navController.navigate(SendFlowRoute)
                                    },
                                    onNavigateToReceiveOrHome = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        )
                    }

                    composable<TransferDetailRoute> { entry ->
                        val route = entry.toRoute<TransferDetailRoute>()
                        val transfer by vm.observeTransferById(route.transferId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        TransferDetailScreen(
                            transfer = transfer,
                            onBack = { navController.popBackStack() },
                            onOpenFile = { uri, mime -> vm.openFile(uri, mime) },
                            onShareFile = { uri, mime -> vm.shareFile(uri, mime) },
                            onDeleteRecord = { id -> vm.deleteTransferRecord(id) }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            settings = settings,
                            onBack = { navController.popBackStack() },
                            onThemeChange = { vm.setThemeMode(it) },
                            onLanguageChange = { vm.setLanguage(it) },
                            onDeviceNameSave = { vm.setDeviceName(it) },
                            onRequireApprovalChange = { vm.setRequireApproval(it) },
                            onAutoAcceptTrustedChange = { vm.setAutoAcceptTrusted(it) },
                            onNotificationsChange = { vm.setNotificationsEnabled(it) },
                            onSaveFolderSelected = { vm.setCustomSaveFolder(it) },
                            onOpenPrivacy = { navController.navigate(PrivacyRoute) },
                            onOpenAbout = { navController.navigate(AboutRoute) }
                        )
                    }

                    composable<PrivacyRoute> {
                        PrivacyScreen(
                            settings = settings,
                            trustedDevices = trustedDevices,
                            onBack = { navController.popBackStack() },
                            onSessionTimeoutChange = { vm.setSessionTimeout(it) },
                            onHideWhenIdleChange = { vm.setHideWhenIdle(it) },
                            onRevokeTrustedDevice = { vm.revokeTrustedDevice(it) }
                        )
                    }

                    composable<AboutRoute> {
                        AboutScreen(
                            language = settings.language,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
