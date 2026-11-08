# -*- coding: utf-8 -*-
"""知账 · 真机通知诊断日志查看器（只读）

用法：
  python show-xiaomi-notifications.py           # 显示最近收到的交易类通知
  python show-xiaomi-notifications.py --all    # 显示全部（最多 200 条）
  python show-xiaomi-notifications.py --clear  # 清空手机上的诊断日志
"""
import subprocess
import sys
import json
from datetime import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from device_config import find_adb, require_serial

ADB = find_adb()
SERIAL = require_serial(ADB)
PKG = "site.zhizhang.app.debug"
CLEAR = "--clear" in sys.argv
SHOW_ALL = "--all" in sys.argv


def adb(*args, timeout=40):
    try:
        return subprocess.run([ADB, "-s", SERIAL] + list(args), capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout).stdout
    except Exception as e:
        return "ERR:%s" % e


if CLEAR:
    print(adb("shell", "run-as", PKG, "rm", "-f", "files/notification_log.jsonl").strip())
    print("已清空诊断日志。")
    sys.exit(0)

raw = adb("shell", "run-as", PKG, "cat", "files/notification_log.jsonl")
lines = [l.strip() for l in raw.splitlines() if l.strip().startswith("{")]
if not SHOW_ALL:
    lines = lines[-20:]

print("=" * 70)
print("真机通知诊断日志（共 %d 条）" % len(lines))
print("=" * 70)
if not lines:
    print("\n  （空）还没有收到任何交易类通知。")
    print("  这说明通知没有送到知账，常见原因：")
    print("   1) 该支付场景本来就不发系统通知（如微信转账给好友是聊天消息）")
    print("   2) 通知被小米折叠成一组，或锁屏时被隐藏内容")
    print("   3) 支付通知由微信/支付宝发出，而非购物 App 本身")
    print("   4) 监听服务被系统回收 → 双击 fix-xiaomi-notification.bat 重新修复")
else:
    for line in reversed(lines):
        try:
            o = json.loads(line)
        except Exception:
            continue
        t = datetime.fromtimestamp(o.get("ts", 0) / 1000).strftime("%m-%d %H:%M:%S")
        mark = "✅ 已入账" if o.get("parsed") else "⏭  未入账"
        print("\n[%s] %s" % (t, mark))
        print("  来源包名 : %s  %s" % (o.get("pkg"), "(白名单内)" if o.get("inWhitelist") else "(白名单外)"))
        print("  标题/正文: %s / %s" % (o.get("title"), o.get("text")))
        if o.get("parsed"):
            print("  识别金额 : ¥%.2f" % (o.get("amountCents", 0) / 100.0))
        print("  判定     : %s" % o.get("reason"))

print("\n" + "=" * 70)
print("把上面「标题/正文」原样发给我，就能补对应的解析规则。")
print("清空日志：python show-xiaomi-notifications.py --clear")
