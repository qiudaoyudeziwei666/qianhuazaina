package site.zhizhang.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 分类预算：一个分类一条，单位「分」，不区分月份（按月统计时共用额度） */
@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val category: String,
    val limitCents: Long
)

/** 储蓄目标：savedCents 是用户手动登记的已存金额，不等于账户余额，也不等于预算结余 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadlineAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

/** 周期性订阅：cycle = MONTHLY / QUARTERLY / YEARLY */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val cycle: String,
    val nextDueAt: Long,
    val active: Boolean = true,
    val lastUsedAt: Long = 0L,
    val note: String = ""
)

/** 用户自定义商户规则，优先级高于内置关键词 */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey val keyword: String,
    val category: String,
    val hits: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 分类修改历史（人工或 AI），用于回溯与规则学习 */
@Entity(tableName = "classification_history")
data class ClassificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val oldCategory: String,
    val newCategory: String,
    val source: String,
    val changedAt: Long = System.currentTimeMillis()
)
