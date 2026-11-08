package site.zhizhang.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import site.zhizhang.app.BuildConfig
import site.zhizhang.app.data.SettingsStore
import site.zhizhang.app.util.PermissionHelper

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    notificationEnabled: Boolean,
    autoConfirm: Boolean,
    darkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onRefreshPermission: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var baseUrl by remember { mutableStateOf(settings.apiBaseUrl) }
    var model by remember { mutableStateOf(settings.apiModel) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var hideAmount by remember { mutableStateOf(settings.hideAiAmount) }
    var acceptDebug by remember { mutableStateOf(settings.acceptDebugNotifications) }
    var keepAlive by remember { mutableStateOf(settings.keepAliveEnabled) }
    var captureAlert by remember { mutableStateOf(settings.captureAlertEnabled) }
    var showAdb by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val listenerComponent = "${BuildConfig.APPLICATION_ID}/site.zhizhang.app.service.PaymentNotificationListener"
    val adbCommand = "adb shell cmd notification allow_listener $listenerComponent"
    val legacyAdbCommand = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("我的", style = MaterialTheme.typography.headlineMedium)
            Text("权限、数据与智能分类都由你控制", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        item {
            AppCard {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("外观", style = MaterialTheme.typography.titleSmall)
                        Text("深色模式使用同一套薄荷绿语义色，图表与文字同步适配", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = darkMode, onCheckedChange = onThemeChange)
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = Green)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("通知访问权限", style = MaterialTheme.typography.titleSmall)
                            Text(if (notificationEnabled) "已授权 · 自动记账可运行" else "未授权 · 不会处理任何通知", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (notificationEnabled) {
                            // 权限还在但服务可能已解绑：主动请系统重新绑定，然后打开系统设置供核对
                            site.zhizhang.app.util.ListenerGuard.ensureNow(context)
                            onRefreshPermission()
                            PermissionHelper.openNotificationSettings(context)
                        } else {
                            // 先尝试无 Root 的 ADB 授权结果，随后立即刷新界面状态
                            val enabledByAdb = PermissionHelper.enableListenerAfterAdbGrant(context)
                            onRefreshPermission()
                            if (!enabledByAdb) PermissionHelper.openNotificationSettings(context)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (notificationEnabled) "自检并重新绑定 / 打开系统设置" else "检查授权状态 / 去系统设置开启") }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("高置信度自动入账", style = MaterialTheme.typography.titleSmall)
                            Text("关闭后，所有新识别流水均进入待确认", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoConfirm, onCheckedChange = onAutoConfirmChange)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("后台常驻守护", style = MaterialTheme.typography.titleSmall)
                            Text("通知栏会有一条静默常驻提示，防止被系统清理", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = keepAlive, onCheckedChange = {
                            keepAlive = it
                            settings.keepAliveEnabled = it
                            if (it) site.zhizhang.app.service.GuardForegroundService.start(context)
                            else site.zhizhang.app.service.GuardForegroundService.stop(context)
                        })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("抓到新流水时提醒", style = MaterialTheme.typography.titleSmall)
                            Text("不用打开 App 就知道抓到了，点通知可去补全", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = captureAlert, onCheckedChange = {
                            captureAlert = it
                            settings.captureAlertEnabled = it
                        })
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("无 Root 的 ADB 授权（可选）", "仅用于简化通知授权，不获取 Root")
                    Row(Modifier.fillMaxWidth().clickable { showAdb = !showAdb }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("查看授权步骤", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    if (showAdb) {
                        Text("1. 手机开启开发者选项 → USB 调试，用数据线连接电脑。\n2. Android 13 及以上推荐执行第一条命令；旧系统执行第二条后回到本页点击上方按钮。\n3. 授权后可断开 USB。小米等系统还需允许自启动与后台运行。\n最稳妥的方式始终是：系统设置 → 通知使用权 → 手动开启知账。", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(adbCommand)) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Icon(Icons.Default.ContentCopy, null); Text(" 复制推荐 ADB 命令")
                        }
                        Text("Android 13+：\n$adbCommand", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Text("旧系统兼容：\n$legacyAdbCommand", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Text("第一条由系统直接允许通知监听；第二条仅授予修改该项安全设置的能力。两者都不需要 Root，知账也不会读取支付密码、短信、通讯录或账户凭据。", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("调试：模拟支付通知", "不实际付款也能验证自动记账链路")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("接受 ADB 模拟通知", style = MaterialTheme.typography.titleSmall)
                            Text("默认关闭。开启后，电脑用 ADB 注入的测试通知会被当成微信/支付宝/拼多多通知解析，并固定进入待确认。验证完请关掉。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = acceptDebug, onCheckedChange = {
                            acceptDebug = it
                            settings.acceptDebugNotifications = it
                        })
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("智能分类 API", "兼容 OpenAI Chat Completions 格式，留空则完全离线")
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it; saved = false }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        label = { Text("Base URL，例如 https://.../v1") }, singleLine = true)
                    OutlinedTextField(value = model, onValueChange = { model = it; saved = false }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("模型名称") }, singleLine = true)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("发送前隐藏金额", style = MaterialTheme.typography.titleSmall)
                            Text("仅发送所选流水的商户名与收支类型", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = hideAmount, onCheckedChange = { hideAmount = it; settings.hideAiAmount = it })
                    }
                    Button(onClick = {
                        settings.apiBaseUrl = baseUrl
                        settings.apiModel = model
                        settings.apiKey = apiKey
                        settings.hideAiAmount = hideAmount
                        saved = true
                    }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (saved) "已安全保存" else "保存 API 配置") }
                    Text("只有你在“智能分类”页主动点击后才发起请求，不会自动逐笔调用。", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, null, tint = Green)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("桌面小组件", style = MaterialTheme.typography.titleSmall)
                            Text("长按安卓桌面 → 小组件 → 知账，添加“本月支出”。点击小组件可返回账本。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = Green)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("本地账本与隐私", style = MaterialTheme.typography.titleSmall)
                            Text("流水默认仅保存于本机 Room 数据库，不启用云备份；API Key 由 Android Keystore 加密。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("导出 CSV 账本") }
                }
            }
        }
        item {
            Text("知账 ${BuildConfig.APP_VERSION} · 无需 Root · 通知解析不保证覆盖所有版本和厂商通知样式", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(84.dp))
        }
    }
}
