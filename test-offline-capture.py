# -*- coding: utf-8 -*-
"""知账 · 拔线自检：验证「电脑不在时」App 是否仍能自动识别支付通知

用法：
  双击 test-offline-capture.bat          # 全自动跑一次（约 30 秒）
  python test-offline-capture.py         # 同上
  python test-offline-capture.py 30      # 自定义延迟秒数（默认 20 秒）

它做了什么：
  1. 用 ADB 在手机上「预约」一条稍后由手机本地 shell 自己发出的支付通知；
  2. 立刻 kill-server 让电脑端 ADB 完全断开；
  3. 等待通知触发（这段时间电脑没有参与）；
  4. 重新连上，读 App 的诊断日志与账本，判断通知是否被自动识别入账。

为什么这能说明「拔掉 USB 也能用」：
  真正干活的不是电脑，而是 Android 系统绑定的 NotificationListenerService。
  有通知时是「系统推给 App」，不是「电脑读给 App」。ADB 只做过一次授权登记，
  那些登记写在手机磁盘的 settings_secure.xml / appops / packages.xml 里，持久有效。

注意：
  1) 通知文本不要带 ASCII 空格，手机 shell 会按空格拆词导致金额丢失；用逗号代替。
  2) 这是「电脑端 ADB 断开」的严格模拟；物理拔线只是额外断掉手机侧 adbd，
     而通知监听链路与 adbd 无关。要 100% 实证，拔线后等下一笔真实支付即可。
  3) 会产生一笔 ¥8.88 的测试流水（待确认），可在 App 里忽略或删除。
"""
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
from datetime import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from device_config import find_adb, require_serial

ADB = find_adb()
SERIAL = require_serial(ADB)
PKG = "site.zhizhang.app.debug"
COMP = PKG + "/site.zhizhang.app.service.PaymentNotificationListener"
DELAY = 20

try:
    DELAY = max(10, int(sys.argv[1])) if len(sys.argv) > 1 else 20
except ValueError:
    pass


def out(s=""):
    print(s, flush=True)


def title(s):
    out("\n" + "=" * 62)
    out(s)
    out("=" * 62)


def adb(*args, timeout=60):
    try:
        return subprocess.run(
            [ADB, "-s", SERIAL] + list(args), capture_output=True,
            text=True, encoding="utf-8", errors="replace", timeout=timeout
        ).stdout.strip()
    except Exception:
        return ""


def server(*args):
    try:
        return subprocess.run(
            [ADB] + list(args), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=30
        ).stdout.strip()
    except Exception:
        return ""


def log_lines():
    s = adb("shell", "run-as", PKG, "cat", "files/notification_log.jsonl")
    return [x for x in s.splitlines() if x.strip()]


def read_rows():
    tmp = tempfile.mkdtemp(prefix="zhizhang_offline_")
    try:
        for f in ("zhizhang.db", "zhizhang.db-wal", "zhizhang.db-shm"):
            with open(os.path.join(tmp, f), "wb") as fh:
                subprocess.run(
                    [ADB, "-s", SERIAL, "exec-out", "run-as", PKG,
                     "cat", "databases/" + f], stdout=fh, timeout=40
                )
        conn = sqlite3.connect(os.path.join(tmp, "zhizhang.db"))
        conn.row_factory = sqlite3.Row
        rows = [dict(r) for r in conn.execute('select * from "ledger_entries"')]
        conn.close()
        return rows
    except Exception:
        return []
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def chain_state():
    allowed = adb("shell", "settings", "get", "secure", "enabled_notification_listeners")
    dump = adb("shell", "dumpsys", "notification", "--noredact", timeout=60)
    live = any("PaymentNotificationListener" in l and "INotificationListener" in l
               for l in dump.splitlines())
    return (COMP in allowed), live


title("知账 · 拔线自检：电脑断开后还能自动记账吗？")

# ---------- 0. 设备在线 ----------
devices = server("devices")
if SERIAL not in devices or "\tdevice" not in devices:
    out("  [FAILED] 手机未通过 USB 连上，或 USB 调试授权被撤销。")
    out("  设备列表：\n" + devices)
    sys.exit(2)
out("  手机在线：%s" % SERIAL)

# ---------- 1. 授权链路 ----------
in_list, live = chain_state()
out("  通知使用权持久登记：%s" % ("是" if in_list else "否"))
out("  系统实时绑定监听服务：%s" % ("是" if live else "否"))
if not (in_list and live):
    out("  [FAILED] 授权链路不完整，请先双击 fix-xiaomi-notification.bat")
    sys.exit(2)

# ---------- 2. 基线 ----------
base_rows = len(read_rows())
base_logs = len(log_lines())
out("  基线：账本 %d 笔 / 诊断日志 %d 条" % (base_rows, base_logs))

# ---------- 3. 预约一条「手机自己发」的通知 ----------
mark = datetime.now().strftime("%H%M%S")
merchant = "拔线自检" + mark
tag = "ZhizhangSimOffline" + mark
body = "已扣费,金额8.88元,商户:" + merchant
inner = "sleep %d; cmd notification post -t 微信支付 %s %s" % (DELAY, tag, body)
adb("shell", "nohup sh -c '%s' >/dev/null 2>&1 &" % inner, timeout=15)
out("  已预约：手机将在 %d 秒后自己发出「%s」" % (DELAY, merchant))

# ---------- 4. 电脑端完全断开 ----------
server("kill-server")
out("\n  >>> 电脑端 ADB 已断开，接下来 %d 秒电脑不参与 <<<" % (DELAY + 6))
time.sleep(DELAY + 6)
server("start-server")
devices2 = server("devices")
reconnected = SERIAL in devices2 and "\tdevice" in devices2
out("  电脑端重新连接：%s（无需再次点 USB 调试授权）" % ("是" if reconnected else "否，请检查数据线"))
# 顺手恢复可能同时连着的 MuMu 模拟器连接（失败无妨）
try:
    server("connect", "127.0.0.1:16384")
except Exception:
    pass

# ---------- 5. 验收 ----------
title("验收结果")
rows = read_rows()
logs = log_lines()
hit_logs = [l for l in logs if merchant in l]
hit_rows = [r for r in rows if merchant in json.dumps(r, ensure_ascii=False, default=str)]

parsed_ok = False
for line in hit_logs:
    try:
        d = json.loads(line)
        parsed_ok = parsed_ok or bool(d.get("parsed"))
        out("  诊断日志：pkg=%s  parsed=%s  amount=%s  reason=%s"
            % (d.get("pkg"), d.get("parsed"), d.get("amountCents"), d.get("reason")))
    except Exception:
        pass

for r in hit_rows:
    out("  账本流水：¥%.2f  %s  %s  %s"
        % (r.get("amountCents", 0) / 100.0, r.get("merchant", ""),
           r.get("category", ""), r.get("status", "")))

out("\n  账本 %d → %d 笔，诊断日志 %d → %d 条"
    % (base_rows, len(rows), base_logs, len(logs)))

if hit_logs and parsed_ok and hit_rows:
    out("\n  [PASS] 电脑端 ADB 完全断开期间，手机自己发出的支付通知")
    out("         仍被自动监听到、解析成功并进入待确认。")
    out("         => 拔掉 USB 数据线后，自动记账照常工作。")
elif hit_logs and not hit_rows:
    out("\n  [PARTIAL] 通知送到了，但没入账。可能是小米短时间内限流，")
    out("            等一分钟重跑一次；或点开一次知账让服务恢复。")
else:
    out("\n  [FAILED] 断开期间没收到通知。常见原因：手机本地后台任务被系统回收、")
    out("           或监听绑定掉了。先跑 fix-xiaomi-notification.bat 再重试。")

out("\n  说明：这笔 ¥8.88 是测试流水，在知账「自动」页可忽略或删除。")
out("  真拔线的最终验证：拔线后正常用手机，等下一笔真实支付看是否自动进待确认。")
out("")
