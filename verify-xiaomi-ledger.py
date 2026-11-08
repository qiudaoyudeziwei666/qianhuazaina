# -*- coding: utf-8 -*-
"""知账 · 小米真机账本验收工具（只读，不改动手机任何设置）

用法：
  python verify-xiaomi-ledger.py            # 查看当前账本 + 权限状态，并保存本次快照
  python verify-xiaomi-ledger.py --diff    # 与上次快照对比，列出新增流水（推荐：付款前后各跑一次）
  python verify-xiaomi-ledger.py --quiet   # 只输出结论行，便于脚本调用
"""
import subprocess
import sys
import os
import json
import sqlite3
import tempfile
import shutil
from datetime import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from device_config import find_adb, require_serial

ADB = find_adb()
SERIAL = require_serial(ADB)
PKG = "site.zhizhang.app.debug"
COMP = PKG + "/site.zhizhang.app.service.PaymentNotificationListener"
BASE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT = os.path.join(BASE, "xiaomi-ledger-snapshot.json")
DIFF = "--diff" in sys.argv
QUIET = "--quiet" in sys.argv


def adb(*args, timeout=40):
    try:
        return subprocess.run([ADB, "-s", SERIAL] + list(args), capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout).stdout.strip()
    except Exception as e:
        return "ERR:%s" % e


def out(*a):
    if not QUIET:
        print(*a, flush=True)


def title(t):
    out("\n" + "=" * 66)
    out(t)
    out("=" * 66)


def money(cents):
    return "¥%.2f" % (cents / 100.0)


def ts(ms):
    try:
        return datetime.fromtimestamp(ms / 1000).strftime("%m-%d %H:%M")
    except Exception:
        return "-"


# ---------- 1. 导出账本（run-as + exec-out 保证二进制安全） ----------
tmp = tempfile.mkdtemp(prefix="zhizhang_verify_")
db = os.path.join(tmp, "zhizhang.db")
try:
    for f in ("zhizhang.db", "zhizhang.db-wal", "zhizhang.db-shm"):
        with open(os.path.join(tmp, f), "wb") as fh:
            r = subprocess.run([ADB, "-s", SERIAL, "exec-out", "run-as", PKG,
                                "cat", "databases/" + f], stdout=fh, timeout=40)
    conn = sqlite3.connect(db)
    conn.row_factory = sqlite3.Row
    cols = [d[1] for d in conn.execute('pragma table_info("ledger_entries")')]
    rows = [dict(r) for r in conn.execute(
        'select * from "ledger_entries" order by occurredAt desc')]
    conn.close()
except Exception as e:
    print("[FAILED] 读取账本失败：%s\n请确认手机已解锁、USB 调试已授权、知账已安装。" % e)
    shutil.rmtree(tmp, ignore_errors=True)
    sys.exit(2)
finally:
    shutil.rmtree(tmp, ignore_errors=True)

# ---------- 2. 权限状态 ----------
allowed = adb("shell", "settings", "get", "secure", "enabled_notification_listeners")
dump = adb("shell", "dumpsys", "notification", "--noredact", timeout=60)
live = any("PaymentNotificationListener" in l and "INotificationListener" in l
           for l in dump.splitlines())
restricted = adb("shell", "appops", "get", PKG, "ACCESS_RESTRICTED_SETTINGS")
in_list = COMP in allowed
ready = in_list and live

status_map = {"CONFIRMED": "已入账", "PENDING": "待确认", "IGNORED": "已忽略"}
conf_map = {"HIGH": "高", "MEDIUM": "中", "LOW": "低"}

# ---------- 3. 输出 ----------
title("一、权限与监听状态")
out("  允许列表含知账      : %s" % ("是 ✅" if in_list else "否 ❌"))
out("  监听服务真正绑定    : %s" % ("是 ✅" if live else "否 ❌"))
out("  受限设置            : %s" % restricted.replace("\n", " ")[:120])
out("  结论                : %s" % ("自动记账链路已就绪 ✅" if ready
                                 else "未完全就绪 ❌，请双击 fix-xiaomi-notification.bat 重新修复"))

title("二、账本流水（共 %d 笔）" % len(rows))
if not rows:
    out("  （空）还没有任何流水。")
else:
    for r in rows:
        out("  [%s] %-9s %-10s %s  %s" % (
            status_map.get(r.get("status"), r.get("status")),
            money(r.get("amountCents", 0)),
            r.get("category", "")[:10],
            r.get("merchant", "")[:24],
            r.get("sourceName", "")))
        out("        时间 %s · 置信度 %s · 来源分类 %s · 需AI %s" % (
            ts(r.get("occurredAt", 0)), conf_map.get(r.get("confidence"), "?"),
            r.get("classificationSource", ""), r.get("needsAi")))
        raw = (r.get("rawNotification") or "").replace("\n", " / ")[:120]
        if raw:
            out("        原始通知：%s" % raw)

# ---------- 4. 对比快照 ----------
old_ids = set()
has_snapshot = False
if os.path.exists(SNAPSHOT):
    try:
        data = json.load(open(SNAPSHOT, encoding="utf-8"))
        old_ids = set(data.get("ids", []))
        # 账本为空时 ids 也是空列表，所以用「文件是否存在」判断有没有基线，而不是 ids 是否为空
        has_snapshot = "savedAt" in data
    except Exception:
        pass

new = [r for r in rows if r.get("id") not in old_ids]
if DIFF:
    title("三、与上次快照对比")
    if not has_snapshot:
        out("  没有可对比的历史快照。建议：先跑一次本脚本（不带 --diff）保存基线，")
        out("  再去手机上付一笔，然后跑 python verify-xiaomi-ledger.py --diff")
    elif new:
        out("  ✅ 新增 %d 笔流水：" % len(new))
        for r in new:
            out("     %s  %s  %s  [%s]" % (
                money(r.get("amountCents", 0)), r.get("merchant", ""),
                r.get("category", ""), status_map.get(r.get("status"), r.get("status"))))
            raw = (r.get("rawNotification") or "").replace("\n", " / ")[:200]
            if raw:
                out("     原始通知：%s" % raw)
        out("\n  这些流水来自真实通知监听（rawNotification 有内容即证明不是手动记账）。")
    else:
        out("  ⚠️ 没有新增流水。可能原因：")
        out("     1) 支付通知里没有明确金额或商户，被解析器按设计跳过")
        out("     2) 通知被小米折叠/静默，或 App 后台被杀")
        out("     3) 付款后还没等几秒，或没刷新")
        out("     4) 高置信度自动入账已开启时，会直接进「已入账」而不是「待确认」")

with open(SNAPSHOT, "w", encoding="utf-8") as f:
    json.dump({"ids": [r.get("id") for r in rows],
               "savedAt": datetime.now().isoformat()}, f, ensure_ascii=False, indent=1)

title("四、提示")
out("  本次流水编号已保存到 xiaomi-ledger-snapshot.json 作为下次对比基线。")
out("  付款前跑一次、付款后跑 --diff，就能确认自动记账是否真的抓到了通知。")
if QUIET:
    print("READY=%s ROWS=%d NEW=%d" % (ready, len(rows), len(new)))
