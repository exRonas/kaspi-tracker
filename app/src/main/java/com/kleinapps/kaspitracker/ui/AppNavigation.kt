package com.kleinapps.kaspitracker.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.ui.components.ThemeToggleButton
import com.kleinapps.kaspitracker.ui.screens.AddTransactionScreen
import com.kleinapps.kaspitracker.ui.screens.AnalyticsScreen
import com.kleinapps.kaspitracker.ui.screens.BackupScreen
import com.kleinapps.kaspitracker.ui.screens.BatchImportScreen
import com.kleinapps.kaspitracker.ui.screens.CategorizeScreen
import com.kleinapps.kaspitracker.ui.screens.CategoryRulesScreen
import com.kleinapps.kaspitracker.ui.screens.HistoryScreen
import com.kleinapps.kaspitracker.ui.screens.HomeScreen
import com.kleinapps.kaspitracker.ui.screens.PairScreen
import com.kleinapps.kaspitracker.ui.screens.RawLogScreen
import com.kleinapps.kaspitracker.ui.screens.StatsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.ui.components.rememberPeople
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.ProvideAccent

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"

    /**
     * History also accepts a category + period, so a tap on the analytics or
     * statistics screen can open exactly the operations behind a number. All
     * arguments are optional — the bottom-bar tab navigates to the bare [HISTORY] route.
     */
    const val HISTORY_PATTERN =
        "history?category={category}&from={from}&to={to}&period={period}"

    fun history(category: Category?, from: Long, to: Long, period: String): String =
        "history?" + listOfNotNull(
            category?.let { "category=${it.name}" },
            "from=$from",
            "to=$to",
            "period=${Uri.encode(period)}"
        ).joinToString("&")
    const val RAW_LOG = "raw_log"
    const val ADD_TRANSACTION = "add_transaction"
    const val CATEGORIZE = "categorize"
    const val CATEGORY_RULES = "category_rules"
    const val BACKUP = "backup"
    const val BATCH_IMPORT = "batch_import"
    const val PAIR = "pair"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Обзор", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.STATS, "Статистика", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
    Tab(Routes.ANALYTICS, "Аналитика", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
    Tab(Routes.HISTORY, "История", Icons.Outlined.ReceiptLong, Icons.Rounded.ReceiptLong)
)

/** Titles of the screens opened on top of a tab; tabs draw their own header. */
private val SCREEN_TITLES = mapOf(
    Routes.RAW_LOG to "Журнал уведомлений",
    Routes.ADD_TRANSACTION to "Новая запись",
    Routes.CATEGORIZE to "Разобрать операции",
    Routes.CATEGORY_RULES to "Правила категорий",
    Routes.BACKUP to "Бэкап и восстановление",
    Routes.BATCH_IMPORT to "Импорт чеков",
    Routes.PAIR to "Пара"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(notificationAccessGranted: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    // Optional-argument routes come back as the whole pattern ("history?..."),
    // so tab selection and the title compare on the part before the query.
    val route = backStack?.destination?.route?.substringBefore('?')
    val title = SCREEN_TITLES[route]
    val couple by CoupleStore.state.collectAsStateWithLifecycle()
    val people = rememberPeople()

    // The whole app takes the colour of whoever's numbers are on screen.
    ProvideAccent(people.accentOf(couple.effectiveScope)) {
    Scaffold(
        topBar = {
            if (title != null) {
                TopAppBar(
                    title = { Text(title, style = Kaspi.type.titleL) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Kaspi.colors.bg,
                        scrolledContainerColor = Kaspi.colors.bg,
                        titleContentColor = Kaspi.colors.ink,
                        navigationIconContentColor = Kaspi.colors.ink
                    )
                )
            }
        },
        bottomBar = {
            if (route in TABS.map { it.route }) {
                androidx.compose.foundation.layout.Column {
                    HorizontalDivider(color = Kaspi.colors.line)
                    NavigationBar(containerColor = Kaspi.colors.surf) {
                        TABS.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                                },
                                label = { Text(tab.label, style = Kaspi.type.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Kaspi.colors.accent,
                                    selectedTextColor = Kaspi.colors.ink,
                                    indicatorColor = Kaspi.colors.accentSoft,
                                    unselectedIconColor = Kaspi.colors.ink2,
                                    unselectedTextColor = Kaspi.colors.ink2
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = Kaspi.colors.bg
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    notificationAccessGranted = notificationAccessGranted,
                    onOpenRawLog = { navController.navigate(Routes.RAW_LOG) },
                    onAddManual = { navController.navigate(Routes.ADD_TRANSACTION) },
                    onOpenCategorize = { navController.navigate(Routes.CATEGORIZE) },
                    onOpenRules = { navController.navigate(Routes.CATEGORY_RULES) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenBatchImport = { navController.navigate(Routes.BATCH_IMPORT) },
                    onOpenHistory = { navController.openHistory(Routes.HISTORY) },
                    onOpenPair = { navController.navigate(Routes.PAIR) },
                    headerActions = { ThemeToggleButton() }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    onOpenMonth = { from, to, label ->
                        navController.openHistory(Routes.history(null, from, to, label))
                    },
                    headerActions = { ThemeToggleButton() }
                )
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(
                    onOpenCategory = { category, from, to, period ->
                        navController.openHistory(Routes.history(category, from, to, period))
                    },
                    headerActions = { ThemeToggleButton() }
                )
            }
            composable(
                route = Routes.HISTORY_PATTERN,
                arguments = listOf(
                    navArgument("category") { nullable = true; defaultValue = null },
                    navArgument("from") { nullable = true; defaultValue = null },
                    navArgument("to") { nullable = true; defaultValue = null },
                    navArgument("period") { nullable = true; defaultValue = null }
                )
            ) { entry ->
                val arguments = entry.arguments
                HistoryScreen(
                    initialCategory = arguments?.getString("category")
                        ?.let { runCatching { Category.valueOf(it) }.getOrNull() },
                    initialFrom = arguments?.getString("from")?.toLongOrNull(),
                    initialTo = arguments?.getString("to")?.toLongOrNull(),
                    initialPeriodLabel = arguments?.getString("period"),
                    onAddManual = { navController.navigate(Routes.ADD_TRANSACTION) },
                    headerActions = { ThemeToggleButton() }
                )
            }
            composable(Routes.RAW_LOG) { RawLogScreen() }
            composable(Routes.ADD_TRANSACTION) {
                AddTransactionScreen(onSaved = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIZE) { CategorizeScreen() }
            composable(Routes.CATEGORY_RULES) { CategoryRulesScreen() }
            composable(Routes.BACKUP) { BackupScreen() }
            composable(Routes.BATCH_IMPORT) { BatchImportScreen() }
            composable(Routes.PAIR) { PairScreen() }
        }
    }
    }
}

/**
 * Opens History with a filter as a switch to the History tab rather than as a
 * screen on top of the current one. Stacked on top, it stayed inside that tab's
 * saved state: tapping Statistics or Analytics afterwards restored the stack
 * with History still on top, so the tab seemed not to respond.
 */
private fun NavHostController.openHistory(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}
