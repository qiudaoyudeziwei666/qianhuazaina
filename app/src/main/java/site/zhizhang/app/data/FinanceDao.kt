package site.zhizhang.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // ---------- 分类预算 ----------
    @Query("SELECT * FROM category_budgets ORDER BY category")
    fun observeBudgets(): Flow<List<CategoryBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: CategoryBudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: CategoryBudgetEntity)

    // ---------- 储蓄目标 ----------
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Insert
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    // ---------- 订阅 ----------
    @Query("SELECT * FROM subscriptions ORDER BY nextDueAt ASC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert
    suspend fun insertSubscription(sub: SubscriptionEntity): Long

    @Update
    suspend fun updateSubscription(sub: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(sub: SubscriptionEntity)

    // ---------- 用户规则 ----------
    @Query("SELECT * FROM category_rules ORDER BY hits DESC, keyword ASC")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun rulesOnce(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: CategoryRuleEntity)

    @Query("UPDATE category_rules SET hits = hits + 1 WHERE keyword = :keyword")
    suspend fun bumpRule(keyword: String)

    @Delete
    suspend fun deleteRule(rule: CategoryRuleEntity)

    // ---------- 分类修改历史 ----------
    @Query("SELECT * FROM classification_history ORDER BY changedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<ClassificationHistoryEntity>>

    @Insert
    suspend fun insertHistory(item: ClassificationHistoryEntity)

    @Query("DELETE FROM classification_history")
    suspend fun clearHistory()
}
