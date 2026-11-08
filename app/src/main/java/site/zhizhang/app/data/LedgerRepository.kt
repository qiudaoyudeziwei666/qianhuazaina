package site.zhizhang.app.data

import site.zhizhang.app.core.ParsedPayment

class LedgerRepository(private val dao: LedgerDao) {
    fun observeVisible() = dao.observeVisible()
    fun observePending() = dao.observePending()
    fun observeNeedsAi() = dao.observeNeedsAi()

    suspend fun insertParsed(parsed: ParsedPayment, autoConfirm: Boolean): Boolean {
        val entry = LedgerEntry(
            amountCents = parsed.amountCents,
            type = parsed.type,
            merchant = parsed.merchant,
            category = parsed.category,
            sourcePackage = parsed.sourcePackage,
            sourceName = parsed.sourceName,
            occurredAt = parsed.occurredAt,
            rawNotification = parsed.rawNotification,
            status = if (autoConfirm && parsed.confidence == site.zhizhang.app.core.ParseConfidence.HIGH) {
                EntryStatus.CONFIRMED
            } else EntryStatus.PENDING,
            confidence = parsed.confidence,
            needsAi = parsed.needsAi,
            dedupKey = parsed.dedupKey
        )
        return dao.insert(entry) != -1L
    }

    suspend fun insert(entry: LedgerEntry): Long = dao.insert(entry)
    suspend fun update(entry: LedgerEntry) = dao.update(entry)
    suspend fun updateAll(entries: List<LedgerEntry>) = dao.updateAll(entries)
    suspend fun delete(entry: LedgerEntry) = dao.delete(entry)
    suspend fun expenseBetween(start: Long, end: Long) = dao.expenseBetween(start, end)
    suspend fun getByIds(ids: List<Long>) = dao.getByIds(ids)
    suspend fun allOnce() = dao.allOnce()
}
