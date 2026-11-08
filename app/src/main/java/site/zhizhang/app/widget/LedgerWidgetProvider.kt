package site.zhizhang.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import site.zhizhang.app.MainActivity
import site.zhizhang.app.R
import site.zhizhang.app.data.LedgerDatabase
import site.zhizhang.app.data.SettingsStore
import java.util.Calendar

class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, LedgerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scope.launch {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                val end = calendar.timeInMillis - 1
                val spent = LedgerDatabase.get(appContext).ledgerDao().expenseBetween(start, end)
                val budget = SettingsStore(appContext).monthlyBudgetCents
                val views = RemoteViews(appContext.packageName, R.layout.widget_ledger).apply {
                    setTextViewText(R.id.widget_expense, "¥ %.2f".format(spent / 100.0))
                    setTextViewText(R.id.widget_budget, "预算剩余 ¥ %.2f".format((budget - spent).coerceAtLeast(0) / 100.0))
                    val openIntent = Intent(appContext, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        appContext, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                }
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }
    }
}
