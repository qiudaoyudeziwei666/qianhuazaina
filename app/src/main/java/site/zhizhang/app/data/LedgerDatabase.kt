package site.zhizhang.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerEntry::class,
        CategoryBudgetEntity::class,
        SavingsGoalEntity::class,
        SubscriptionEntity::class,
        CategoryRuleEntity::class,
        ClassificationHistoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var instance: LedgerDatabase? = null

        /** v1→v2 只新增表，绝不改动 ledger_entries，真实账本零风险 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `category_budgets` (`category` TEXT NOT NULL, `limitCents` INTEGER NOT NULL, PRIMARY KEY(`category`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `savedCents` INTEGER NOT NULL, `deadlineAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `note` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `cycle` TEXT NOT NULL, `nextDueAt` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, `note` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `category_rules` (`keyword` TEXT NOT NULL, `category` TEXT NOT NULL, `hits` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`keyword`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `classification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `oldCategory` TEXT NOT NULL, `newCategory` TEXT NOT NULL, `source` TEXT NOT NULL, `changedAt` INTEGER NOT NULL)")
            }
        }

        fun get(context: Context): LedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "zhizhang.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
