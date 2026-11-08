package site.zhizhang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import site.zhizhang.app.ui.MainViewModel
import site.zhizhang.app.ui.ZhizhangApp
import site.zhizhang.app.ui.ZhizhangTheme
import site.zhizhang.app.widget.LedgerWidgetProvider

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewCsv)
    }
    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(viewModel::backupJson)
        }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreJson)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by remember { mutableStateOf(viewModel.settings.darkMode) }
            ZhizhangTheme(darkMode = darkMode) {
                ZhizhangApp(
                    viewModel = viewModel,
                    darkMode = darkMode,
                    onExport = { exportLauncher.launch("知账账本.csv") },
                    onImportPick = { importLauncher.launch(arrayOf("text/*", "application/json")) },
                    onBackupPick = { backupLauncher.launch("zhizhang-backup.json") },
                    onRestorePick = { restoreLauncher.launch(arrayOf("application/json", "text/*")) },
                    onThemeChange = { enabled ->
                        darkMode = enabled
                        viewModel.settings.darkMode = enabled
                    }
                )
            }
        }
        LedgerWidgetProvider.updateAll(this)
        // Android 13+：抓到流水的提醒通知需要通知权限（前台服务通知本身豁免）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // 从前台启动前台服务最稳妥；开机广播和划掉卡片时也会再尝试
        site.zhizhang.app.service.GuardForegroundService.start(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
        // 服务长时间没动静时，主动请系统重新绑定；授权被系统整个撤销时仍需用户手动开启
        site.zhizhang.app.util.ListenerGuard.ensureIfStale(this)
        // 仅 Debug：adb shell am start -n <包名>/.MainActivity --ez forceRebind true
        // 用于验证自愈链路。小米会静默丢弃静态广播，所以改用 Activity Intent（必定送达）。
        if (site.zhizhang.app.BuildConfig.DEBUG && intent.getBooleanExtra("forceRebind", false)) {
            site.zhizhang.app.util.ListenerGuard.requestRebind(this)
        }
    }
}
