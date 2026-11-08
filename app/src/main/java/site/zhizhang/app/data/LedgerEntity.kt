package site.zhizhang.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import site.zhizhang.app.core.ParseConfidence
import site.zhizhang.app.core.PaymentType

enum class EntryStatus { PENDING, CONFIRMED, IGNORED }
enum class ClassificationSource { LOCAL, MANUAL, AI }

@Entity(tableName = "ledger_entries", indices = [Index(value = ["dedupKey"], unique = true)])
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val type: PaymentType,
    val merchant: String,
    val category: String,
    val sourcePackage: String,
    val sourceName: String,
    val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val rawNotification: String = "",
    val status: EntryStatus = EntryStatus.PENDING,
    val confidence: ParseConfidence = ParseConfidence.HIGH,
    val classificationSource: ClassificationSource = ClassificationSource.LOCAL,
    val needsAi: Boolean = false,
    val dedupKey: String = ""
)
