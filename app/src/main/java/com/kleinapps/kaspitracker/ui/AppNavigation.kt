package com.kleinapps.kaspitracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.ui.screens.AddTransactionScreen
import com.kleinapps.kaspitracker.ui.screens.AnalyticsScreen
import com.kleinapps.kaspitracker.ui.screens.BackupScreen
import com.kleinapps.kaspitracker.ui.screens.BatchImportScreen
import com.kleinapps.kaspitracker.ui.screens.BudgetsScreen
import com.kleinapps.kaspitracker.ui.screens.CategorizeScreen
import com.kleinapps.kaspitracker.ui.screens.CategoryRulesScreen
import com.kleinapps.kaspitracker.ui.screens.SubscriptionsScreen
import com.kleinapps.kaspitracker.ui.screens.HistoryScreen
import com.kleinapps.kaspitracker.ui.screens.HomeScreen
import com.kleinapps.kaspitracker.ui.screens.RawLogScreen

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"

    /**
     * History also accepts a category + period, so a tap on the analytics screen
     * can open exactly the operations behind a number. All arguments are
     * optional — the bottom-bar tab navigates to the bare [HISTORY] route.
     */
    const val HISTORY_PATTERN =
        "history?category={category}&from={from}&to={to}&period={period}"

    fun history(category: Category, from: Long, to: Long, period: String): String =
        "history?category=${category.name}&from=$from&to=$to&period=${Uri.encode(period)}"
    const val BUDGETS = "budgets"
    const val RAW_LOG = "raw_log"
    const val ADD_TRANSACTION = "add_transaction"
    const val CATEGORIZE = "categorize"
    const val CATEGORY_RULES = "category_rules"
    const val SUBSCRIPTIONS = "subscriptions"
    const val BACKUP = "backup"
    const val BATCH_IMPORT = "batch_import"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Обзор", Icons.Filled.Home),
    Tab(Routes.ANALYTICS, "Аналитика", Icons.Filled.PieChart),
    Tab(Routes.HISTORY, "История", Icons.Filled.ReceiptLong),
    Tab(Routes.BUDGETS, "Бюджеты", Icons.Filled.Savings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(notificationAccessGranted: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    // Optional-argument routes come back as the whole pattern ("history?..."),
    // so tab selection and the title compare on the part before the query.
    val route = backStack?.destination?.route?.substringBefore('?')

    val (title, showBack) = when (route) {
        Routes.RAW_LOG -> "Журнал уведомлений" to true
        Routes.ADD_TRANSACTION -> "Новая запись" to true
        Routes.CATEGORIZE -> "Разобрать операции" to true
        Routes.CATEGORY_RULES -> "Правила категорий" to true
        Routes.SUBSCRIPTIONS -> "Подписки" to true
        Routes.BACKUP -> "Бэкап и восстановление" to true
        Routes.BATCH_IMPORT -> "Импорт чеков" to true
        Routes.ANALYTICS -> "Аналитика" to false
        Routes.HISTORY -> "История" to false
        Routes.BUDGETS -> "Бюджеты" to false
        else -> "" to false
    }

    Scaffold(
        topBar = {
            if (title.isNotEmpty()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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
                    onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenBatchImport = { navController.navigate(Routes.BATCH_IMPORT) }
                )
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(
                    onOpenCategory = { category, from, to, period ->
                        navController.navigate(Routes.history(category, from, to, period))
                    }
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
                    initialPeriodLabel = arguments?.getString("period")
                )
            }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.RAW_LOG) { RawLogScreen() }
            composable(Routes.ADD_TRANSACTION) {
                AddTransactionScreen(onSaved = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIZE) { CategorizeScreen() }
            composable(Routes.CATEGORY_RULES) { CategoryRulesScreen() }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen() }
            composable(Routes.BACKUP) { BackupScreen() }
            composable(Routes.BATCH_IMPORT) { BatchImportScreen() }
        }
    }
}
