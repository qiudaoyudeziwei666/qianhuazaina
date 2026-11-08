package site.zhizhang.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import site.zhizhang.app.core.FinanceStats
import site.zhizhang.app.core.HomeCards
import site.zhizhang.app.data.LedgerEntry

private enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    STATS("分析", Icons.Default.BarChart),
    AUTO("自动", Icons.Default.Bolt),
    LEDGER("流水", Icons.Default.ReceiptLong),
    SETTINGS("我的", Icons.Default.Settings),
    CATEGORY("分类", Icons.Default.Sell),
    BUDGET("预算", Icons.Default.ChevronRight),
    GOALS("储蓄", Icons.Default.ChevronRight),
    SUBS("订阅", Icons.Default.ChevronRight),
    INSIGHTS("洞察", Icons.Default.ChevronRight),
    DATA("数据", Icons.Default.ChevronRight)
}

private val BottomTabs = listOf(
    AppScreen.HOME, AppScreen.STATS, AppScreen.AUTO, AppScreen.LEDGER, AppScreen.SETTINGS
)

/** 固定 / 非必要分类的默认定义，后续可由用户配置 */
private val DefaultFixedCategories = setOf("居家生活", "通讯订阅", "医疗健康")
private val DefaultNonEssentialCategories = setOf("休闲娱乐", "购物消费")

@Composable
fun ZhizhangApp(
    viewModel: MainViewModel,
    darkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImportPick: () -> Unit,
    onBackupPick: () -> Unit,
    onRestorePick: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val needsAi by viewModel.needsAi.collectAsStateWithLifecycle()
    val notificationEnabled by viewModel.notificationEnabled.collectAsStateWithLifecycle()
    val lastNotificationAt by viewModel.lastNotificationAt.collectAsStateWithLifecycle()
    val autoConfirm by viewModel.autoConfirm.collectAsStateWithLifecycle()
    val classifying by viewModel.classifying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val classificationHistory by viewModel.classificationHistory.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var statsTab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var budgetCents by remember { mutableStateOf(viewModel.settings.monthlyBudgetCents) }
    var cards by remember { mutableStateOf(HomeCards.parse(viewModel.settings.homeCards)) }
    val snackbar = remember { SnackbarHostState() }

    val dashboard = FinanceStats.buildDashboard(entries, budgetCents)
    val insights = FinanceStats.insightLines(
        entries, budgetCents, DefaultFixedCategories, DefaultNonEssentialCategories
    )

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                BottomTabs.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item || (item == AppScreen.STATS && screen == AppScreen.CATEGORY),
                        onClick = {
                            screen = item
                            if (item == AppScreen.STATS) statsTab = 0
                        },
                        icon = {
                            if (item == AppScreen.AUTO && pending.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(pending.size.toString()) } }) { Icon(item.icon, item.label) }
                            } else Icon(item.icon, item.label)
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Green,
                contentColor = OnGreen) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    entries = entries,
                    notificationEnabled = notificationEnabled,
                    lastNotificationAt = lastNotificationAt,
                    dashboard = dashboard,
                    goals = goals,
                    insights = insights,
                    cardConfig = cards,
                    actions = HomeActions(
                        onOpenAuto = { screen = AppScreen.AUTO },
                        onOpenLedger = { screen = AppScreen.LEDGER },
                        onOpenStats = { screen = AppScreen.STATS; statsTab = 0 },
                        onOpenBudget = { screen = AppScreen.BUDGET },
                        onOpenGoals = { screen = AppScreen.GOALS },
                        onOpenSubs = { screen = AppScreen.SUBS },
                        onOpenInsights = { screen = AppScreen.INSIGHTS },
                        onOpenData = { screen = AppScreen.DATA },
                        onAdd = { showAdd = true },
                        onEntryClick = { editing = it },
                        onCardsChange = { updated ->
                            cards = updated
                            viewModel.settings.homeCards = HomeCards.serialize(updated)
                        }
                    )
                )

                AppScreen.STATS -> Column(Modifier.fillMaxHeight()) {
                    TabRow(selectedTabIndex = statsTab) {
                        Tab(selected = statsTab == 0, onClick = { statsTab = 0 }) { Text("统计分析", Modifier.padding(12.dp)) }
                        Tab(selected = statsTab == 1, onClick = { statsTab = 1 }) { Text("智能分类", Modifier.padding(12.dp)) }
                    }
                    Box(Modifier.weight(1f)) {
                        if (statsTab == 0) StatsScreen(
                            entries = entries,
                            pending = pending,
                            fixedCategories = DefaultFixedCategories,
                            onExport = onExport,
                            onEntryClick = { editing = it }
                        ) else CategoryScreen(
                            entries = entries,
                            needsAi = needsAi,
                            classifying = classifying,
                            rules = rules,
                            history = classificationHistory,
                            aiCallCount = viewModel.settings.aiCallCount,
                            aiTokens = viewModel.settings.aiEstimatedTokens,
                            onClassify = viewModel::classifyBatch,
                            onLocalClassify = viewModel::localClassify,
                            onAddRule = viewModel::addRule,
                            onDeleteRule = viewModel::deleteRule,
                            onClearHistory = viewModel::clearHistory,
                            onOpenSettings = { screen = AppScreen.SETTINGS }
                        )
                    }
                }

                AppScreen.CATEGORY -> CategoryScreen(
                    entries = entries,
                    needsAi = needsAi,
                    classifying = classifying,
                    rules = rules,
                    history = classificationHistory,
                    aiCallCount = viewModel.settings.aiCallCount,
                    aiTokens = viewModel.settings.aiEstimatedTokens,
                    onClassify = viewModel::classifyBatch,
                    onLocalClassify = viewModel::localClassify,
                    onAddRule = viewModel::addRule,
                    onDeleteRule = viewModel::deleteRule,
                    onClearHistory = viewModel::clearHistory,
                    onOpenSettings = { screen = AppScreen.SETTINGS }
                )

                AppScreen.AUTO -> AutoScreen(pending, notificationEnabled, autoConfirm, viewModel::setAutoConfirm,
                    viewModel::confirm, viewModel::ignore, viewModel::testNotification,
                    { screen = AppScreen.SETTINGS }, { editing = it },
                    viewModel::confirmAll, viewModel::ignoreAll, viewModel::batchCategory)

                AppScreen.LEDGER -> LedgerScreen(
                    entries = entries,
                    onEntryClick = { editing = it },
                    onBatchCategory = viewModel::batchCategory,
                    onBatchDelete = viewModel::batchDelete,
                    onUndo = viewModel::undoDelete
                )

                AppScreen.SETTINGS -> SettingsScreen(viewModel.settings, notificationEnabled, autoConfirm,
                    darkMode, onThemeChange, viewModel::setAutoConfirm, viewModel::refreshPermission, onExport)

                AppScreen.BUDGET -> BudgetScreen(
                    entries = entries,
                    monthlyBudgetCents = budgetCents,
                    budgets = budgets,
                    fixedCategories = DefaultFixedCategories,
                    onSetMonthly = { budgetCents = it; viewModel.setMonthlyBudget(it) },
                    onUpsert = viewModel::upsertCategoryBudget,
                    onDelete = viewModel::deleteCategoryBudget,
                    onBack = { screen = AppScreen.HOME }
                )

                AppScreen.GOALS -> GoalsScreen(
                    goals = goals,
                    entries = entries,
                    onAdd = viewModel::addGoal,
                    onUpdate = viewModel::updateGoal,
                    onDelete = viewModel::deleteGoal,
                    onBack = { screen = AppScreen.HOME }
                )

                AppScreen.SUBS -> SubscriptionsScreen(
                    subs = subscriptions,
                    onAdd = viewModel::addSubscription,
                    onUpdate = viewModel::updateSubscription,
                    onDelete = viewModel::deleteSubscription,
                    onBack = { screen = AppScreen.HOME }
                )

                AppScreen.INSIGHTS -> InsightsScreen(
                    entries = entries,
                    monthlyBudgetCents = budgetCents,
                    fixedCategories = DefaultFixedCategories,
                    nonEssentialCategories = DefaultNonEssentialCategories,
                    onBack = { screen = AppScreen.HOME }
                )

                AppScreen.DATA -> DataManageScreen(
                    entryCount = entries.size,
                    preview = importPreview,
                    onDismissPreview = viewModel::dismissImportPreview,
                    onConfirmImport = viewModel::confirmImport,
                    onExportCsv = onExport,
                    onImportPick = onImportPick,
                    onBackupPick = onBackupPick,
                    onRestorePick = onRestorePick,
                    onDedup = viewModel::dedup,
                    onUndo = viewModel::undoDelete,
                    onValidate = viewModel::validateData,
                    onBack = { screen = AppScreen.HOME }
                )
            }
        }
    }

    if (showAdd) AddManualDialog(onDismiss = { showAdd = false }, onSave = { amount, merchant, category, source, note ->
        viewModel.addManual(amount, merchant, category, source, note)
        if (amount > 0) showAdd = false
    })
    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            suggestions = viewModel.recentSuggestions(entries),
            onDismiss = { editing = null },
            onSave = { viewModel.saveEntry(it); editing = null },
            onDelete = { viewModel.delete(it); editing = null }
        )
    }
}
