package site.zhizhang.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import site.zhizhang.app.ai.AiClassifier
import site.zhizhang.app.core.CategoryClassifier
import site.zhizhang.app.core.ParseConfidence
import site.zhizhang.app.core.PaymentParser
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.core.CategoryEngine
import site.zhizhang.app.core.EntryCompleteness
import site.zhizhang.app.data.CategoryBudgetEntity
import site.zhizhang.app.data.CategoryRuleEntity
import site.zhizhang.app.data.ClassificationHistoryEntity
import site.zhizhang.app.data.ClassificationSource as DataClassificationSource
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerDatabase
import site.zhizhang.app.data.LedgerEntry
import site.zhizhang.app.data.LedgerRepository
import site.zhizhang.app.data.SavingsGoalEntity
import site.zhizhang.app.data.SettingsStore
import site.zhizhang.app.data.SubscriptionEntity
import site.zhizhang.app.util.CsvExporter
import site.zhizhang.app.util.DataCodec
import site.zhizhang.app.util.PermissionHelper
import site.zhizhang.app.widget.LedgerWidgetProvider
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LedgerDatabase.get(application)
    private val repository = LedgerRepository(database.ledgerDao())
    private val financeDao = database.financeDao()
    val settings = SettingsStore(application)
    private val aiClassifier = AiClassifier()
    private var lastDeleted: LedgerEntry? = null

    val entries = repository.observeVisible().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending = repository.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val needsAi = repository.observeNeedsAi().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = financeDao.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = financeDao.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subscriptions = financeDao.observeSubscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules = financeDao.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val classificationHistory = financeDao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _notificationEnabled = MutableStateFlow(false)
    val notificationEnabled = _notificationEnabled.asStateFlow()
    private val _lastNotificationAt = MutableStateFlow(settings.lastNotificationAt)
    val lastNotificationAt = _lastNotificationAt.asStateFlow()
    private val _autoConfirm = MutableStateFlow(settings.autoConfirm)
    val autoConfirm = _autoConfirm.asStateFlow()
    private val _classifying = MutableStateFlow(false)
    val classifying = _classifying.asStateFlow()

    fun refreshPermission() {
        _notificationEnabled.value = PermissionHelper.isNotificationListenerEnabled(getApplication())
        // 每次回到界面都重读一次，退出期间后台收到的通知也会体现在这里
        _lastNotificationAt.value = settings.lastNotificationAt
    }

    fun setAutoConfirm(enabled: Boolean) {
        settings.autoConfirm = enabled
        _autoConfirm.value = enabled
    }

    fun consumeMessage() { _message.value = null }

    fun addManual(amountCents: Long, merchant: String, category: String, sourceName: String, note: String) = launchDb {
        require(amountCents > 0) { "金额必须大于 0" }
        repository.insert(LedgerEntry(
            amountCents = amountCents,
            type = PaymentType.EXPENSE,
            merchant = merchant.ifBlank { "手动记账" },
            category = category,
            sourcePackage = "manual",
            sourceName = sourceName,
            occurredAt = System.currentTimeMillis(),
            note = note,
            status = EntryStatus.CONFIRMED,
            confidence = ParseConfidence.HIGH,
            classificationSource = DataClassificationSource.MANUAL,
            needsAi = category == "未分类",
            dedupKey = "manual-${UUID.randomUUID()}"
        ))
        _message.value = "已记录 ¥${"%.2f".format(amountCents / 100.0)}"
    }

    fun testNotification(raw: String) = launchDb {
        val parsed = PaymentParser.parse("com.tencent.mm", "微信支付", raw, System.currentTimeMillis())
        if (parsed == null) {
            _message.value = "未识别到明确的支付动作和金额，请检查通知文本"
            return@launchDb
        }
        val inserted = repository.insertParsed(parsed, autoConfirm = false)
        _message.value = if (inserted) "解析成功，已加入待确认" else "相同通知已存在，已自动去重"
    }

    fun confirm(entry: LedgerEntry) = launchDb {
        repository.update(entry.copy(status = EntryStatus.CONFIRMED))
        _message.value = "已确认入账"
    }

    fun ignore(entry: LedgerEntry) = launchDb {
        repository.update(entry.copy(status = EntryStatus.IGNORED))
        _message.value = "已忽略，不参与统计"
    }

    fun saveEntry(entry: LedgerEntry) = launchDb {
        val previous = repository.getByIds(listOf(entry.id)).firstOrNull()
        repository.update(entry.copy(
            classificationSource = DataClassificationSource.MANUAL,
            needsAi = entry.category == "未分类"
        ))
        if (previous != null && previous.category != entry.category) {
            financeDao.insertHistory(
                ClassificationHistoryEntity(
                    entryId = entry.id,
                    oldCategory = previous.category,
                    newCategory = entry.category,
                    source = "MANUAL"
                )
            )
            // 规则学习：只把「真实商户名」记成规则，通用渠道名不会污染规则库
            if (!EntryCompleteness.isGenericMerchant(entry.merchant)) {
                financeDao.upsertRule(CategoryRuleEntity(keyword = entry.merchant.trim(), category = entry.category))
            }
        }
        _message.value = "流水已更新"
    }

    fun delete(entry: LedgerEntry) = launchDb {
        lastDeleted = entry
        repository.delete(entry)
        _message.value = "流水已删除 · 可撤销"
    }

    fun undoDelete() = launchDb {
        val entry = lastDeleted ?: run {
            _message.value = "没有可撤销的删除"
            return@launchDb
        }
        repository.insert(entry.copy(id = 0, dedupKey = "${entry.dedupKey}-undo-${UUID.randomUUID()}"))
        lastDeleted = null
        _message.value = "已恢复上一笔删除"
    }

    // ---------- 预算 ----------
    fun setMonthlyBudget(cents: Long) {
        settings.monthlyBudgetCents = cents.coerceAtLeast(0)
        _message.value = "月度总预算已更新"
    }

    fun upsertCategoryBudget(category: String, cents: Long) = launchDb {
        financeDao.upsertBudget(CategoryBudgetEntity(category.trim(), cents.coerceAtLeast(0)))
        _message.value = "分类预算已保存"
    }

    fun deleteCategoryBudget(category: String) = launchDb {
        financeDao.deleteBudget(CategoryBudgetEntity(category, 0))
        _message.value = "分类预算已删除"
    }

    // ---------- 储蓄目标 ----------
    fun addGoal(name: String, targetCents: Long, savedCents: Long, deadlineAt: Long, note: String) = launchDb {
        require(name.isNotBlank() && targetCents > 0) { "请填写目标名称和金额" }
        financeDao.insertGoal(
            SavingsGoalEntity(
                name = name.trim(), targetCents = targetCents, savedCents = savedCents,
                deadlineAt = deadlineAt, note = note
            )
        )
        _message.value = "储蓄目标已创建"
    }

    fun updateGoal(goal: SavingsGoalEntity) = launchDb { financeDao.updateGoal(goal) }
    fun deleteGoal(goal: SavingsGoalEntity) = launchDb { financeDao.deleteGoal(goal) }

    // ---------- 订阅 ----------
    fun addSubscription(name: String, amountCents: Long, cycle: String, nextDueAt: Long, note: String) = launchDb {
        require(name.isNotBlank() && amountCents > 0) { "请填写订阅名称和金额" }
        financeDao.insertSubscription(
            SubscriptionEntity(
                name = name.trim(), amountCents = amountCents, cycle = cycle,
                nextDueAt = nextDueAt, note = note
            )
        )
        _message.value = "订阅已添加"
    }

    fun updateSubscription(sub: SubscriptionEntity) = launchDb { financeDao.updateSubscription(sub) }
    fun deleteSubscription(sub: SubscriptionEntity) = launchDb { financeDao.deleteSubscription(sub) }

    // ---------- 分类规则 / 历史 ----------
    fun addRule(keyword: String, category: String) = launchDb {
        require(keyword.isNotBlank()) { "关键词不能为空" }
        financeDao.upsertRule(CategoryRuleEntity(keyword.trim(), category))
        _message.value = "规则已保存，之后优先按它分类"
    }

    fun deleteRule(rule: CategoryRuleEntity) = launchDb { financeDao.deleteRule(rule) }
    fun clearHistory() = launchDb { financeDao.clearHistory() }

    // ---------- 批量操作 ----------
    fun confirmAll(selected: List<LedgerEntry>) = launchDb {
        repository.updateAll(selected.map { it.copy(status = EntryStatus.CONFIRMED) })
        _message.value = "已确认 ${selected.size} 笔"
    }

    fun ignoreAll(selected: List<LedgerEntry>) = launchDb {
        repository.updateAll(selected.map { it.copy(status = EntryStatus.IGNORED) })
        _message.value = "已忽略 ${selected.size} 笔"
    }

    fun batchDelete(selected: List<LedgerEntry>) = launchDb {
        if (selected.isEmpty()) return@launchDb
        selected.forEach { repository.delete(it) }
        lastDeleted = selected.last()
        _message.value = "已删除 ${selected.size} 笔 · 可撤销最后一笔"
    }

    /** 分层分类的第 1~3 层批量执行：用户规则 > 本地关键词 > 历史推断，完全不联网 */
    fun localClassify(selected: List<LedgerEntry>) = launchDb {
        val rules = financeDao.rulesOnce().map { it.keyword to it.category }
        val history = entries.value
            .filter { it.status == EntryStatus.CONFIRMED && it.category != "未分类" }
            .map { it.merchant to it.category }
        val updated = selected.mapNotNull { e ->
            val d = CategoryEngine.classify(
                merchant = e.merchant,
                rawText = e.rawNotification,
                isIncome = e.type == PaymentType.INCOME,
                userRules = rules,
                history = history
            )
            if (d.category != "未分类") e.copy(
                category = d.category, needsAi = false,
                classificationSource = DataClassificationSource.LOCAL
            ) else null
        }
        repository.updateAll(updated)
        _message.value = "本地规则命中 ${updated.size} 笔，未调用任何 API"
    }

    fun batchCategory(selected: List<LedgerEntry>, category: String) = launchDb {
        repository.updateAll(selected.map {
            it.copy(category = category, needsAi = false, classificationSource = DataClassificationSource.MANUAL)
        })
        selected.forEach {
            financeDao.insertHistory(
                ClassificationHistoryEntity(
                    entryId = it.id, oldCategory = it.category, newCategory = category, source = "BATCH"
                )
            )
        }
        _message.value = "已将 ${selected.size} 笔归为「$category」"
    }

    /**
     * 补全流水时的一键候选：先给「你自己记过的商户」，不足 6 个再用常用组合兜底。
     * 全部来自本机账本，不调用任何 API。
     */
    fun recentSuggestions(entries: List<LedgerEntry>): List<Pair<String, String>> {
        val learned = entries
            .filter { it.status == EntryStatus.CONFIRMED && it.merchant.isNotBlank() && it.category != "未分类" }
            .map { it.merchant.trim() to it.category }
            .distinctBy { it.first }
            .take(6)
            .toMutableList()
        listOf(
            "拼多多" to "购物消费", "淘宝" to "购物消费", "京东" to "购物消费",
            "美团外卖" to "餐饮美食", "瑞幸咖啡" to "餐饮美食", "地铁" to "交通出行"
        ).forEach { suggestion ->
            if (learned.size >= 6) return@forEach
            if (learned.none { it.first == suggestion.first }) learned.add(suggestion)
        }
        return learned
    }

    fun classifyBatch(selected: List<LedgerEntry>) {
        if (_classifying.value) return
        viewModelScope.launch {
            _classifying.value = true
            try {
                require(selected.isNotEmpty()) { "请先勾选要分类的流水" }
                require(selected.size <= 20) { "每批最多选择 20 笔" }
                val result = aiClassifier.classify(
                    entries = selected,
                    baseUrl = settings.apiBaseUrl,
                    apiKey = settings.apiKey,
                    model = settings.apiModel,
                    hideAmount = settings.hideAiAmount
                )
                val updated = selected.mapNotNull { entry ->
                    result[entry.id]?.let { category ->
                        entry.copy(category = category, needsAi = false, classificationSource = DataClassificationSource.AI)
                    }
                }
                repository.updateAll(updated)
                settings.aiCallCount = settings.aiCallCount + 1
                settings.aiEstimatedTokens = settings.aiEstimatedTokens + selected.size * 60L
                updated.forEach {
                    financeDao.insertHistory(
                        ClassificationHistoryEntity(
                            entryId = it.id, oldCategory = it.category, newCategory = it.category, source = "AI"
                        )
                    )
                }
                LedgerWidgetProvider.updateAll(getApplication())
                _message.value = "已应用 ${updated.size} 条分类建议，本次只调用了 1 次 API"
            } catch (error: Exception) {
                _message.value = error.message?.take(180) ?: "分类失败，请检查 API 配置"
            } finally {
                _classifying.value = false
            }
        }
    }

    fun exportCsv(uri: Uri) = launchDb {
        CsvExporter.export(getApplication(), uri, entries.value)
        _message.value = "账本已导出"
    }

    // ---------- 数据管理 ----------
    private fun readUri(uri: Uri): String = getApplication<Application>().contentResolver
        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

    private fun writeUri(uri: Uri, text: String) {
        getApplication<Application>().contentResolver.openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
    }

    /** CSV 导入：预览在 UI 层完成，这里只写入用户已确认的行；重复 dedupKey 自动跳过 */
    fun importCsv(uri: Uri) = launchDb {
        val result = DataCodec.parseCsv(readUri(uri))
        var added = 0
        result.rows.forEach { row ->
            if (repository.insert(DataCodec.rowToEntry(row)) != -1L) added++
        }
        _message.value = "导入完成：新增 $added 笔，跳过 ${result.skipped} 行（重复自动去重）"
    }

    private var pendingImportUri: Uri? = null
    private val _importPreview = MutableStateFlow<DataCodec.CsvResult?>(null)
    val importPreview = _importPreview.asStateFlow()

    /** 导入第一步：只解析、不写库，交给 UI 预览并由用户确认 */
    fun previewCsv(uri: Uri) = viewModelScope.launch {
        runCatching {
            pendingImportUri = uri
            _importPreview.value = DataCodec.parseCsv(readUri(uri))
        }.onFailure { _message.value = "读取文件失败：${it.message?.take(80)}" }
    }

    fun dismissImportPreview() {
        _importPreview.value = null
        pendingImportUri = null
    }

    fun confirmImport() {
        pendingImportUri?.let { importCsv(it) }
        _importPreview.value = null
        pendingImportUri = null
    }

    /** 完整备份：包含 IGNORED，NDJSON 每行一条，保留全部元数据 */
    fun backupJson(uri: Uri) = launchDb {
        val all = repository.allOnce()
        writeUri(uri, DataCodec.toBackupJson(all))
        _message.value = "已备份 ${all.size} 笔流水（含已忽略）"
    }

    /** 恢复采用追加模式，绝不覆盖或清空现有真实数据 */
    fun restoreJson(uri: Uri) = launchDb {
        val list = DataCodec.fromBackupJson(readUri(uri))
        var added = 0
        list.forEach { e ->
            val restored = e.copy(id = 0, dedupKey = "${e.dedupKey}-restore-${UUID.randomUUID()}")
            if (repository.insert(restored) != -1L) added++
        }
        _message.value = "恢复完成：追加 $added 笔（原有数据未改动）"
    }

    /** 去重是破坏性操作，UI 必须先弹确认框再调用 */
    fun dedup() = launchDb {
        val ids = DataCodec.duplicateIds(repository.allOnce())
        if (ids.isEmpty()) {
            _message.value = "没有发现重复流水"
            return@launchDb
        }
        repository.getByIds(ids).forEach { repository.delete(it) }
        _message.value = "已清理 ${ids.size} 笔重复流水"
    }

    fun validateData() = viewModelScope.launch {
        val all = repository.allOnce()
        val bad = all.count {
            it.amountCents <= 0 || it.dedupKey.isBlank() ||
                it.occurredAt > System.currentTimeMillis() + 86_400_000L
        }
        _message.value = if (bad == 0) "完整性校验通过：${all.size} 笔无异常"
        else "发现 $bad 笔异常数据（金额/去重键/时间），建议检查"
    }

    private fun launchDb(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                LedgerWidgetProvider.updateAll(getApplication())
            } catch (error: Exception) {
                _message.value = error.message?.take(160) ?: "操作失败"
            }
        }
    }
}
