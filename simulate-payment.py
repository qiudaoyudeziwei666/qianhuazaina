# -*- coding: utf-8 -*-
"""知账 · 一键模拟支付通知（不实际付款、不需要商家二维码）

用法：
  python simulate-payment.py             # 注入 1 条微信支付（默认，最稳）
  python simulate-payment.py all        # 依次注入 3 种场景（间隔 12 秒，较慢）
  python simulate-payment.py pdd        # 只注入拼多多订单
  python simulate-payment.py transfer   # 只注入转账
  python simulate-payment.py --status   # 只看链路状态，不注入

前提：手机已按 fix-xiaomi-notification.bat 完成授权、且保持解锁。
注意：
  1) 文本里不要有 ASCII 空格，手机 shell 会按空格拆词导致金额丢失；用逗号代替。
  2) 小米会限流短时间内连续注入的通知（通知在系统里但监听器不回调）。
     所以每条之间留 12 秒；如果没入账，等一分钟再跑一次即可。
"""
import subprocess
import sys
import time
import json
from datetime import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from device_config import find_adb, require_serial

ADB = find_adb()
SERIAL = require_serial(ADB)
PKG = "site.zhizhang.app.debug"
COMP = PKG + "/site.zhizhang.app.service.PaymentNotificationListener"

CASES = [
    ("ZhizhangSimWechat", "微信支付", "支付成功,金额12.50元,商户:楼下便利店", "微信商户消费"),
    ("ZhizhangSimPdd", "拼多多", "订单支付成功,实付款29.90元,待发货", "拼多多电商订单"),
    ("ZhizhangSimTransfer", "微信支付", "转账成功,金额66.00元,转账给张三", "微信转账给好友"),
]


def adb(*args, timeout=60):
    return subprocess.run([ADB, "-s", SERIAL] + list(args), capture_output=True,
                          text=True, encoding="utf-8", errors="replace",
                          timeout=timeout).stdout.strip()


def state():
    allowed = adb("shell", "settings", "get", "secure", "enabled_notification_listeners")
    dump = adb("shell", "dumpsys", "notification", "--noredact", timeout=90)
    live = any("PaymentNotificationListener" in l and "INotificationListener" in l
               for l in dump.splitlines())
    return COMP in allowed and live


if "--status" in sys.argv:
    print("链路就绪：" , "✅" if state() else "❌（双击 fix-xiaomi-notification.bat 修复）")
    sys.exit(0)

if not state():
    print("[!] 通知监听未绑定，先修复再测试")
    adb("shell", "cmd", "notification", "disallow_listener", COMP)
    time.sleep(2)
    adb("shell", "cmd", "notification", "allow_listener", COMP)
    time.sleep(4)
    if not state():
        print("[FAILED] 仍无法绑定，请双击 fix-xiaomi-notification.bat")
        sys.exit(2)

only = next((a for a in sys.argv[1:] if not a.startswith("-")), None)
if only is None:
    cases = CASES[:1]                      # 默认只发一条，避开小米限流
elif only.lower() == "all":
    cases = CASES
else:
    cases = [c for c in CASES if only.lower() in c[0].lower()]
    if not cases:
        print("[!] 未知场景：%s（可选 wechat / pdd / transfer / all）" % only)
        sys.exit(1)

print("=" * 66)
print("注入 %d 条模拟通知（无需付款）" % len(cases))
print("=" * 66)
for tag, title, body, label in cases:
    # 用唯一 tag 避免与上一条同 id 覆盖
    tag = tag + str(int(time.time() * 1000) % 100000)
    print("  → %-14s %s" % (label, body))
    adb("shell", "cmd", "notification", "post", "-t", title, tag, body)
    time.sleep(12 if len(cases) > 1 else 5)

print("\n" + "=" * 66)
print("诊断日志（最近 5 条）")
print("=" * 66)
raw = adb("shell", "run-as", PKG, "cat", "files/notification_log.jsonl")
lines = [l for l in raw.splitlines() if l.strip().startswith("{")][-5:]
for line in reversed(lines):
    try:
        o = json.loads(line)
        t = datetime.fromtimestamp(o.get("ts", 0) / 1000).strftime("%H:%M:%S")
        print("[%s] %s / %s" % (t, o.get("title"), o.get("text")))
        print("      %s  ¥%.2f  %s" % (
            "✅ 已解析" if o.get("parsed") else "⏭  未入账",
            o.get("amountCents", 0) / 100.0, o.get("reason")))
    except Exception:
        pass

print("\n在手机上打开知账 →「自动」→ 待确认流水，或用 verify-xiaomi-ledger.bat diff 查看新增。")
