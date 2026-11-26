package com.qrcommunication.smsforwarder.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.ui.history.HistoryScreen
import com.qrcommunication.smsforwarder.ui.permissions.BatteryBanner
import com.qrcommunication.smsforwarder.ui.permissions.BatteryDialog
import com.qrcommunication.smsforwarder.ui.permissions.PermissionGate
import com.qrcommunication.smsforwarder.ui.permissions.SystemState
import com.qrcommunication.smsforwarder.ui.rules.RuleEditScreen
import com.qrcommunication.smsforwarder.ui.rules.RuleEditViewModel
import com.qrcommunication.smsforwarder.ui.rules.RulesScreen
import com.qrcommunication.smsforwarder.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.RULES, R.string.tab_rules, Icons.AutoMirrored.Filled.List),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Filled.History),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

/** App shell: permission gate -> 3 tabs (Rules / History / Settings) + full-screen rule editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() = PermissionGate {
    val context = LocalContext.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    var batteryExempt by remember { mutableStateOf(SystemState.isIgnoringBatteryOptimizations(context)) }
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    var batteryPromptShown by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        batteryExempt = SystemState.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    // Ask once per launch, right after permissions are in place.
    LaunchedEffect(batteryExempt) {
        if (!batteryExempt && !batteryPromptShown) {
            batteryPromptShown = true
            showBatteryDialog = true
        }
    }
    if (showBatteryDialog) BatteryDialog(onDismiss = { showBatteryDialog = false })

    NavHost(nav, startDestination = Routes.RULES, modifier = Modifier.fillMaxSize()) {
        TABS.forEach { tab ->
            composable(tab.route) {
                Scaffold(
                    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(tab.label)) }) },
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            TABS.forEach { t ->
                                NavigationBarItem(
                                    selected = route == t.route,
                                    onClick = {
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(stringResource(t.label)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (!batteryExempt) BatteryBanner(onFix = { showBatteryDialog = true })
                        when (tab.route) {
                            Routes.RULES -> RulesScreen(
                                snackbar = snackbar,
                                onEditRule = { nav.navigate(Routes.ruleEdit(it)) },
                                onNewRule = { nav.navigate(Routes.ruleEdit(0)) },
                            )
                            Routes.HISTORY -> HistoryScreen(snackbar)
                            Routes.SETTINGS -> SettingsScreen(onFixBattery = { showBatteryDialog = true })
                        }
                    }
                }
            }
        }
        composable(
            Routes.RULE_EDIT,
            arguments = listOf(navArgument(RuleEditViewModel.ARG_RULE_ID) { type = NavType.LongType }),
        ) {
            RuleEditScreen(onDone = { nav.popBackStack() })
        }
    }
}
