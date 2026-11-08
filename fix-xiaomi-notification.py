# -*- coding: utf-8 -*-
"""知账 · 小米/HyperOS 通知使用权诊断与修复（无需 Root）

用法：
  python fix-xiaomi-notification.py            # 等待授权后自动诊断+修复
  python fix-xiaomi-notification.py --no-wait  # 已授权，直接跑
  python fix-xiaomi-notification.py --check    # 只诊断不改设置
"""
import subprocess
import sys
import time
import os

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from device_config import find_adb, find_apk

ADB = find_adb()
APK = find_apk()
SERVICE = "site.zhizhang.app.service.PaymentNotificationListener"
KNOWN_PKGS = ["site.zhizhang.app.debug", "site.zhizhang.app"]

WAIT = "--no-wait" not in sys.argv
CHECK_ONLY = "--check" in sys.argv
WAIT_SECONDS = 480


def adb(*args, timeout=40):
    try:
        return subprocess.run(
            [ADB] + list(args), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=timeout
        ).stdout.strip()
    except Exception as e:
        return "ERR: %s" % e


def sh(cmd, timeout=40):
    return adb("-s", SERIAL, "shell", cmd, timeout=timeout)


def title(text):
    print("\n" + "=" * 62)
    print(text)
    print("=" * 62, flush=True)


def step(name, cmd, shell=True):
    out = sh(cmd) if shell else adb(*cmd)
    shown = out.replace("\n", " ")[:220] if out else "(空)"
    print("  [%s] %s\n        -> %s" % (name, cmd if shell else " ".join(cmd), shown), flush=True)
    return out


# ---------- 1. 等待 USB 调试授权 ----------
print("ADB:", ADB, flush=True)
SERIAL = ""
for i in range(WAIT_SECONDS // 3):
    devices = adb("devices")
    lines = [l for l in devices.splitlines()[1:] if l.strip()]
    for line in lines:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device" and not parts[0].startswith("127.0.0.1") \
                and not parts[0].startswith("emulator-"):
            SERIAL = parts[0]
            break
    if SERIAL or not WAIT:
        break
    if i % 10 == 0:
        state = "未授权(unauthorized)，请在手机上点『允许 USB 调试』" if any("unauthorized" in l for l in lines) else "未检测到设备"
        print("  等待中… %s（%ds）" % (state, i * 3), flush=True)
    time.sleep(3)

if not SERIAL:
    print("\n[FAILED] 手机仍未授权或没连上。请在手机屏幕上确认『允许 USB 调试』后重新运行本脚本。")
    print("当前 devices：\n" + adb("devices"))
    sys.exit(2)

print("\n[OK] 已连接设备：%s" % SERIAL, flush=True)

# ---------- 2. 诊断 ----------
title("一、设备与系统信息")
for key, label in [
    ("ro.product.model", "型号"),
    ("ro.build.version.release", "Android 版本"),
    ("ro.build.version.sdk", "API"),
    ("ro.miui.ui.version.name", "MIUI/HyperOS"),
    ("ro.build.version.incremental", "系统版本"),
    ("ro.product.brand", "品牌"),
]:
    print("  %-14s %s" % (label, sh("getprop " + key)), flush=True)

title("二、知账安装情况")
installed = []
# 按行精确匹配，避免 "site.zhizhang.app" 误命中 "site.zhizhang.app.debug"
pkg_lines = set(l.strip().replace("package:", "") for l in sh("pm list packages").splitlines())
for pkg in KNOWN_PKGS:
    if pkg in pkg_lines:
        installed.append(pkg)
        print("  已安装：%s  version=%s" % (pkg, sh("dumpsys package " + pkg + " | grep versionName | head -1")), flush=True)
if not installed:
    print("  !! 手机上没有找到知账，准备安装 1.01", flush=True)
    if not CHECK_ONLY and os.path.exists(APK):
        print("  " + adb("-s", SERIAL, "install", "-r", APK, timeout=180).replace("\n", " ")[:300], flush=True)
        pkg_lines = set(l.strip().replace("package:", "") for l in sh("pm list packages").splitlines())
        installed = [p for p in KNOWN_PKGS if p in pkg_lines]
print("  目标包名：%s" % (installed[0] if installed else "未确定"), flush=True)
PKG = installed[0] if installed else KNOWN_PKGS[0]
COMP = PKG + "/" + SERVICE

title("三、通知使用权当前状态")
print("  enabled_notification_listeners = %s" % sh("settings get secure enabled_notification_listeners"), flush=True)
print("  --- dumpsys notification 中跟知账有关的行 ---", flush=True)
dump = sh("dumpsys notification --noredact", timeout=60)
for line in dump.splitlines():
    if "zhizhang" in line.lower() or "listener" in line.lower() and ("bound" in line.lower() or "allowed" in line.lower()):
        print("    " + line.strip()[:200], flush=True)
print("  --- cmd notification 支持情况 ---", flush=True)
print("    " + ("支持 allow_listener" if "allow_listener" in sh("cmd notification help") else "不支持 allow_listener（将用兜底方案）"), flush=True)
print("  --- 受限设置 appops ---", flush=True)
print("    " + sh("appops get " + PKG + " ACCESS_RESTRICTED_SETTINGS").replace("\n", " ")[:200], flush=True)
live_lines = [l.strip() for l in dump.splitlines() if "PaymentNotificationListener" in l and "INotificationListener" in l]
print("  --- 实时监听服务（Live notification listeners）---", flush=True)
for l in live_lines[:4]:
    print("    " + l[:200], flush=True)
if not live_lines:
    print("    (未绑定！只写允许列表不算解决，需要 disallow→allow 重新触发)", flush=True)

if CHECK_ONLY:
    ok = COMP in sh("settings get secure enabled_notification_listeners") and bool(live_lines)
    print("\n[诊断模式结束，未修改任何设置]")
    print("  结论：%s" % ("权限已完全生效 ✅" if ok else "尚未完全生效 ❌，请不带 --check 重跑修复"), flush=True)
    sys.exit(0)

# ---------- 3. 修复 ----------
title("四、开始修复（无需 Root）")

step("① 解除侧载应用『受限设置』标记（HyperOS 开关灰色/点不动的根因）",
     "appops set %s ACCESS_RESTRICTED_SETTINGS allow" % PKG)
step("② 官方命令允许通知监听器（Android 13+）",
     "cmd notification allow_listener %s" % COMP)
step("③ 授予 ADB 修改安全设置的能力（兜底，需手机开启『USB 调试(安全设置)』才生效）",
     "pm grant %s android.permission.WRITE_SECURE_SETTINGS" % PKG)
step("④ 加入电池优化白名单，避免后台被小米杀掉",
     "cmd deviceidle whitelist +%s" % PKG)

# 关键：小米 HyperOS 上，allow_listener 只写标记、不一定真的绑定服务。
# 实测「先 disallow 再 allow」能强制系统重新执行 bindService，Live listeners 才会出现本应用。
print("  ⑤ 重新触发系统绑定（disallow → allow），这是 HyperOS 上真正生效的关键步", flush=True)
step("    5a 撤销", "cmd notification disallow_listener %s" % COMP)
time.sleep(2)
step("    5b 重新授权", "cmd notification allow_listener %s" % COMP)
time.sleep(3)

# 兜底：直接写 secure setting（必须追加，不能覆盖别的监听器）
current = sh("settings get secure enabled_notification_listeners")
if current in ("null", "", "ERR") or COMP not in current:
    parts = [x for x in (current or "").split(":") if x and x != "null"]
    if COMP not in parts:
        parts.append(COMP)
    step("⑤ 兜底写入通知监听列表（追加，不覆盖其他应用）",
         "settings put secure enabled_notification_listeners '%s'" % ":".join(parts))
else:
    print("  ⑤ 监听列表已包含知账，无需兜底写入", flush=True)

title("五、修复后验证")
final = sh("settings get secure enabled_notification_listeners")
print("  enabled_notification_listeners = %s" % final, flush=True)
print("  知账在允许列表中：%s" % ("是" if COMP in final else "否"), flush=True)

dump2 = sh("dumpsys notification --noredact", timeout=60)
live = [l.strip()[:200] for l in dump2.splitlines() if "PaymentNotificationListener" in l]
print("  --- 实时监听服务（Live notification listeners）---", flush=True)
for h in live[:8]:
    print("    " + h, flush=True)
CONNECTED = any("ComponentInfo{" + COMP in l or COMP in l and "INotificationListener" in l for l in live)
print("  监听服务已真正绑定：%s" % ("是 ✅" if CONNECTED else "否 ❌（需要重启手机或重跑脚本）"), flush=True)

# 打开 App 让它刷新权限状态
step("⑥ 启动知账首页", "monkey -p %s -c android.intent.category.LAUNCHER 1" % PKG)
print("\n[DONE] 判定标准：手机首页显示『自动记账运行中』+ 上面『监听服务已真正绑定：是』，两者同时满足才算完全解决。", flush=True)
if not CONNECTED:
    print("若仍是『否』，请重启手机后再运行本脚本；或到手机：设置 → 应用设置 → 应用管理 → 知账 → "
          "右上角 ⋮ →『允许受限设置』，再到『特殊权限设置 → 通知使用权』手动开关一次。", flush=True)
