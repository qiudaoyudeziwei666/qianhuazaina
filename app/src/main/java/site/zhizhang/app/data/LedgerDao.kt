package site.zhizhang.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries WHERE status != 'IGNORED' ORDER BY occurredAt DESC")
    fun observeVisible(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE status = 'PENDING' ORDER BY occurredAt DESC")
    fun observePending(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE needsAi = 1 AND status != 'IGNORED' ORDER BY occurredAt DESC")
    fun observeNeedsAi(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LedgerEntry?

    @Query("SELECT * FROM ledger_entries WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<LedgerEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LedgerEntry): Long

    @Update
    suspend fun update(entry: LedgerEntry)

    @Update
    suspend fun updateAll(entries: List<LedgerEntry>)

    @Delete
    suspend fun delete(entry: LedgerEntry)

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM ledger_entries WHERE status = 'CONFIRMED' AND type = 'EXPENSE' AND occurredAt BETWEEN :start AND :end")
    suspend fun expenseBetween(start: Long, end: Long): Long

    @Query("SELECT COUNT(*) FROM ledger_entries")
    suspend fun count(): Int

    /** 完整备份 / 完整性校验用：包含 IGNORED 在内的全部记录 */
    @Query("SELECT * FROM ledger_entries ORDER BY occurredAt DESC")
    suspend fun allOnce(): List<LedgerEntry>
}
